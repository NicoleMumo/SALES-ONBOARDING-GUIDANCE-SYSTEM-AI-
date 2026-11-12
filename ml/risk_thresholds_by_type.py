"""Should business and other sessions have their own risk-warning thresholds?

Found on the emulator: a banking business session with every answer correct, at a normal
pace, showed "Needs help" by its 6th answer. The shipped risk model (ml/models/risk_model.json)
gives business sessions a large weight, because in the simulated data they are abandoned more
often, so one threshold for all sessions sends nearly every business session to red.

The fix tried here keeps the model's weights (so its ROC-AUC does not change) and measures
the amber and red thresholds separately for business and other sessions, by the same rule as
before: the riskiest 20% (amber) and 10% (red) of validation steps, now within each group.
The thresholds come from the validation agents of the dataset the model was trained on
(onboarding_dataset_v3.csv); the test agents are only scored.

Decision rule, fixed before any result was seen. Adopt the per-type thresholds only if, on
the test sessions of all three datasets (seeds 2026, 7 and 99):
  (a) fewer completed business sessions are shown "Needs help" (false alarms),
  (b) the share of abandoned sessions shown "Check in" or more falls by at most 5 points, and
  (c) F1 at amber over all test steps falls by at most 0.02.
It also reports a clean business session (every answer right, 1 minute each), which should
no longer reach "Needs help".

Result: the first variant (both groups get their own thresholds) failed (c): it lowered the
threshold for every other session, so their false alarms tripled and F1 fell by about 0.08.
A second variant was then added, after seeing that result, and judged by the same rule:
other sessions keep the shipped thresholds unchanged, and only business sessions get their
own (the riskiest 20% / 10% of business validation steps). --write uses the first variant
that passes.

Simulated data only. Usage:
    python ml/risk_thresholds_by_type.py [--write]
--write adds the thresholds to ml/models/risk_model.json, only if the rule passes.
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np

import consistency
from risk_policy_replay import HIGH, LOW, MEDIUM, Pacer, test_sessions

ML = Path(__file__).resolve().parent
MODELS = ML / "models"
DATASETS = {
    "2026": ML / "data" / "onboarding_dataset_v3.csv",
    "7": ML / "data" / "onboarding_dataset_v3_seed7.csv",
    "99": ML / "data" / "onboarding_dataset_v3_seed99.csv",
}
BUSINESS = consistency.FEATURE_NAMES.index("is_business")


def scores_of(x, weights, intercept):
    return 1.0 / (1.0 + np.exp(-(intercept + x @ weights)))


def thresholds_from(scores):
    return {"medium": round(float(np.quantile(scores, 0.80)), 4), "high": round(float(np.quantile(scores, 0.90)), 4)}


def level(score, t):
    return HIGH if score >= t["high"] else MEDIUM if score >= t["medium"] else LOW


def evaluate(path, weights, intercept, pick):
    """pick(is_business) -> thresholds. Step F1 at amber, and the paced replay per session."""
    test = consistency.load_split_rows(path)["test"]
    x, y, _ = consistency.as_arrays(test)
    s = scores_of(x, weights, intercept)
    amber = np.array([sc >= pick(bool(row[BUSINESS]))["medium"] for sc, row in zip(s, x)])
    tp = int((amber & (y == 1)).sum())
    precision = tp / max(int(amber.sum()), 1)
    recall = tp / max(int((y == 1).sum()), 1)
    f1 = 2 * precision * recall / max(precision + recall, 1e-9)

    sessions = {"business": {"completed": 0, "completed_needs_help": 0, "completed_check_in": 0},
                "other": {"completed": 0, "completed_needs_help": 0, "completed_check_in": 0}}
    abandoned = abandoned_warned = 0
    for events, industry, context in test_sessions(path):
        steps = consistency.session_steps(events)
        is_banking, is_business = industry == "banking", context == "business"
        t = pick(is_business)
        pacer, shown = Pacer(), []
        for position in range(1, len(steps) + 1):
            f = consistency.features(steps[:position], is_banking, is_business)
            sc = 1.0 / (1.0 + math.exp(-(intercept + sum(w * v for w, v in zip(weights, f)))))
            shown.append(pacer.next(level(sc, t)))
        group = sessions["business" if is_business else "other"]
        if events[-1]["session_status"] == "abandoned":
            abandoned += 1
            abandoned_warned += max(shown) >= MEDIUM
        else:
            group["completed"] += 1
            group["completed_needs_help"] += max(shown) >= HIGH
            group["completed_check_in"] += max(shown) >= MEDIUM
    for group in sessions.values():
        n = max(group["completed"], 1)
        group["needs_help_false_alarm_pct"] = round(100 * group["completed_needs_help"] / n, 1)
        group["check_in_false_alarm_pct"] = round(100 * group["completed_check_in"] / n, 1)
    return {
        "f1_at_amber": round(f1, 4),
        "abandoned_warned_check_in_or_more_pct": round(100 * abandoned_warned / max(abandoned, 1), 1),
        "completed_sessions": sessions,
    }


def clean_business_session(weights, intercept, t_business):
    """Banking business, 19 questions, every answer right, 1 minute each: what the agent sees."""
    steps, shown, pacer = [], [], Pacer()
    names = {LOW: "On track", MEDIUM: "Check in", HIGH: "Needs help"}
    for answered in range(1, 13):
        steps.append(consistency.Step(1.0, True, False, 100 * answered / 19))
        f = consistency.features(steps, True, True)
        sc = 1.0 / (1.0 + math.exp(-(intercept + sum(w * v for w, v in zip(weights, f)))))
        shown.append(names[pacer.next(level(sc, t_business))])
    return shown


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--out", default=str(MODELS / "risk_thresholds_by_type.json"))
    args = parser.parse_args()

    model_path = MODELS / "risk_model.json"
    model = json.loads(model_path.read_text(encoding="utf-8"))
    weights, intercept = np.asarray(model["weights"]), model["intercept"]

    val = consistency.load_split_rows(DATASETS["2026"])["validation"]
    vx, _, _ = consistency.as_arrays(val)
    vs = scores_of(vx, weights, intercept)
    business = vx[:, BUSINESS] == 1
    one = {"medium": model["medium_risk_threshold"], "high": model["high_risk_threshold"]}
    by_type = {"business": thresholds_from(vs[business]), "other": thresholds_from(vs[~business])}

    business_only = {"business": by_type["business"], "other": dict(one)}
    variants = {"by_client_type": by_type, "business_only": business_only}
    results = {}
    for seed, path in DATASETS.items():
        results[seed] = {"one_threshold": evaluate(path, weights, intercept, lambda b: one)}
        for name, t in variants.items():
            results[seed][name] = evaluate(path, weights, intercept, lambda b, t=t: t["business" if b else "other"])

    def passes(r, name):
        before, after = r["one_threshold"], r[name]
        return (
            after["completed_sessions"]["business"]["completed_needs_help"]
            < before["completed_sessions"]["business"]["completed_needs_help"]
            and before["abandoned_warned_check_in_or_more_pct"] - after["abandoned_warned_check_in_or_more_pct"] <= 5.0
            and before["f1_at_amber"] - after["f1_at_amber"] <= 0.02
        )

    verdicts = {name: all(passes(r, name) for r in results.values()) for name in variants}
    chosen = next((name for name in variants if verdicts[name]), None)
    report = {
        "note": "Simulated data. Model weights unchanged; thresholds from validation agents of seed 2026.",
        "validation_steps": {"business": int(business.sum()), "other": int((~business).sum())},
        "one_threshold": one,
        "variants": variants,
        "test": results,
        "clean_business_session": {
            "one_threshold": clean_business_session(weights, intercept, one),
            "business_only": clean_business_session(weights, intercept, business_only["business"]),
        },
        "decision_rule_passes": verdicts,
        "chosen": chosen,
    }
    Path(args.out).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))

    if args.write:
        if chosen is None:
            raise SystemExit("Decision rule not met by any variant: risk_model.json left unchanged.")
        model["thresholds_by_client_type"] = variants[chosen]
        model["note"] = (
            "score = 1 / (1 + exp(-(intercept + sum(weights * features)))). "
            "Thresholds are the 80th and 90th percentile of validation scores, so about 20% of steps "
            "reach medium and 10% high. thresholds_by_client_type keeps those for other sessions and uses the "
            "80th and 90th percentile of business validation scores for business sessions "
            "(ml/risk_thresholds_by_type.py); the app uses it when present. "
            "Trained on simulated sessions only; not a real-world probability."
        )
        model_path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {model_path}")


if __name__ == "__main__":
    main()
