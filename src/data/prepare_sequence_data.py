import pandas as pd
from pathlib import Path

INPUT_DIR = Path("data/splits")
OUTPUT_DIR = Path("data/sequences")

TRAIN_INPUT = INPUT_DIR / "train.csv"
VALIDATION_INPUT = INPUT_DIR / "validation.csv"
TEST_INPUT = INPUT_DIR / "test.csv"

TRAIN_OUTPUT = OUTPUT_DIR / "train_sequences.csv"
VALIDATION_OUTPUT = OUTPUT_DIR / "validation_sequences.csv"
TEST_OUTPUT = OUTPUT_DIR / "test_sequences.csv"


def create_sequences(df):
    sequences = []

    # Identify sessions that actually completed
    completed_session_ids = set(
        df.loc[
            df["session_status"] == "completed",
            "session_id"
        ]
    )

    # Keep ALL events belonging to completed sessions
    df = df[
        df["session_id"].isin(completed_session_ids)
    ].copy()

    for session_id, session in df.groupby("session_id"):

        session = session.sort_values("event_timestamp")

        fields = session["onboarding_field"].tolist()

        # Need at least two events to create
        # an input sequence and next-field target
        if len(fields) < 2:
            continue

        # Create next-field prediction examples
        for i in range(1, len(fields)):

            input_sequence = fields[:i]
            target = fields[i]

            sequences.append({
                "session_id": session_id,
                "input_sequence": " | ".join(input_sequence),
                "next_field": target
            })

    return pd.DataFrame(sequences)


def process_file(input_file, output_file):

    df = pd.read_csv(input_file)

    sequences = create_sequences(df)

    output_file.parent.mkdir(
        parents=True,
        exist_ok=True
    )

    sequences.to_csv(
        output_file,
        index=False
    )

    print(f"\n{input_file}")
    print(f"Input events: {len(df)}")
    print(
        f"Completed sessions: "
        f"{df.loc[df['session_status'] == 'completed', 'session_id'].nunique()}"
    )
    print(f"Sequences created: {len(sequences)}")
    print(f"Output: {output_file}")

    return sequences


def main():

    print(
        "Preparing Transformer sequence training data..."
    )

    train = process_file(
        TRAIN_INPUT,
        TRAIN_OUTPUT
    )

    validation = process_file(
        VALIDATION_INPUT,
        VALIDATION_OUTPUT
    )

    test = process_file(
        TEST_INPUT,
        TEST_OUTPUT
    )

    print("\nSEQUENCE PREPARATION COMPLETE")

    print(
        f"Training sequences: {len(train)}"
    )

    print(
        f"Validation sequences: {len(validation)}"
    )

    print(
        f"Test sequences: {len(test)}"
    )


if __name__ == "__main__":
    main()
