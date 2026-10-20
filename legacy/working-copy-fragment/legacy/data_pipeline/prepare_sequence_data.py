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

    for session_id, session in df.groupby("session_id"):

        # Ensure events are in chronological order
        session = session.sort_values("event_timestamp")

        fields = session["onboarding_field"].tolist()

        # Need at least:
        # one input field + one target field
        if len(fields) < 2:
            continue

        # Create prefix sequences
        #
        # Example:
        # A, B, C
        #
        # Input: A       -> Target: B
        # Input: A | B   -> Target: C

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

    # --------------------------------------------------------
    # Load event-level dataset
    # --------------------------------------------------------

    df = pd.read_csv(input_file)

    original_events = len(df)
    original_sessions = df["session_id"].nunique()

    # --------------------------------------------------------
    # Find completed sessions
    #
    # A session is considered completed if ANY event belonging
    # to that session has session_status = completed.
    # --------------------------------------------------------

    completed_session_ids = df.loc[
        df["session_status"] == "completed",
        "session_id"
    ].unique()

    # --------------------------------------------------------
    # Keep ALL events from completed sessions
    #
    # Do NOT filter individual rows directly by session_status.
    # --------------------------------------------------------

    df = df[
        df["session_id"].isin(completed_session_ids)
    ].copy()

    completed_events = len(df)
    completed_sessions = df["session_id"].nunique()

    # --------------------------------------------------------
    # Create Transformer training sequences
    # --------------------------------------------------------

    sequences = create_sequences(df)

    # --------------------------------------------------------
    # Save
    # --------------------------------------------------------

    output_file.parent.mkdir(
        parents=True,
        exist_ok=True
    )

    sequences.to_csv(
        output_file,
        index=False
    )

    # --------------------------------------------------------
    # Print results
    # --------------------------------------------------------

    print(f"\n{input_file}")

    print(f"Original events: {original_events}")
    print(f"Original sessions: {original_sessions}")

    print(f"Completed session events: {completed_events}")
    print(f"Completed sessions: {completed_sessions}")

    print(f"Sequences created: {len(sequences)}")

    print(f"Output: {output_file}")

    return sequences


def main():

    print("Preparing Transformer sequence training data...")

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

    print(f"Training sequences: {len(train)}")
    print(f"Validation sequences: {len(validation)}")
    print(f"Test sequences: {len(test)}")


if __name__ == "__main__":
    main()