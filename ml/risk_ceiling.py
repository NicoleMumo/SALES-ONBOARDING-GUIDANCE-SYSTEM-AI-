"""How well could *any* model warn of abandonment on the simulated data?

The planned target for the completion-risk warning was an F1-score of 80%. The
shipped logistic regression reaches about 0.35 at the amber threshold. This
script asks whether that gap is the model's fault or the data's, by comparing
four warnings under the same protocol as `consistency.py`:

  app rule        the app's old hand-set rule
  shipped model   the 14-input logistic regression the app uses
  richer inputs   the same model with 10 more inputs the app could compute from
                  its four facts per step (duration, passed, revisit, progress)
  hidden traits   gradient boosting that is also told what no app can know: the
                  client's patience, whether they brought documents, their
                  literacy and phone quality, the agent's skill, and the real
                  elapsed time against the client's patience

The hidden traits are not in the CSV. They come from re-running the generator
with the dataset's own seed, which reproduces every event exactly; the script
checks that before using them. The "hidden traits" model is therefore a
practical ceiling: if it cannot reach the target, no app-side model can.

Protocol, as in `consistency.py`: train on the training agents, choose
thresholds on the validation agents, score the test agents once. F1 is reported
per step, at the amber threshold (the riskiest 20% of validation steps, as in
the app) and at the threshold with the best validation F1. "Best F1 on test"
picks the threshold by looking at the test answers; it is not a fair result,
only an upper bound for that model.

Usage:
    .\\.venv-ml\\Scripts\\python.exe ml/risk_ceiling.py \\
        --report ml/models/risk_ceiling_report.json
"""

import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path

import consistency
import dataset

ROOT = Path(__file__).resolve().parents[1]

# (CSV, seed and session count it was generated with)
DATASETS = (
    (ROOT / "ml" / "data" / "onboarding_dataset_v3.csv", 2026, 8000),
    (ROOT / "ml" / "data" / "onboarding_dataset_v3_seed7.csv", 7, 8000),
    (ROOT / "ml" / "data" / "onboarding_dataset_v3_seed99.csv", 99, 8000),
)

EXTRA_FEATURE_NAMES = (
    "last_failed",
    "last_duration",
    "last_revisit",
    "failures_last3",
    "revisits_last3",
    "max_consecutive_failures",
    "steps_since_failure",
    "mean_duration_last3",
    "log_elapsed",
    "remaining_percent",
)

HIDDEN_TRAIT_NAMES = (
    "patience_minutes",
    "has_documents",
    "literacy",
    "low_end_device",
    "agent_skill",
    "elapsed_over_patience",
    "wall_clock_minutes",
    "past_patience",
)

AMBER_SHARE = 0.80  # amber = riskiest 20% of validation steps, as in risk_model.json


def extra_features(steps):
    """Ten more inputs the app could compute from the steps it already holds."""
    if not steps:
        raise ValueError("extra_features() needs at least one completed step.")
    last3 = steps[-3:]
    run = longest_run = 0
    steps_since_failure = len(steps)
    for index, step in enumerate(steps):
        if step.validation_passed:
            run = 0
            continue
        run += 1
        longest_run = max(longest_run, run)
        steps_since_failure = len(steps) - 1 - index
    elapsed = sum(step.duration_minutes for step in steps)
    return [
        0.0 if steps[-1].validation_passed else 1.0,
        steps[-1].duration_minutes,
        1.0 if steps[-1].is_revisit else 0.0,
        float(sum(1 for step in last3 if not step.validation_passed)),
        float(sum(1 for step in last3 if step.is_revisit)),
        float(longest_run),
        float(min(steps_since_failure, 40)),
        sum(step.duration_minutes for step in last3) / len(last3),
        math.log1p(elapsed),
        100.0 - steps[-1].progress_percent,
    ]


def hidden_traits(seed, sessions):
    """Re-run the generator and record each session's hidden client and agent traits.

    Recording happens after each session is simulated, so the random stream (and
    therefore every event) is unchanged.
    """
    traits = {}
    original = dataset.to_events

    def recording(session_id, customer, agent, *rest):
        traits[session_id] = (
            customer.patience,
            1.0 if customer.has_documents else 0.0,
            customer.literacy,
            1.0 if customer.low_end_device else 0.0,
            agent.skill,
        )
        return original(session_id, customer, agent, *rest)

    dataset.to_events = recording
    try:
        events = dataset.generate(sessions, seed)
    finally:
        dataset.to_events = original
    return traits, events


def load(path, seed, sessions_count):
    sessions = defaultdict(list)
    with open(path, newline="", encoding="utf-8") as handle:
        for event in csv.DictReader(handle):
            sessions[event["session_id"]].append(event)

    traits, regenerated = hidden_traits(seed, sessions_count)
    regenerated_ids = {event["session_id"] for event in regenerated}
    if len(regenerated) != sum(len(v) for v in sessions.values()) or regenerated_ids != set(sessions):
        raise SystemExit(f"Re-running the generator with seed {seed} does not reproduce {path}.")

    splits = defaultdict(lambda: defaultdict(list))
    for session_id, events in sessions.items():
        events.sort(key=lambda event: event["event_timestamp"])
        steps = consistency.session_steps(events)
        if len(steps) < 2:
            continue
        first = events[0]
        is_banking = first["industry"] == "banking"
        is_business = first["context_value"] == "business"
        abandoned = events[-1]["session_status"] == "abandoned"
        patience, documents, literacy, low_end, skill = traits[session_id]
        wall_clock = 0.0
        # Same rows as consistency.rows_from_sessions: every prefix but the last step.
        for position in range(1, len(steps)):
            prefix = steps[:position]
            event = events[position - 1]
            wall_clock += float(event["event_duration_minutes"]) + float(
                event["agent_response_time_minutes"]
            )
            shipped = consistency.features(prefix, is_banking, is_business)
            richer = shipped + extra_features(prefix)
            rows = splits[first["split"]]
            rows["app_rule"].append(consistency.app_rule_score(prefix))
            rows["shipped"].append(shipped)
            rows["richer"].append(richer)
            rows["hidden"].append(
                richer
                + [patience, documents, literacy, low_end, skill,
                   wall_clock / patience, wall_clock, 1.0 if wall_clock > patience else 0.0]
            )
            rows["y"].append(1 if abandoned else 0)
    return splits


def precision_recall_f1(y, flagged):
    true_pos = sum(1 for label, flag in zip(y, flagged) if label and flag)
    false_pos = sum(1 for label, flag in zip(y, flagged) if not label and flag)
    false_neg = sum(1 for label, flag in zip(y, flagged) if label and not flag)
    precision = true_pos / (true_pos + false_pos) if true_pos + false_pos else 0.0
    recall = true_pos / (true_pos + false_neg) if true_pos + false_neg else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {"precision": round(precision, 3), "recall": round(recall, 3), "f1": round(f1, 3)}


def best_f1_threshold(y, scores):
    from sklearn.metrics import precision_recall_curve

    precision, recall, thresholds = precision_recall_curve(y, scores)
    best, best_f1 = thresholds[0], -1.0
    for p, r, threshold in zip(precision[:-1], recall[:-1], thresholds):
        f1 = 2 * p * r / (p + r) if p + r else 0.0
        if f1 > best_f1:
            best, best_f1 = threshold, f1
    return float(best), best_f1


def judge(val_y, val_scores, test_y, test_scores):
    import numpy as np
    from sklearn.metrics import average_precision_score, roc_auc_score

    amber = float(np.quantile(val_scores, AMBER_SHARE))
    tuned, _ = best_f1_threshold(val_y, val_scores)
    _, best_on_test = best_f1_threshold(test_y, test_scores)
    return {
        "test_auc": round(float(roc_auc_score(test_y, test_scores)), 4),
        "test_average_precision": round(float(average_precision_score(test_y, test_scores)), 4),
        "at_amber": precision_recall_f1(test_y, [s >= amber for s in test_scores]),
        "at_validation_best": precision_recall_f1(test_y, [s >= tuned for s in test_scores]),
        "best_f1_on_test_upper_bound": round(best_on_test, 3),
    }


def evaluate(path, seed, sessions_count):
    import numpy as np
    from sklearn.ensemble import HistGradientBoostingClassifier
    from sklearn.linear_model import LogisticRegression
    from sklearn.preprocessing import StandardScaler

    splits = load(path, seed, sessions_count)
    train, val, test = splits["train"], splits["validation"], splits["test"]
    train_y, val_y, test_y = (np.asarray(s["y"]) for s in (train, val, test))

    def logistic(key):
        x = np.asarray(train[key])
        scaler = StandardScaler().fit(x)
        model = LogisticRegression(max_iter=3000, class_weight="balanced")
        model.fit(scaler.transform(x), train_y)
        return (model.predict_proba(scaler.transform(np.asarray(val[key])))[:, 1],
                model.predict_proba(scaler.transform(np.asarray(test[key])))[:, 1])

    def boosting(key):
        model = HistGradientBoostingClassifier(
            max_iter=300, learning_rate=0.05, l2_regularization=1.0, random_state=0
        )
        model.fit(np.asarray(train[key]), train_y)
        return (model.predict_proba(np.asarray(val[key]))[:, 1],
                model.predict_proba(np.asarray(test[key]))[:, 1])

    warnings = {
        "app_rule": (np.asarray(val["app_rule"]), np.asarray(test["app_rule"])),
        "shipped_model": logistic("shipped"),
        "richer_inputs": logistic("richer"),
        "hidden_traits_ceiling": boosting("hidden"),
    }
    result = {
        "dataset": path.name,
        "test_abandoned_share": round(float(test_y.mean()), 4),
        "warnings": {name: judge(val_y, v, test_y, t) for name, (v, t) in warnings.items()},
    }
    print(f"\n=== {path.name} (test abandoned share {result['test_abandoned_share']})")
    for name, scores in result["warnings"].items():
        print(
            f"{name:22s} AUC {scores['test_auc']:.3f}  "
            f"F1 at amber {scores['at_amber']['f1']:.3f}  "
            f"F1 val-tuned {scores['at_validation_best']['f1']:.3f}  "
            f"best F1 on test {scores['best_f1_on_test_upper_bound']:.3f}"
        )
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, help="where to write the results as JSON")
    arguments = parser.parse_args()

    results = [evaluate(path, seed, count) for path, seed, count in DATASETS]
    best_anywhere = max(
        r["warnings"][name]["best_f1_on_test_upper_bound"]
        for r in results for name in r["warnings"]
    )
    print(f"\nHighest F1 any warning reached, even choosing the threshold on the test "
          f"answers: {best_anywhere:.3f} (planned target 0.80)")
    if arguments.report:
        arguments.report.parent.mkdir(parents=True, exist_ok=True)
        arguments.report.write_text(
            json.dumps({"datasets": results, "highest_f1_upper_bound": best_anywhere,
                        "planned_f1_target": 0.80}, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"wrote {arguments.report}")


if __name__ == "__main__":
    main()
