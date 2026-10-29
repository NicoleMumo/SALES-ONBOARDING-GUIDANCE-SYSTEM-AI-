"""Flag onboarding sessions that were run unusually.

This is the part that speaks to the project's purpose: finding sessions that did
not follow the standard process, so an administrator can see where agents drift.

It learns from completed sessions with no labels (an Isolation Forest), so it
needs no examples of "bad" sessions to start. To check it honestly, known
deviations are planted into held-out sessions: steps shuffled out of order, and
extra retries added. A useful detector finds those and leaves normal sessions
alone.

Pass mark, fixed before any result was looked at: at least 80% of planted
deviations flagged, and at most 10% of untouched normal sessions flagged.

What this detector is and is not good at (measured, see `single_cause_report`):
it passes that mark comfortably on deviations that break several habits at once,
but it is weak on a session whose only fault is the order of steps, catching
about a third of those. For order alone, the plain `out_of_order_steps` count
below is far better (about 98%) and is exact and explainable, so the app should
use that count as its order measure and keep this detector for sessions that
look unusual in several ways at once. The pass mark above was, with hindsight,
too easy: the planted deviation changed order *and* added retries.

Usage:
    .\\.venv-ml\\Scripts\\python.exe src/consistency/anomaly.py data/onboarding_dataset_v3.csv
"""

import argparse
import csv
import json
import random
from collections import defaultdict
from pathlib import Path

import numpy as np
from sklearn.ensemble import IsolationForest

from features import SESSION_FEATURE_NAMES, out_of_order_steps, session_features
from playbooks import standard_order

FEATURE_NAMES = SESSION_FEATURE_NAMES

MIN_DEVIATIONS_CAUGHT = 0.80
MAX_NORMAL_SESSIONS_FLAGGED = 0.10
FLAG_SHARE = 0.10  # the detector is tuned to flag this share of training sessions


def plant_deviation(events, rng):
    """Return a copy of a session run badly: steps out of order, plus retries.

    This is the known-wrong example the detector has to catch.
    """
    disturbed = [dict(event) for event in events]
    if len(disturbed) >= 4:
        # Move a late step to the front: a clear break from the standard order.
        source = rng.randrange(len(disturbed) // 2, len(disturbed))
        disturbed.insert(rng.randrange(0, len(disturbed) // 2), disturbed.pop(source))
    for event in rng.sample(disturbed, k=min(3, len(disturbed))):
        event["validation_status"] = "failed"
        event["is_revisit"] = "True"
    return disturbed


def reorder_only(events, rng, moves=1):
    """A session whose *only* fault is the order of its steps."""
    disturbed = [dict(event) for event in events]
    for _ in range(moves):
        if len(disturbed) >= 4:
            source = rng.randrange(len(disturbed) // 2, len(disturbed))
            disturbed.insert(rng.randrange(0, len(disturbed) // 2), disturbed.pop(source))
    return disturbed


def retries_only(events, rng):
    """A session whose only fault is extra failed checks and revisits."""
    disturbed = [dict(event) for event in events]
    for event in rng.sample(disturbed, k=min(3, len(disturbed))):
        event["validation_status"] = "failed"
        event["is_revisit"] = "True"
    return disturbed


def load_sessions(path):
    grouped = defaultdict(list)
    meta = {}
    with open(path, newline="", encoding="utf-8") as handle:
        for event in csv.DictReader(handle):
            grouped[event["session_id"]].append(event)
            meta[event["session_id"]] = (
                event["industry"], event["context_value"], event["split"],
                event["session_status"],
            )
    sessions = defaultdict(list)
    for session_id, events in grouped.items():
        events.sort(key=lambda event: event["event_timestamp"])
        industry, context, split, _ = meta[session_id]
        sessions[split].append((session_id, industry, context, events))
    return sessions


def evaluate(path, seed=0, verbose=True):
    rng = random.Random(seed)
    sessions = load_sessions(path)
    missing = {"train", "test"} - set(sessions)
    if missing:
        raise SystemExit(f"{path} has no {sorted(missing)} sessions.")

    train = np.asarray(
        [session_features(events, industry, context)
         for _, industry, context, events in sessions["train"]],
        dtype=np.float64,
    )
    detector = IsolationForest(
        n_estimators=200, contamination=FLAG_SHARE, random_state=seed
    ).fit(train)

    normal, planted = [], []
    for _, industry, context, events in sessions["test"]:
        normal.append(session_features(events, industry, context))
        planted.append(session_features(plant_deviation(events, rng), industry, context))

    flagged_normal = float((detector.predict(np.asarray(normal)) == -1).mean())
    caught = float((detector.predict(np.asarray(planted)) == -1).mean())
    result = {
        "dataset": str(path),
        "train_sessions": len(train),
        "test_sessions": len(normal),
        "deviations_caught": round(caught, 4),
        "normal_sessions_flagged": round(flagged_normal, 4),
        "passes_mark": caught >= MIN_DEVIATIONS_CAUGHT
        and flagged_normal <= MAX_NORMAL_SESSIONS_FLAGGED,
    }
    # Honest breakdown: deviations with a single cause are much harder, and the
    # plain out-of-order count beats the detector on order. Always reported.
    single_cause = {}
    for label, disturb in (("order_only", lambda events: reorder_only(events, rng, 1)),
                           ("retries_only", lambda events: retries_only(events, rng))):
        disturbed = np.asarray([
            session_features(disturb(events), industry, context)
            for _, industry, context, events in sessions["test"]
        ])
        single_cause[label] = round(float((detector.predict(disturbed) == -1).mean()), 4)

    def order_count(events, industry, context):
        return out_of_order_steps(
            [event["onboarding_field"] for event in events], standard_order(industry, context)
        )

    counts_normal = [order_count(events, i, c) for _, i, c, events in sessions["test"]]
    counts_reordered = [
        order_count(reorder_only(events, rng, 1), i, c)
        for _, i, c, events in sessions["test"]
    ]
    result["single_cause_report"] = single_cause
    result["out_of_order_count"] = {
        "catches_order_only_deviation": round(
            float(np.mean(np.asarray(counts_reordered) >= 1)), 4
        ),
        "normal_sessions_with_any_out_of_order_step": round(
            float(np.mean(np.asarray(counts_normal) >= 1)), 4
        ),
        "normal_mean_out_of_order_steps": round(float(np.mean(counts_normal)), 2),
    }

    if verbose:
        print(f"\n=== {path}")
        print(f"train sessions {result['train_sessions']:,} | test sessions {result['test_sessions']:,}")
        print(f"planted deviations caught: {caught:.1%} (need >= {MIN_DEVIATIONS_CAUGHT:.0%})")
        print(f"normal sessions flagged:   {flagged_normal:.1%} (need <= {MAX_NORMAL_SESSIONS_FLAGGED:.0%})")
        print(f"-> {'PASS' if result['passes_mark'] else 'FAIL'}")
        print("  harder, single-cause deviations:")
        print(f"    order only:   detector {single_cause['order_only']:.1%}, "
              f"plain count {result['out_of_order_count']['catches_order_only_deviation']:.1%}")
        print(f"    retries only: detector {single_cause['retries_only']:.1%}")
        print(f"  normal sessions already out of order: "
              f"{result['out_of_order_count']['normal_sessions_with_any_out_of_order_step']:.1%} "
              f"(mean {result['out_of_order_count']['normal_mean_out_of_order_steps']} steps) "
              f"-- working out of order is normal, so measure how much, not whether")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("datasets", nargs="+", type=Path)
    parser.add_argument("--report", type=Path)
    arguments = parser.parse_args()

    results = [evaluate(path) for path in arguments.datasets]
    all_pass = all(result["passes_mark"] for result in results)
    print(f"\n=== Pass mark on every dataset -> {'PASS' if all_pass else 'FAIL'}")
    if arguments.report:
        arguments.report.parent.mkdir(parents=True, exist_ok=True)
        arguments.report.write_text(
            json.dumps({"datasets": results, "all_pass": all_pass}, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"wrote {arguments.report}")


if __name__ == "__main__":
    main()
