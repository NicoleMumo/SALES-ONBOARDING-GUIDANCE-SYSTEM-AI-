import pandas as pd
from pathlib import Path


INPUT_FILE = "multi_industry_onboarding_dataset.csv"
OUTPUT_FILE = "clean_multi_industry_onboarding_dataset.csv"


EXPECTED_COLUMNS = [
    "session_id",
    "customer_id",
    "sales_agent_id",
    "industry",
    "onboarding_scenario",
    "onboarding_stage",
    "onboarding_field",
    "event_name",
    "event_timestamp",
    "event_status",
    "event_duration_minutes",
    "is_revisit",
    "previous_onboarding_field",
    "next_onboarding_field",
    "validation_status",
    "failure_reason",
    "onboarding_progress",
    "onboarding_risk_score",
    "onboarding_risk_level",
    "agent_action",
    "agent_response_time_minutes",
    "agent_guidance_given",
    "agent_correction_count",
    "agent_escalation",
    "real_time_guidance",
    "session_status",
]


def validate_columns(df):
    missing_columns = [
        column for column in EXPECTED_COLUMNS
        if column not in df.columns
    ]

    if missing_columns:
        raise ValueError(
            f"Missing required columns: {missing_columns}"
        )


def clean_dataset(df):
    df = df.copy()

    # ------------------------------------------------------------
    # Remove exact duplicate events
    # ------------------------------------------------------------
    before = len(df)

    df = df.drop_duplicates()

    removed_duplicates = before - len(df)

    # ------------------------------------------------------------
    # Convert timestamp
    # ------------------------------------------------------------
    df["event_timestamp"] = pd.to_datetime(
        df["event_timestamp"],
        errors="coerce"
    )

    # ------------------------------------------------------------
    # Validate numeric values
    # ------------------------------------------------------------
    df["event_duration_minutes"] = pd.to_numeric(
        df["event_duration_minutes"],
        errors="coerce"
    )

    df["agent_response_time_minutes"] = pd.to_numeric(
        df["agent_response_time_minutes"],
        errors="coerce"
    )

    df["onboarding_progress"] = pd.to_numeric(
        df["onboarding_progress"],
        errors="coerce"
    )

    df["onboarding_risk_score"] = pd.to_numeric(
        df["onboarding_risk_score"],
        errors="coerce"
    )

    df["agent_correction_count"] = pd.to_numeric(
        df["agent_correction_count"],
        errors="coerce"
    )

    # ------------------------------------------------------------
    # Remove impossible numeric values
    # ------------------------------------------------------------
    df = df[
        (df["event_duration_minutes"] > 0)
        & (df["agent_response_time_minutes"] >= 0)
        & (df["onboarding_progress"].between(0, 100))
        & (df["onboarding_risk_score"].between(0, 1))
        & (df["agent_correction_count"] >= 0)
    ]

    # ------------------------------------------------------------
    # Validate industries
    # ------------------------------------------------------------
    valid_industries = {"banking", "telecom"}

    df = df[
        df["industry"].isin(valid_industries)
    ]

    # ------------------------------------------------------------
    # Validate scenarios
    # ------------------------------------------------------------
    valid_scenarios = {
        "normal_completion",
        "validation_errors",
        "revisited_fields",
        "long_completion_times",
        "incomplete_sessions",
    }

    df = df[
        df["onboarding_scenario"].isin(valid_scenarios)
    ]

    # ------------------------------------------------------------
    # Validate event statuses
    # ------------------------------------------------------------
    valid_event_statuses = {
        "completed",
        "failed",
    }

    df = df[
        df["event_status"].isin(valid_event_statuses)
    ]

    # ------------------------------------------------------------
    # Validate validation statuses
    # ------------------------------------------------------------
    valid_validation_statuses = {
        "passed",
        "failed",
    }

    df = df[
        df["validation_status"].isin(valid_validation_statuses)
    ]

    # ------------------------------------------------------------
    # Failed validation must have a failure reason
    # Passed validation must not have a failure reason
    # ------------------------------------------------------------
    failed_without_reason = (
        (df["validation_status"] == "failed")
        & (df["failure_reason"].isna())
    )

    passed_with_reason = (
        (df["validation_status"] == "passed")
        & (df["failure_reason"].notna())
    )

    df = df[
        ~failed_without_reason
        & ~passed_with_reason
    ]

    # ------------------------------------------------------------
    # Validate event status against validation status
    # ------------------------------------------------------------
    df = df[
        ~(
            (df["validation_status"] == "failed")
            & (df["event_status"] != "failed")
        )
    ]

    df = df[
        ~(
            (df["validation_status"] == "passed")
            & (df["event_status"] != "completed")
        )
    ]

    # ------------------------------------------------------------
    # Validate revisit information
    # ------------------------------------------------------------
    df = df[
        ~(
            (df["is_revisit"] == True)
            & (
                df["previous_onboarding_field"].isna()
            )
        )
    ]

    # ------------------------------------------------------------
    # Validate agent guidance
    # ------------------------------------------------------------
    df = df[
        ~(
            (df["agent_guidance_given"] == True)
            & (df["real_time_guidance"].isna())
        )
    ]

    # ------------------------------------------------------------
    # Validate correction counts
    # ------------------------------------------------------------
    # FIX: corrections are NOT exclusive to validation failures in
    # this generator — generate_agent_behaviour() also produces a
    # non-zero agent_correction_count for revisits, incomplete
    # sessions, and long-completion scenarios, none of which imply
    # validation_status == "failed". The original check here
    # ("correction_count > 0 and validation_status != failed" ->
    # drop) was silently removing ~9.5% of otherwise valid rows,
    # concentrated in exactly the scenario types meant to add
    # variety. The only genuinely impossible state is a correction
    # being logged with no agent action recorded at all.
    df = df[
        ~(
            (df["agent_correction_count"] > 0)
            & (df["agent_action"].isna())
        )
    ]

    # ------------------------------------------------------------
    # Validate risk levels against risk scores
    # ------------------------------------------------------------
    def expected_risk_level(score):
        if score < 0.30:
            return "Low"
        elif score < 0.70:
            return "Medium"
        return "High"

    df = df[
        df["onboarding_risk_level"]
        == df["onboarding_risk_score"].apply(
            expected_risk_level
        )
    ]

    # ------------------------------------------------------------
    # Sort events by session and timestamp
    # ------------------------------------------------------------
    df = df.sort_values(
        ["session_id", "event_timestamp"]
    ).reset_index(drop=True)

    return df, removed_duplicates


def validate_final_dataset(df):
    problems = []

    if df.empty:
        problems.append("Dataset is empty.")

    if df.duplicated().sum() > 0:
        problems.append("Exact duplicate rows remain.")

    if df["event_timestamp"].isna().any():
        problems.append("Invalid timestamps remain.")

    if not df["event_duration_minutes"].gt(0).all():
        problems.append("Invalid event durations remain.")

    if not df["onboarding_progress"].between(0, 100).all():
        problems.append("Invalid onboarding progress remains.")

    if not df["onboarding_risk_score"].between(0, 1).all():
        problems.append("Invalid risk scores remain.")

    if not df["industry"].isin(
        {"banking", "telecom"}
    ).all():
        problems.append("Invalid industries remain.")

    if problems:
        print("\nVALIDATION FAILED")

        for problem in problems:
            print(f"- {problem}")

        return False

    print("\nFINAL VALIDATION PASSED")

    return True


def main():

    print("Loading dataset...")

    input_path = Path(INPUT_FILE)

    if not input_path.exists():
        raise FileNotFoundError(
            f"Input dataset not found: {INPUT_FILE}"
        )

    df = pd.read_csv(INPUT_FILE)

    print(f"Original shape: {df.shape}")

    validate_columns(df)

    cleaned_df, removed_duplicates = clean_dataset(df)

    print(
        f"Exact duplicate rows removed: "
        f"{removed_duplicates}"
    )

    print(
        f"Cleaned shape: {cleaned_df.shape}"
    )

    if not validate_final_dataset(cleaned_df):
        raise ValueError(
            "Dataset failed final validation."
        )

    cleaned_df.to_csv(
        OUTPUT_FILE,
        index=False
    )

    print(
        f"\nClean dataset saved to: "
        f"{OUTPUT_FILE}"
    )

    print("\nIndustries:")
    print(
        cleaned_df["industry"].value_counts()
    )

    print("\nScenarios:")
    print(
        cleaned_df[
            "onboarding_scenario"
        ].value_counts()
    )

    print("\nOnboarding fields (should be individual fields, NOT broad stages):")
    print(
        cleaned_df["onboarding_field"].value_counts().to_string()
    )

    print("\nMissing values:")
    print(
        cleaned_df.isnull().sum()[
            cleaned_df.isnull().sum() > 0
        ]
    )

    print(
        f"\nFinal dataset contains "
        f"{len(cleaned_df)} events."
    )


if __name__ == "__main__":
    main()