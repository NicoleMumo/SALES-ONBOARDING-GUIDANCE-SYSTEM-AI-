"""Evaluate the app's completion-risk heuristic against observed abandonment."""

import argparse
import json
from pathlib import Path

import pandas as pd
from sklearn.metrics import average_precision_score, precision_recall_curve


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = ROOT / "data" / "onboarding_dataset_v3.csv"
PROGRESS_CHECKPOINTS = (25, 50, 75)
REQUIRED_COLUMNS = {
    "session_id",
    "event_timestamp",
    "event_duration_minutes",
    "validation_status",
    "is_revisit",
    "onboarding_progress",
    "session_status",
    "split",
}


def score_sessions(
    dataset: pd.DataFrame,
    threshold: float,
    progress_cutoff: float,
) -> pd.DataFrame:
    """Score each session using only events observed by the progress cutoff."""
    ordered = dataset.assign(
        event_timestamp=pd.to_datetime(dataset["event_timestamp"], errors="raise"),
        onboarding_progress=pd.to_numeric(
            dataset["onboarding_progress"], errors="raise"
        ),
        event_duration_minutes=pd.to_numeric(
            dataset["event_duration_minutes"], errors="raise"
        ),
        is_revisit=dataset["is_revisit"].astype(str).str.lower().isin(
            {"true", "1", "yes"}
        ),
    ).sort_values(["session_id", "event_timestamp"])

    scored = []
    for session_id, events in ordered.groupby("session_id", sort=False):
        final_status = str(events.iloc[-1]["session_status"])
        if final_status not in {"completed", "abandoned"}:
            raise ValueError(
                f"Session {session_id!r} has unsupported final status "
                f"{final_status!r}; expected completed or abandoned."
            )

        observed = events[events["onboarding_progress"] <= progress_cutoff]
        if observed.empty:
            observed = events.iloc[:1]

        progress = float(observed.iloc[-1]["onboarding_progress"])
        elapsed_minutes = float(observed["event_duration_minutes"].max())
        validation_errors = int((observed["validation_status"] == "failed").sum())
        field_revisits = int(observed["is_revisit"].sum())
        score = min(
            0.05
            + (validation_errors > 0) * 0.25
            + (elapsed_minutes > 15) * 0.15
            + field_revisits * 0.10
            + (progress < 50) * 0.10,
            1.0,
        )
        scored.append(
            {
                "session_id": session_id,
                "final_status": final_status,
                "actual_abandoned": final_status == "abandoned",
                "progress_at_snapshot": progress,
                "score": score,
                "predicted_abandoned": score >= threshold,
                "validation_errors": validation_errors,
                "field_revisits": field_revisits,
                "elapsed_minutes": elapsed_minutes,
            }
        )
    return pd.DataFrame(scored).set_index("session_id")


def calculate_metrics(sessions: pd.DataFrame, threshold: float) -> dict:
    predicted = sessions["predicted_abandoned"]
    actual = sessions["actual_abandoned"]
    scores = sessions["score"]
    true_positive = int((predicted & actual).sum())
    false_positive = int((predicted & ~actual).sum())
    false_negative = int((~predicted & actual).sum())
    true_negative = int((~predicted & ~actual).sum())

    precision = (
        true_positive / (true_positive + false_positive)
        if true_positive + false_positive
        else 0.0
    )
    recall = (
        true_positive / (true_positive + false_negative)
        if true_positive + false_negative
        else 0.0
    )
    accuracy = (true_positive + true_negative) / len(sessions)
    average_precision = (
        float(average_precision_score(actual, scores)) if actual.any() else 0.0
    )
    curve_precision, curve_recall, _ = precision_recall_curve(actual, scores)
    eligible_recall = curve_recall[:-1][curve_precision[:-1] >= 0.80]
    recall_at_precision = (
        float(eligible_recall.max()) if len(eligible_recall) else None
    )

    calibration_bins = []
    for bin_index in range(10):
        lower = bin_index / 10
        upper = (bin_index + 1) / 10
        in_bin = (scores >= lower) & (
            scores < upper if bin_index < 9 else scores <= upper
        )
        if in_bin.any():
            calibration_bins.append(
                {
                    "lower_score": round(lower, 1),
                    "upper_score": round(upper, 1),
                    "count": int(in_bin.sum()),
                    "mean_score": float(scores[in_bin].mean()),
                    "observed_abandonment_rate": float(actual[in_bin].mean()),
                }
            )

    return {
        "session_count": int(len(sessions)),
        "abandoned_session_count": int(actual.sum()),
        "decision_threshold": threshold,
        "accuracy": float(accuracy),
        "precision_at_threshold": float(precision),
        "recall_at_threshold": float(recall),
        "pr_auc_average_precision": average_precision,
        "recall_at_precision_0_80": recall_at_precision,
        "confusion_matrix": {
            "true_positive": true_positive,
            "false_positive": false_positive,
            "false_negative": false_negative,
            "true_negative": true_negative,
        },
        "calibration_bins": calibration_bins,
    }


def evaluate(
    dataset_path: Path,
    split: str,
    threshold: float,
    output_path: Path,
) -> None:
    dataset = pd.read_csv(dataset_path)
    missing = REQUIRED_COLUMNS - set(dataset.columns)
    if missing:
        raise ValueError(f"Dataset is missing columns: {sorted(missing)}")
    if split not in set(dataset["split"].dropna().unique()):
        raise ValueError(f"No sessions found for split '{split}'.")

    split_data = dataset[dataset["split"] == split]
    results = {}
    for checkpoint in PROGRESS_CHECKPOINTS:
        sessions = score_sessions(split_data, threshold, checkpoint)
        metrics = calculate_metrics(sessions, threshold)
        results[str(checkpoint)] = metrics

    result = {
        "dataset": str(dataset_path),
        "split_column": "split",
        "split": split,
        "target": "final session_status == abandoned",
        "decision_threshold": threshold,
        "progress_checkpoints_percent": list(PROGRESS_CHECKPOINTS),
        "limitations": [
            "The dataset is synthetic; high scores do not establish real-world predictive performance.",
            "The risk score is a heuristic, not a calibrated probability.",
            "Threshold selection belongs on the validation split; report final metrics only on the untouched test split.",
        ],
        "metrics_by_progress_checkpoint": results,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")

    print(f"Dataset: {dataset_path}")
    print(f"Split: split={split}")
    for checkpoint, metrics in results.items():
        print(
            f"{checkpoint}% progress | sessions {metrics['session_count']:,} | "
            f"abandoned {metrics['abandoned_session_count']:,} | "
            f"precision {metrics['precision_at_threshold']:.2%} | "
            f"recall {metrics['recall_at_threshold']:.2%} | "
            f"PR-AUC {metrics['pr_auc_average_precision']:.4f}"
        )
    print(f"Saved machine-readable results to: {output_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--split", choices=("train", "validation", "test"), default="test")
    parser.add_argument("--threshold", type=float, default=0.30)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not 0.0 <= args.threshold <= 1.0:
        parser.error("--threshold must be between 0 and 1")
    output_path = args.output or (
        ROOT
        / "results"
        / f"completion-risk-split-{args.split}-threshold-{args.threshold:.2f}.json"
    )
    evaluate(args.dataset, args.split, args.threshold, output_path)


if __name__ == "__main__":
    main()
