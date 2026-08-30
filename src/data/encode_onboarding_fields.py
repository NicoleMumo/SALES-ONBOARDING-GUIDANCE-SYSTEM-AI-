import json
import pandas as pd
from pathlib import Path


# ============================================================
# PATHS
# ============================================================

SEQUENCES_DIR = Path("data/sequences")
VOCABULARY_DIR = Path("data/vocabulary")
ENCODED_DIR = Path("data/encoded_sequences")

TRAIN_INPUT = SEQUENCES_DIR / "train_sequences.csv"
VALIDATION_INPUT = SEQUENCES_DIR / "validation_sequences.csv"
TEST_INPUT = SEQUENCES_DIR / "test_sequences.csv"

TRAIN_OUTPUT = ENCODED_DIR / "train_sequences_encoded.csv"
VALIDATION_OUTPUT = ENCODED_DIR / "validation_sequences_encoded.csv"
TEST_OUTPUT = ENCODED_DIR / "test_sequences_encoded.csv"

VOCABULARY_OUTPUT = VOCABULARY_DIR / "field_vocabulary.json"


# ============================================================
# BUILD VOCABULARY
# ============================================================

def build_vocabulary(train_df):
    """
    Create the field vocabulary from the training data only.

    Each unique onboarding field receives one numerical ID.
    The same ID is then used across training, validation and test.
    """

    fields = set()

    for sequence in train_df["input_sequence"]:
        fields.update(
            field.strip()
            for field in sequence.split("|")
            if field.strip()
        )

    fields.update(
        train_df["next_field"]
        .dropna()
        .astype(str)
        .str.strip()
        .tolist()
    )

    fields = sorted(fields)

    vocabulary = {
        field: field_id
        for field_id, field in enumerate(fields)
    }

    return vocabulary


# ============================================================
# ENCODE SEQUENCES
# ============================================================

def encode_sequence(sequence, vocabulary):
    """Convert a field sequence into numerical field IDs."""

    fields = [
        field.strip()
        for field in sequence.split("|")
        if field.strip()
    ]

    return [
        vocabulary[field]
        for field in fields
    ]


def encode_dataset(df, vocabulary):
    """Encode input sequences and prediction targets."""

    df = df.copy()

    df["input_sequence_ids"] = df["input_sequence"].apply(
        lambda sequence: encode_sequence(sequence, vocabulary)
    )

    df["next_field_id"] = df["next_field"].map(vocabulary)

    if df["next_field_id"].isna().any():
        missing = (
            df.loc[df["next_field_id"].isna(), "next_field"]
            .unique()
            .tolist()
        )

        raise ValueError(
            f"Fields found outside the training vocabulary: {missing}"
        )

    df["next_field_id"] = df["next_field_id"].astype(int)

    return df


# ============================================================
# PROCESS FILE
# ============================================================

def process_file(input_file, output_file, vocabulary):

    df = pd.read_csv(input_file)

    encoded_df = encode_dataset(df, vocabulary)

    output_file.parent.mkdir(
        parents=True,
        exist_ok=True
    )

    encoded_df.to_csv(
        output_file,
        index=False
    )

    print(f"\n{input_file}")
    print(f"Sequences: {len(df)}")
    print(f"Encoded output: {output_file}")

    return encoded_df


# ============================================================
# MAIN
# ============================================================

def main():

    print("Encoding onboarding fields...")
    print("--------------------------------")

    # --------------------------------------------------------
    # Load training data FIRST
    # --------------------------------------------------------

    train_df = pd.read_csv(TRAIN_INPUT)

    print(f"Training sequences: {len(train_df)}")

    # --------------------------------------------------------
    # Build vocabulary ONLY from training data
    # --------------------------------------------------------

    vocabulary = build_vocabulary(train_df)

    print(f"\nUnique onboarding fields: {len(vocabulary)}")

    print("\nFIELD VOCABULARY")

    for field, field_id in vocabulary.items():
        print(f"{field_id:3d} -> {field}")

    # --------------------------------------------------------
    # Save vocabulary
    # --------------------------------------------------------

    VOCABULARY_DIR.mkdir(
        parents=True,
        exist_ok=True
    )

    with open(
        VOCABULARY_OUTPUT,
        "w",
        encoding="utf-8"
    ) as file:

        json.dump(
            vocabulary,
            file,
            indent=4
        )

    print(
        f"\nVocabulary saved to: {VOCABULARY_OUTPUT}"
    )

    # --------------------------------------------------------
    # Encode all three splits using SAME vocabulary
    # --------------------------------------------------------

    train_encoded = process_file(
        TRAIN_INPUT,
        TRAIN_OUTPUT,
        vocabulary
    )

    validation_encoded = process_file(
        VALIDATION_INPUT,
        VALIDATION_OUTPUT,
        vocabulary
    )

    test_encoded = process_file(
        TEST_INPUT,
        TEST_OUTPUT,
        vocabulary
    )

    # --------------------------------------------------------
    # Final validation
    # --------------------------------------------------------

    print("\n--------------------------------")
    print("ENCODING VALIDATION")

    for name, df in [
        ("Training", train_encoded),
        ("Validation", validation_encoded),
        ("Test", test_encoded)
    ]:

        invalid_inputs = df[
            df["input_sequence_ids"].isna()
        ]

        invalid_targets = df[
            df["next_field_id"].isna()
        ]

        print(
            f"{name}: "
            f"{len(df)} sequences | "
            f"invalid targets: {len(invalid_targets)}"
        )

    print("\nENCODING COMPLETE")


if __name__ == "__main__":
    main()