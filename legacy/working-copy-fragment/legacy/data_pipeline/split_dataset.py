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

    # ------------------------------------------------------------
    # One row per session, carrying the columns we want the split
    # to be balanced on. Every event in a session shares the same
    # industry/scenario, so the first row per session is enough.
    # ------------------------------------------------------------
    session_meta = (
        df.drop_duplicates(subset="session_id")
        [["session_id", "industry", "onboarding_scenario"]]
        .reset_index(drop=True)
    )

    print(f"Unique sessions: {len(session_meta)}")

    # ------------------------------------------------------------
    # FIX: stratify by industry + scenario combined, not a plain
    # random split. Without this, splits at this session count
    # (2000) drift noticeably on the smaller scenario categories —
    # e.g. validation_errors ended up ~17% overrepresented and
    # normal_completion ~11% underrepresented in the validation
    # set purely from sampling noise, which biases any metric
    # computed on it and makes runs across reseeds incomparable.
    # ------------------------------------------------------------
    strat_key = (
        session_meta["industry"] + "_" + session_meta["onboarding_scenario"]
    )

    # 70% training, 30% temporary
    train_sessions, temp_sessions, train_strat, temp_strat = train_test_split(
        session_meta["session_id"],
        strat_key,
        test_size=0.30,
        random_state=RANDOM_STATE,
        stratify=strat_key,
    )

    # Split remaining 30% into 15% validation and 15% test
    validation_sessions, test_sessions = train_test_split(
        temp_sessions,
        test_size=0.50,
        random_state=RANDOM_STATE,
        stratify=temp_strat,
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

    # ------------------------------------------------------------
    # Verify every onboarding_field the model needs to predict in
    # validation/test was actually seen during training. If a field
    # only appears in val/test, the model was never trained on it
    # and next_onboarding_field predictions for it are meaningless.
    # ------------------------------------------------------------
    train_fields = set(train_df["onboarding_field"].unique())
    val_only_fields = set(validation_df["onboarding_field"].unique()) - train_fields
    test_only_fields = set(test_df["onboarding_field"].unique()) - train_fields

    if val_only_fields or test_only_fields:
        print("\nWARNING: fields present in val/test but never in train:")
        print("  validation:", val_only_fields)
        print("  test:", test_only_fields)
    else:
        print("\nField coverage check: PASSED (train covers every field seen in val/test)")

    print("\nSPLIT COMPLETE")
    print(f"Training:   {train_df.shape} | Sessions: {len(train_sessions_final)}")
    print(f"Validation: {validation_df.shape} | Sessions: {len(validation_sessions_final)}")
    print(f"Test:       {test_df.shape} | Sessions: {len(test_sessions_final)}")

    print("\nSession leakage check: PASSED")

    print("\nIndustry balance across splits:")
    for name, d in [("train", train_df), ("validation", validation_df), ("test", test_df)]:
        print(f"  {name:11s}", dict(d["industry"].value_counts(normalize=True).round(3)))

    print("\nScenario balance across splits:")
    for name, d in [("train", train_df), ("validation", validation_df), ("test", test_df)]:
        print(f"  {name:11s}", dict(d["onboarding_scenario"].value_counts(normalize=True).round(3)))

    print(f"\nTraining file:   {TRAIN_FILE}")
    print(f"Validation file: {VALIDATION_FILE}")
    print(f"Test file:       {TEST_FILE}")


if __name__ == "__main__":
    main()