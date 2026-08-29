import pandas as pd
from pathlib import Path
from sklearn.model_selection import train_test_split

INPUT_FILE = "clean_multi_industry_onboarding_dataset.csv"
OUTPUT_DIR = Path("data/splits")

TRAIN_FILE = OUTPUT_DIR / "train.csv"
VALIDATION_FILE = OUTPUT_DIR / "validation.csv"
TEST_FILE = OUTPUT_DIR / "test.csv"

RANDOM_STATE = 42


def main():
    print("Loading clean dataset...")

    df = pd.read_csv(INPUT_FILE)

    print(f"Dataset shape: {df.shape}")

    # Get unique onboarding sessions
    session_ids = df["session_id"].unique()

    print(f"Unique sessions: {len(session_ids)}")

    # 70% training, 30% temporary
    train_sessions, temp_sessions = train_test_split(
        session_ids,
        test_size=0.30,
        random_state=RANDOM_STATE
    )

    # Split remaining 30% into 15% validation and 15% test
    validation_sessions, test_sessions = train_test_split(
        temp_sessions,
        test_size=0.50,
        random_state=RANDOM_STATE
    )

    train_set = set(train_sessions)
    validation_set = set(validation_sessions)
    test_set = set(test_sessions)

    # Create event-level datasets while keeping complete sessions together
    train_df = df[df["session_id"].isin(train_set)].copy()
    validation_df = df[df["session_id"].isin(validation_set)].copy()
    test_df = df[df["session_id"].isin(test_set)].copy()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    train_df.to_csv(TRAIN_FILE, index=False)
    validation_df.to_csv(VALIDATION_FILE, index=False)
    test_df.to_csv(TEST_FILE, index=False)

    # Verify there is no session leakage
    train_sessions_final = set(train_df["session_id"])
    validation_sessions_final = set(validation_df["session_id"])
    test_sessions_final = set(test_df["session_id"])

    assert not train_sessions_final & validation_sessions_final
    assert not train_sessions_final & test_sessions_final
    assert not validation_sessions_final & test_sessions_final

    print("\nSPLIT COMPLETE")
    print(f"Training:   {train_df.shape} | Sessions: {len(train_sessions_final)}")
    print(f"Validation: {validation_df.shape} | Sessions: {len(validation_sessions_final)}")
    print(f"Test:       {test_df.shape} | Sessions: {len(test_sessions_final)}")

    print("\nSession leakage check: PASSED")
    print(f"Training file:   {TRAIN_FILE}")
    print(f"Validation file: {VALIDATION_FILE}")
    print(f"Test file:       {TEST_FILE}")


if __name__ == "__main__":
    main()
