"""What the agent actually sees: the risk warning as the app shows it, replayed on test sessions.

The risk model's published results (Section 5.4.4) score every step against the riskiest-20%
(amber) and riskiest-10% (red) thresholds. The app does not show those raw levels: after each
checked answer it passes the model's level through RiskWarningPacer (Insights.kt):

- nothing for the first 4 checked answers;
- the shown level rises at most one step per answer ("Check in" before "Needs help");
- "Needs help" only after 3 high scores in a row;
- the warning drops as soon as the model's level drops.

This script replays every test session (unseen sales agents) answer by answer, scores it with
the shipped model (ml/models/risk_model.json) exactly as the app does, applies the same pacer,
and reports, per session:

- abandoned sessions: how many were shown "Check in" / "Needs help" before the client quit,
  and how many answers before the end the first warning came (the lead);
- completed sessions: how many were shown a warning anyway (false alarms);
- the same numbers for the raw, unpaced levels, to show what pacing changes.

Simulated data only. Usage:
    python ml/risk_policy_replay.py ml/data/onboarding_dataset_v3.csv \
        [--out ml/models/risk_policy_replay.json]
"""
import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path
from statistics import median

import consistency

MODELS = Path(__file__).resolve().parent / "models"
LOW, MEDIUM, HIGH = 0, 1, 2


class Pacer:
    """A copy of RiskWarningPacer in Insights.kt. Change both together."""

    def __init__(self, quiet_checks=4, high_streak=3):
        self.quiet_checks, self.high_streak = quiet_checks, high_streak
        self.checks, self.high_run, self.shown = 0, 0, LOW

    def next(self, model_level):
        self.checks += 1
        self.high_run = self.high_run + 1 if model_level == HIGH else 0
        if self.checks <= self.quiet_checks or model_level == LOW:
            target = LOW
        elif model_level == HIGH and self.high_run >= self.high_streak:
            target = HIGH
        else:
            target = MEDIUM
        self.shown = self.shown + 1 if target > self.shown else target
        return self.shown


def model_scorer():
    model = json.loads((MODELS / "risk_model.json").read_text(encoding="utf-8"))
    weights, intercept = model["weights"], model["intercept"]
    medium, high = model["medium_risk_threshold"], model["high_risk_threshold"]

    def level(steps, is_banking, is_business):
        x = consistency.features(steps, is_banking, is_business)
        score = 1.0 / (1.0 + math.exp(-(intercept + sum(w * v for w, v in zip(weights, x)))))
        return HIGH if score >= high else MEDIUM if score >= medium else LOW

    return level, model


def test_sessions(path):
    sessions, meta = defaultdict(list), {}
    with open(path, newline="", encoding="utf-8") as handle:
        for event in csv.DictReader(handle):
            if event["split"] != "test":
                continue
            sessions[event["session_id"]].append(event)
            meta[event["session_id"]] = (event["industry"], event["context_value"])
    for session_id, events in sessions.items():
        events.sort(key=lambda event: event["event_timestamp"])
        industry, context = meta[session_id]
        yield events, industry, context


def replay(path):
    level_of, model = model_scorer()
    rows = []
    for events, industry, context in test_sessions(path):
        steps = consistency.session_steps(events)
        is_banking, is_business = industry == "banking", context == "business"
        pacer = Pacer()
        raw, shown = [], []
        # After each checked answer the app scores the session so far, including that answer.
        for position in range(1, len(steps) + 1):
            level = level_of(steps[:position], is_banking, is_business)
            raw.append(level)
            shown.append(pacer.next(level))
        rows.append({
            "abandoned": events[-1]["session_status"] == "abandoned",
            "answers": len(steps),
            "raw": raw,
            "shown": shown,
        })
    return rows, model


def first_at_least(levels, wanted):
    return next((i for i, level in enumerate(levels) if level >= wanted), None)


def summary(rows, key):
    def share(group, wanted):
        hit = [r for r in group if first_at_least(r[key], wanted) is not None]
        return round(100 * len(hit) / max(len(group), 1), 1), hit

    abandoned = [r for r in rows if r["abandoned"]]
    completed = [r for r in rows if not r["abandoned"]]
    out = {}
    for name, wanted in (("check_in_or_more", MEDIUM), ("needs_help", HIGH)):
        caught_pct, caught = share(abandoned, wanted)
        false_pct, false_alarms = share(completed, wanted)
        # Lead: answers between the first warning and the client's last answer (0 = at the last one).
        leads = [r["answers"] - 1 - first_at_least(r[key], wanted) for r in caught]
        warned = len(caught) + len(false_alarms)
        out[name] = {
            "abandoned_sessions_warned_pct": caught_pct,
            "abandoned_warned_at_least_one_answer_early_pct":
                round(100 * sum(1 for lead in leads if lead >= 1) / max(len(abandoned), 1), 1),
            "median_lead_answers": median(leads) if leads else None,
            "completed_sessions_warned_pct": false_pct,
            "warned_sessions_that_were_abandoned_pct": round(100 * len(caught) / max(warned, 1), 1),
            "answers_shown_per_completed_session": round(
                sum(sum(1 for level in r[key] if level >= wanted) for r in completed) / max(len(completed), 1), 2),
        }
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("csv")
    parser.add_argument("--out", default=str(MODELS / "risk_policy_replay.json"))
    args = parser.parse_args()

    rows, model = replay(args.csv)
    abandoned = sum(r["abandoned"] for r in rows)
    report = {
        "dataset": Path(args.csv).name,
        "note": "Simulated data, test split (unseen sales agents). 'Check in' = medium, 'Needs help' = high.",
        "test_sessions": len(rows),
        "abandoned_sessions": abandoned,
        "abandoned_share_pct": round(100 * abandoned / len(rows), 1),
        "thresholds": {"medium": model["medium_risk_threshold"], "high": model["high_risk_threshold"]},
        "pacer": {"quiet_checks": 4, "high_streak": 3},
        "as_the_app_shows_it": summary(rows, "shown"),
        "raw_model_levels_without_pacing": summary(rows, "raw"),
    }
    Path(args.out).write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
