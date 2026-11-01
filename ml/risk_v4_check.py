"""Does more realistic abandonment change the risk results?

In the v3 data a session is abandoned by a chance draw at each step. The v4
option of the generator (`dataset.py`, V4_BEHAVIOUR) makes clients show strain
first: frustration builds with failed answers, verification failures and long
steps, frustrated or uncommitted clients slow down and make more mistakes, and
the chance of giving up rises with that strain. The share of abandoned sessions
is kept at v3's level.

This script generates v4 for the project's three seeds (in a temporary folder),
trains the risk model with the unchanged protocol of `consistency.py`, and
reports, per dataset:
  - test ROC-AUC of the model and of the old hand-set rule;
  - precision, recall and F1 at the amber threshold (the riskiest 20% of
    validation steps), scored with the exported weights exactly as the app would;
  - the warning signs: failed answers and minutes per step in the three steps
    before the end, for abandoned and for completed sessions.

The project's shipped model stays trained on v3; this is a check.

Usage:
    .\\.venv-ml\\Scripts\\python.exe ml/risk_v4_check.py --report ml/models/risk_v4_check.json
"""

import argparse
import csv
import json
import math
import tempfile
from collections import defaultdict
from pathlib import Path

import consistency
import dataset

SEEDS = (2026, 7, 99)
SESSIONS = 8000


def warning_signs(path):
    """Failed share and minutes per step in the 3 steps before each session's end."""
    sessions = defaultdict(list)
    with open(path, newline="", encoding="utf-8") as handle:
        for event in csv.DictReader(handle):
            sessions[event["session_id"]].append(event)
    collected = {"abandoned": ([], []), "completed": ([], [])}
    for events in sessions.values():
        events.sort(key=lambda event: event["event_timestamp"])
        if len(events) < 5:
            continue
        failed, minutes = collected[events[-1]["session_status"]]
        for event in events[-4:-1]:
            failed.append(event["validation_status"] == "failed")
            minutes.append(float(event["event_duration_minutes"]))
    return {
        outcome: {
            "failed_share": round(sum(failed) / len(failed), 3),
            "minutes_per_step": round(sum(minutes) / len(minutes), 2),
        }
        for outcome, (failed, minutes) in collected.items()
    }


def at_amber(path, export):
    """Precision, recall and F1 on the test agents, scored as the app scores."""
    rows = consistency.load_split_rows(path)["test"]
    weights, intercept = export["weights"], export["intercept"]
    amber = export["medium_risk_threshold"]
    true_pos = false_pos = false_neg = 0
    for row in rows:
        total = intercept + sum(w * x for w, x in zip(weights, row["features"]))
        flagged = 1.0 / (1.0 + math.exp(-total)) >= amber
        if flagged and row["abandoned"]:
            true_pos += 1
        elif flagged:
            false_pos += 1
        elif row["abandoned"]:
            false_neg += 1
    precision = true_pos / (true_pos + false_pos) if true_pos + false_pos else 0.0
    recall = true_pos / (true_pos + false_neg) if true_pos + false_neg else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {"precision": round(precision, 3), "recall": round(recall, 3), "f1": round(f1, 3)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, help="where to write the results as JSON")
    arguments = parser.parse_args()

    results = []
    with tempfile.TemporaryDirectory() as folder:
        for seed in SEEDS:
            path = Path(folder) / f"onboarding_dataset_v4_seed{seed}.csv"
            events = dataset.generate(SESSIONS, seed, version="v4")
            dataset.save(events, path)
            finals = [e["session_status"] for e in events if e["session_status"] != "in_progress"]
            result, export = consistency.evaluate(path, verbose=False)
            if export is None:
                raise SystemExit("The chosen model is not the exportable logistic regression.")
            entry = {
                "seed": seed,
                "abandoned_sessions": round(finals.count("abandoned") / len(finals), 3),
                "test_auc": result["test_auc"],
                "at_amber": at_amber(path, export),
                "warning_signs_last_3_steps": warning_signs(path),
            }
            results.append(entry)
            print(f"seed {seed}: abandoned {entry['abandoned_sessions']:.1%}, AUC "
                  f"{entry['test_auc']['model']:.3f} (rule {entry['test_auc']['app_rule']:.3f}), "
                  f"F1 at amber {entry['at_amber']['f1']:.3f}, last 3 steps failed "
                  f"{entry['warning_signs_last_3_steps']['abandoned']['failed_share']:.1%} vs "
                  f"{entry['warning_signs_last_3_steps']['completed']['failed_share']:.1%}")

    if arguments.report:
        arguments.report.parent.mkdir(parents=True, exist_ok=True)
        arguments.report.write_text(json.dumps({"version": "v4", "datasets": results}, indent=2) + "\n",
                                    encoding="utf-8")
        print(f"wrote {arguments.report}")


if __name__ == "__main__":
    main()
