"""Evaluate completion-risk detection against the held-out session dataset."""

import argparse
from pathlib import Path

import pandas as pd

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
        + (grouped["elapsed_minutes"] > 15) * 0.15
        + grouped["field_revisits"] * 0.10
        + (grouped["progress_percent"] < 50) * 0.10
    ).clip(upper=1.0)
    grouped["predicted_difficult"] = grouped["score"] >= 0.30
    grouped["actual_difficult"] = grouped["scenario"].isin(DIFFICULT_SCENARIOS)
    return grouped


def evaluate(dataset_path: Path, split: str) -> None:
    dataset = pd.read_csv(dataset_path)
    required = {
        "session_id",
        "event_timestamp",
        "onboarding_scenario",
        "event_duration_minutes",
        "validation_status",
        "is_revisit",
        "onboarding_progress",
        "split",
    }
    missing = required - set(dataset.columns)
    if missing:
        raise ValueError(f"Dataset is missing columns: {sorted(missing)}")

    sessions = score_sessions(dataset[dataset["split"] == split])
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

    print(f"Split: {split}")
    print(f"Sessions: {len(sessions)}")
    print(f"Accuracy: {accuracy * 100:.2f}%")
    print(f"Precision: {precision * 100:.2f}%")
    print(f"Recall: {recall * 100:.2f}%")
    print("\nConfusion matrix:")
    print(f"  True positives:  {true_positive}")
    print(f"  False positives: {false_positive}")
    print(f"  False negatives: {false_negative}")
    print(f"  True negatives:  {true_negative}")
    print("\nAverage score by scenario:")
    print(sessions.groupby("scenario")["score"].mean().round(3).to_string())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--split", default="test")
    args = parser.parse_args()
    evaluate(args.dataset, args.split)


if __name__ == "__main__":
    main()
