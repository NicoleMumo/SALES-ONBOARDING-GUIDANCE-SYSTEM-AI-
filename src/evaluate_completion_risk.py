"""Evaluate completion-risk detection against the held-out session dataset."""

import argparse
import json
from pathlib import Path

import pandas as pd
from sklearn.metrics import average_precision_score, precision_recall_curve, roc_auc_score

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = ROOT / "data" / "onboarding_dataset.csv"

DIFFICULT_SCENARIOS = {
    "incomplete_sessions",
    "long_completion_times",
    "revisited_fields",
    "validation_errors",
}


def score_sessions(dataset: pd.DataFrame) -> pd.DataFrame:
    ordered = dataset.assign(
        event_timestamp=pd.to_datetime(dataset["event_timestamp"])
    ).sort_values(["session_id", "event_timestamp"])
    grouped = ordered.groupby("session_id").agg(
        scenario=("onboarding_scenario", "first"),
        elapsed_minutes=("event_duration_minutes", "sum"),
        long_events=("event_duration_minutes", lambda values: (values > 15).sum()),
        validation_errors=(
            "validation_status",
            lambda values: (values == "failed").sum(),
        ),
        field_revisits=("is_revisit", "sum"),
        progress_percent=("onboarding_progress", "last"),
    )
    grouped["score"] = (
        0.05
        + (grouped["validation_errors"] > 0) * 0.25
        + (grouped["long_events"] > 0) * 0.15
        + grouped["field_revisits"] * 0.10
        + (grouped["progress_percent"] < 50) * 0.10
    ).clip(upper=1.0)
    grouped["predicted_difficult"] = grouped["score"] >= 0.30
    grouped["actual_difficult"] = grouped["scenario"].isin(DIFFICULT_SCENARIOS)
    return grouped


def evaluate(dataset_path: Path, split: str, split_column: str = "split", seed: int = 42) -> dict:
    dataset = pd.read_csv(dataset_path)
    required = {
        "session_id",
        "event_timestamp",
        "onboarding_scenario",
        "event_duration_minutes",
        "validation_status",
        "is_revisit",
        "onboarding_progress",
        split_column,
    }
    missing = required - set(dataset.columns)
    if missing:
        raise ValueError(f"Dataset is missing columns: {sorted(missing)}")

    sessions = score_sessions(dataset[dataset[split_column] == split])
    if sessions.empty:
        raise ValueError(f"No sessions found for split '{split}'.")

    predicted = sessions["predicted_difficult"]
    actual = sessions["actual_difficult"]
    true_positive = (predicted & actual).sum()
    false_positive = (predicted & ~actual).sum()
    false_negative = (~predicted & actual).sum()
    true_negative = (~predicted & ~actual).sum()

    precision = true_positive / (true_positive + false_positive) if true_positive else 0
    recall = true_positive / (true_positive + false_negative) if true_positive else 0
    accuracy = (true_positive + true_negative) / len(sessions)
    probabilities = sessions["score"].astype(float)
    labels = actual.astype(int)
    precision_curve, recall_curve, thresholds = precision_recall_curve(labels, probabilities)
    eligible = [
        (float(recall_curve[index]), float(thresholds[index]))
        for index in range(len(thresholds))
        if precision_curve[index] >= 0.80
    ]
    recall_at_precision = max(eligible, default=(0.0, None), key=lambda item: item[0])
    metrics = {
        "dataset": str(dataset_path),
        "split_column": split_column,
        "split": split,
        "seed": seed,
        "sessions": int(len(sessions)),
        "threshold": 0.30,
        "accuracy": float(accuracy),
        "precision": float(precision),
        "recall": float(recall),
        "average_precision": float(average_precision_score(labels, probabilities)),
        "roc_auc": float(roc_auc_score(labels, probabilities)) if labels.nunique() == 2 else None,
        "recall_at_precision_0_80": recall_at_precision[0],
        "threshold_at_precision_0_80": recall_at_precision[1],
        "brier_score": float(((probabilities - labels) ** 2).mean()),
        "confusion_matrix": {
            "true_positive": int(true_positive),
            "false_positive": int(false_positive),
            "false_negative": int(false_negative),
            "true_negative": int(true_negative),
        },
        "label_source": "onboarding_scenario (synthetic generator scenario label)",
        "calibration_bins": [],
    }
    for lower in (0.0, 0.2, 0.4, 0.6, 0.8):
        upper = lower + 0.2
        in_bin = (probabilities >= lower) & (
            (probabilities < upper) if upper < 1.0 else (probabilities <= upper)
        )
        if in_bin.any():
            metrics["calibration_bins"].append({
                "lower": lower,
                "upper": upper,
                "count": int(in_bin.sum()),
                "mean_predicted_risk": float(probabilities[in_bin].mean()),
                "observed_difficult_rate": float(labels[in_bin].mean()),
            })

    print(f"Split: {split}")
    print(f"Sessions: {len(sessions)}")
    print(f"Accuracy: {accuracy * 100:.2f}%")
    print(f"Precision: {precision * 100:.2f}%")
    print(f"Recall: {recall * 100:.2f}%")
    print(f"PR-AUC (average precision): {metrics['average_precision']:.4f}")
    print(f"Recall at precision >= 0.80: {metrics['recall_at_precision_0_80'] * 100:.2f}%")
    print(f"Brier score: {metrics['brier_score']:.4f}")
    print("\nConfusion matrix:")
    print(f"  True positives:  {true_positive}")
    print(f"  False positives: {false_positive}")
    print(f"  False negatives: {false_negative}")
    print(f"  True negatives:  {true_negative}")
    print("\nAverage score by scenario:")
    print(sessions.groupby("scenario")["score"].mean().round(3).to_string())
    return metrics


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--split", default="test")
    parser.add_argument("--split-column", choices=("split", "agent_split"), default="split")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    metrics = evaluate(args.dataset, args.split, args.split_column, args.seed)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(metrics, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
