"""Report dataset quality and next-field lookup baselines.

Usage:
    python data/check_dataset.py data/onboarding_dataset_v3.csv
    python data/check_dataset.py data/onboarding_dataset_v3.csv --compare path/to/raw.csv
"""

import argparse
import csv
from collections import Counter, defaultdict
from pathlib import Path
from statistics import median


REQUIRED_COLUMNS = {
    "session_id",
    "sales_agent_id",
    "context_value",
    "onboarding_field",
    "event_timestamp",
    "event_duration_minutes",
    "agent_response_time_minutes",
    "previous_onboarding_field",
    "next_onboarding_field",
    "validation_status",
    "failure_reason",
    "is_revisit",
    "session_status",
    "split",
}


def read_dataset(path):
    with path.open(newline="", encoding="utf-8") as dataset_file:
        reader = csv.DictReader(dataset_file)
        missing = REQUIRED_COLUMNS - set(reader.fieldnames or ())
        if missing:
            raise ValueError(f"{path} is missing columns: {sorted(missing)}")
        rows = list(reader)
    if not rows:
        raise ValueError(f"{path} contains no data rows.")
    return rows


def deduplicate(rows):
    seen = set()
    unique_rows = []
    for row in rows:
        key = tuple(row.items())
        if key not in seen:
            seen.add(key)
            unique_rows.append(row)
    return unique_rows, len(rows) - len(unique_rows)


def majority_table(rows, columns):
    counts = defaultdict(Counter)
    for row in rows:
        key = tuple(row[column] or "" for column in columns)
        counts[key][row["next_onboarding_field"]] += 1
    return {key: targets.most_common(1)[0][0] for key, targets in counts.items()}


def predict(table, rows, columns, fallback):
    predictions = []
    for row in rows:
        key = tuple(row[column] or "" for column in columns)
        predictions.append(table.get(key, fallback(row)))
    return predictions


def next_field_baselines(rows):
    labeled_rows = [row for row in rows if row["next_onboarding_field"]]
    training = [row for row in labeled_rows if row["split"] == "train"]
    test = [row for row in labeled_rows if row["split"] == "test"]
    if not training or not test:
        raise ValueError("The dataset needs labeled train and test rows for baselines.")

    global_majority = Counter(
        row["next_onboarding_field"] for row in training
    ).most_common(1)[0][0]
    baseline_keys = (
        ("B1 (context, field)", ("context_value", "onboarding_field")),
        (
            "B2 (+ previous field, validation)",
            (
                "context_value",
                "previous_onboarding_field",
                "onboarding_field",
                "validation_status",
            ),
        ),
        (
            "B3 (+ agent id)",
            (
                "sales_agent_id",
                "context_value",
                "previous_onboarding_field",
                "onboarding_field",
                "validation_status",
            ),
        ),
    )

    results = {}
    prior_predictions = None
    for name, columns in baseline_keys:
        table = majority_table(training, columns)
        if prior_predictions is None:
            fallback = lambda row: global_majority
        else:
            prior_by_row = {
                id(row): prediction for row, prediction in zip(test, prior_predictions)
            }
            fallback = lambda row: prior_by_row[id(row)]
        predictions = predict(table, test, columns, fallback)
        accuracy = sum(
            prediction == row["next_onboarding_field"]
            for prediction, row in zip(predictions, test)
        ) / len(test)
        results[name] = accuracy
        prior_predictions = predictions
    return results


def _number(row, key):
    try:
        return float(row[key] or 0)
    except ValueError as error:
        raise ValueError(f"Invalid number in {key}: {row[key]!r}") from error


def _is_true(value):
    return value.strip().lower() in {"true", "1", "yes"}


def report(path):
    all_rows = read_dataset(path)
    rows, duplicate_count = deduplicate(all_rows)
    sessions = defaultdict(list)
    for row in rows:
        sessions[row["session_id"]].append(row)
    for events in sessions.values():
        events.sort(key=lambda row: row["event_timestamp"])

    event_counts = [len(events) for events in sessions.values()]
    session_minutes = [
        sum(
            _number(event, "event_duration_minutes")
            + _number(event, "agent_response_time_minutes")
            for event in events
        )
        for events in sessions.values()
    ]
    final_statuses = Counter(events[-1]["session_status"] for events in sessions.values())
    split_sessions = defaultdict(set)
    for row in rows:
        split_sessions[row["split"]].add(row["session_id"])
    failures = [row for row in rows if row["validation_status"] == "failed"]
    failed_fields = Counter(row["onboarding_field"] for row in failures)
    revisits = sum(_is_true(row["is_revisit"]) for row in rows)
    missing_reasons = sum(not row["failure_reason"].strip() for row in failures)

    print(f"\n=== {path}")
    print(
        f"sessions {len(sessions):,}  events {len(rows):,}  "
        f"agents {len({row['sales_agent_id'] for row in rows}):,}  "
        f"exact duplicate rows removed {duplicate_count:,}"
    )
    print(
        f"events/session median {median(event_counts):.0f}  "
        f"p90 {sorted(event_counts)[int(0.9 * (len(event_counts) - 1))]}  "
        f"max {max(event_counts)}  "
        f">30 events {sum(count > 30 for count in event_counts) / len(event_counts):.1%}"
    )
    print(
        f"session minutes median {median(session_minutes):.1f}  "
        f"p90 {sorted(session_minutes)[int(0.9 * (len(session_minutes) - 1))]:.1f}"
    )
    print("final status:", dict(Counter(final_statuses).most_common()))
    print(f"validation failure rate per event: {len(failures) / len(rows):.3f}")
    print(f"revisit rate per event: {revisits / len(rows):.3f}")
    print(f"failed events with no reason: {missing_reasons:,}")
    print("most-failed fields:", dict(failed_fields.most_common(5)))
    print("split (sessions):", {key: len(value) for key, value in split_sessions.items()})
    for name, accuracy in next_field_baselines(rows).items():
        print(f"  {name:38s} test next-field accuracy {accuracy:.2%}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    parser.add_argument("--compare", type=Path)
    args = parser.parse_args()
    report(args.path)
    if args.compare:
        report(args.compare)


if __name__ == "__main__":
    main()
