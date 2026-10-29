"""Train and honestly test a risk model against the app's current hand-set rule.

The question: part-way through an onboarding session, how well can we tell that
the customer will give up before finishing?

Protocol, fixed before any result was looked at:
  * Rows are split by agent (the dataset's own split column), so the test set
    only has agents no model saw in training.
  * Candidate models are compared on the validation agents only. The test agents
    are scored once, at the end, with the already-chosen model.
  * The score is ROC-AUC: the chance that a session that really was abandoned is
    ranked above one that was not. 0.5 is guessing.
  * Pass mark: the trained model must beat the app's current rule by at least
    0.03 AUC on the test agents, on every seed. Logistic regression is kept
    unless gradient boosting beats it by at least 0.02 AUC on validation,
    because the simpler model is far easier to put in the app.

Usage:
    .\\.venv-ml\\Scripts\\python.exe src/consistency/train_risk.py \\
        data/onboarding_dataset_v3.csv [more datasets...] --out src/models/risk_model.json
"""

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path

import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, roc_auc_score
from sklearn.preprocessing import StandardScaler

import features as feature_module
from features import FEATURE_NAMES, rows_from_sessions

MIN_AUC_GAIN_OVER_APP_RULE = 0.03
MIN_AUC_GAIN_TO_PREFER_BOOSTING = 0.02

# Columns that must never become features: they describe how the session ended,
# or they restate the label.
FORBIDDEN_AS_FEATURES = (
    "session_status",
    "onboarding_scenario",
    "onboarding_risk_score",
    "onboarding_risk_level",
    "next_onboarding_field",
    "real_time_guidance",
)


def load_split_rows(path):
    """Read one dataset and build training rows, grouped by split."""
    sessions = defaultdict(list)
    meta = {}
    with open(path, newline="", encoding="utf-8") as handle:
        for event in csv.DictReader(handle):
            sessions[event["session_id"]].append(event)
            meta[event["session_id"]] = (
                event["sales_agent_id"],
                event["industry"],
                event["context_value"],
                event["split"],
            )

    by_split = defaultdict(list)
    for session_id, events in sessions.items():
        events.sort(key=lambda event: event["event_timestamp"])
        agent_id, industry, context, split = meta[session_id]
        by_split[split].append((session_id, agent_id, industry, context, events))

    return {split: rows_from_sessions(group) for split, group in by_split.items()}


def as_arrays(rows):
    x = np.asarray([row["features"] for row in rows], dtype=np.float64)
    y = np.asarray([row["abandoned"] for row in rows], dtype=np.int32)
    app_rule = np.asarray([row["app_rule"] for row in rows], dtype=np.float64)
    return x, y, app_rule


def check_no_agent_crosses_splits(splits):
    agents = {split: {row["agent_id"] for row in rows} for split, rows in splits.items()}
    for first in agents:
        for second in agents:
            if first < second and agents[first] & agents[second]:
                raise SystemExit(
                    f"Agents appear in both '{first}' and '{second}'; the test would be unfair."
                )


def evaluate(path, verbose=True):
    with open(path, newline="", encoding="utf-8") as handle:
        header = next(csv.reader(handle))
    leaked = [name for name in FORBIDDEN_AS_FEATURES if name in FEATURE_NAMES]
    if leaked:
        raise SystemExit(f"Outcome columns used as features: {leaked}")

    splits = load_split_rows(path)
    missing = {"train", "validation", "test"} - set(splits)
    if missing:
        raise SystemExit(f"{path} has no {sorted(missing)} rows.")
    check_no_agent_crosses_splits(splits)

    train_x, train_y, _ = as_arrays(splits["train"])
    val_x, val_y, val_rule = as_arrays(splits["validation"])
    test_x, test_y, test_rule = as_arrays(splits["test"])

    scaler = StandardScaler().fit(train_x)
    logistic = LogisticRegression(max_iter=2000, class_weight="balanced")
    logistic.fit(scaler.transform(train_x), train_y)
    boosting = HistGradientBoostingClassifier(
        max_iter=200, learning_rate=0.1, random_state=0
    )
    boosting.fit(train_x, train_y)

    val_logistic = roc_auc_score(val_y, logistic.predict_proba(scaler.transform(val_x))[:, 1])
    val_boosting = roc_auc_score(val_y, boosting.predict_proba(val_x)[:, 1])
    chosen_name = (
        "gradient_boosting"
        if val_boosting - val_logistic >= MIN_AUC_GAIN_TO_PREFER_BOOSTING
        else "logistic_regression"
    )

    def score(model_name, x):
        if model_name == "logistic_regression":
            return logistic.predict_proba(scaler.transform(x))[:, 1]
        return boosting.predict_proba(x)[:, 1]

    test_scores = score(chosen_name, test_x)
    result = {
        "dataset": str(path),
        "rows": {name: len(rows) for name, rows in splits.items()},
        "abandoned_share": {
            name: round(float(as_arrays(rows)[1].mean()), 4) for name, rows in splits.items()
        },
        "validation_auc": {
            "logistic_regression": round(val_logistic, 4),
            "gradient_boosting": round(val_boosting, 4),
            "app_rule": round(roc_auc_score(val_y, val_rule), 4),
        },
        "chosen_model": chosen_name,
        "test_auc": {
            "model": round(float(roc_auc_score(test_y, test_scores)), 4),
            "app_rule": round(float(roc_auc_score(test_y, test_rule)), 4),
        },
        "test_average_precision": {
            "model": round(float(average_precision_score(test_y, test_scores)), 4),
            "app_rule": round(float(average_precision_score(test_y, test_rule)), 4),
        },
    }
    result["test_auc"]["gain_over_app_rule"] = round(
        result["test_auc"]["model"] - result["test_auc"]["app_rule"], 4
    )
    result["passes_mark"] = (
        result["test_auc"]["gain_over_app_rule"] >= MIN_AUC_GAIN_OVER_APP_RULE
    )

    if verbose:
        print(f"\n=== {path}")
        print(f"rows {result['rows']} | abandoned share {result['abandoned_share']}")
        print(f"validation AUC {result['validation_auc']} -> chose {chosen_name}")
        print(
            f"test AUC: model {result['test_auc']['model']:.4f} vs "
            f"app rule {result['test_auc']['app_rule']:.4f} "
            f"(gain {result['test_auc']['gain_over_app_rule']:+.4f}) "
            f"-> {'PASS' if result['passes_mark'] else 'FAIL'}"
        )

    export = None
    if chosen_name == "logistic_regression":
        # Fold the scaler into the weights so the app needs only a dot product.
        weights = logistic.coef_[0] / scaler.scale_
        intercept = float(logistic.intercept_[0] - (logistic.coef_[0] * scaler.mean_ / scaler.scale_).sum())
        # Warning thresholds come from the validation agents, never the test ones:
        # "medium" is the riskiest 20% of steps, "high" the riskiest 10%. Scores
        # from a class-balanced model are rankings, not true probabilities, so
        # these have to be measured rather than guessed.
        val_scores = logistic.predict_proba(scaler.transform(val_x))[:, 1]
        medium, high = (float(np.quantile(val_scores, share)) for share in (0.80, 0.90))
        export = {
            "model": "logistic_regression",
            "feature_names": list(FEATURE_NAMES),
            "weights": [round(float(weight), 6) for weight in weights],
            "intercept": round(intercept, 6),
            "medium_risk_threshold": round(medium, 4),
            "high_risk_threshold": round(high, 4),
            "long_event_threshold_minutes": feature_module.LONG_EVENT_THRESHOLD_MINUTES,
            "note": (
                "score = 1 / (1 + exp(-(intercept + sum(weights * features)))). "
                "Thresholds are the 80th and 90th percentile of validation scores, "
                "so about 20% of steps reach medium and 10% high. "
                "Trained on simulated sessions only; not a real-world probability."
            ),
        }
        check = 1.0 / (1.0 + np.exp(-(intercept + test_x @ weights)))
        largest_difference = float(np.max(np.abs(check - test_scores)))
        result["export_matches_sklearn_to"] = largest_difference
        if verbose:
            print(f"exported weights reproduce sklearn to {largest_difference:.2e}")

    return result, export


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("datasets", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, help="where to write the model for the app")
    parser.add_argument("--report", type=Path, help="where to write the full results as JSON")
    arguments = parser.parse_args()

    results, exports = [], []
    for path in arguments.datasets:
        result, export = evaluate(path)
        results.append(result)
        exports.append(export)

    gains = [result["test_auc"]["gain_over_app_rule"] for result in results]
    all_pass = all(result["passes_mark"] for result in results)
    print("\n=== Pass mark: beat the app's rule by >= "
          f"{MIN_AUC_GAIN_OVER_APP_RULE} AUC on every dataset")
    print(f"gains: {gains} -> {'PASS' if all_pass else 'FAIL'}")

    if arguments.report:
        arguments.report.parent.mkdir(parents=True, exist_ok=True)
        arguments.report.write_text(
            json.dumps({"datasets": results, "all_pass": all_pass}, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"wrote {arguments.report}")

    if arguments.out:
        if not all_pass:
            print("Not exporting a model: the pass mark was not met.")
        elif exports[0] is None:
            print("Not exporting: the chosen model is not the exportable one.")
        else:
            arguments.out.parent.mkdir(parents=True, exist_ok=True)
            arguments.out.write_text(json.dumps(exports[0], indent=2) + "\n", encoding="utf-8")
            print(f"wrote {arguments.out}")


if __name__ == "__main__":
    main()
