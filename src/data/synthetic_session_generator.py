import csv
import random
import uuid
from datetime import datetime, timedelta


# ============================================================
# INDUSTRY-SPECIFIC ONBOARDING WORKFLOWS
# ============================================================

BANKING_WORKFLOW = [
    "Customer Information",
    "Identity Verification",
    "Document Verification",
    "Account Information",
    "Onboarding Confirmation"
]

TELECOM_WORKFLOW = [
    "Customer Information",
    "Service Selection",
    "Document Verification",
    "Installation",
    "Activation"
]


# ============================================================
# ONBOARDING SESSION SCENARIOS
# ============================================================

SCENARIOS = [
    "normal_completion",
    "validation_errors",
    "revisited_fields",
    "long_completion_times",
    "incomplete_sessions"
]

# Controlled probabilities.
# These are generation probabilities, not claims about the
# true real-world distribution.

SCENARIO_WEIGHTS = {
    "normal_completion": 0.50,
    "validation_errors": 0.15,
    "revisited_fields": 0.15,
    "long_completion_times": 0.10,
    "incomplete_sessions": 0.10
}


# ============================================================
# GENERATE IDENTIFIERS
# ============================================================

def generate_customer_id():
    return f"CUST{random.randint(10000, 99999)}"


def generate_sales_agent_id():
    return f"AGENT{random.randint(100, 999)}"


def generate_session_id():
    return f"SESSION-{uuid.uuid4().hex[:8].upper()}"


# ============================================================
# SELECT ONBOARDING SCENARIO
# ============================================================

def select_scenario():
    return random.choices(
        list(SCENARIO_WEIGHTS.keys()),
        weights=list(SCENARIO_WEIGHTS.values()),
        k=1
    )[0]


# ============================================================
# GENERATE EVENT DURATION
# ============================================================

def generate_event_duration(scenario):
    if scenario == "long_completion_times":
        return round(random.uniform(15, 60), 2)

    return round(random.uniform(1, 12), 2)


# ============================================================
# GENERATE VALIDATION RESULT
# ============================================================

def generate_validation_status(scenario):
    if scenario == "validation_errors":
        return random.choices(
            ["passed", "failed"],
            weights=[70, 30],
            k=1
        )[0]

    return "passed"


def generate_failure_reason(validation_status):
    if validation_status != "failed":
        return ""

    return random.choice([
        "Required information missing",
        "Invalid information entered",
        "Information could not be verified"
    ])


# ============================================================
# GENERATE ONBOARDING RISK
# ============================================================

def calculate_onboarding_risk(
    validation_status,
    event_duration,
    revisit_count,
    session_progress
):
    risk = 0.05

    if validation_status == "failed":
        risk += 0.25

    if event_duration > 15:
        risk += 0.15

    if revisit_count > 0:
        risk += 0.10 * revisit_count

    if session_progress < 50:
        risk += 0.10

    return min(round(risk, 2), 1.00)


def get_risk_level(risk_score):
    if risk_score < 0.30:
        return "Low"

    if risk_score < 0.70:
        return "Medium"

    return "High"


# ============================================================
# GENERATE ONE ONBOARDING SESSION
# ============================================================

def generate_session(industry, scenario=None):

    industry = industry.lower()

    if industry == "banking":
        workflow = BANKING_WORKFLOW.copy()

    elif industry == "telecom":
        workflow = TELECOM_WORKFLOW.copy()

    else:
        raise ValueError(
            "Industry must be 'banking' or 'telecom'"
        )

    if scenario is None:
        scenario = select_scenario()

    session_id = generate_session_id()
    customer_id = generate_customer_id()
    sales_agent_id = generate_sales_agent_id()

    start_time = datetime.now()

    # --------------------------------------------------------
    # Determine how many onboarding fields are completed
    # --------------------------------------------------------

    if scenario == "incomplete_sessions":

        number_of_fields = random.randint(
            1,
            len(workflow) - 1
        )

        fields = workflow[:number_of_fields]

    else:
        fields = workflow.copy()

    # --------------------------------------------------------
    # Introduce realistic revisits
    # --------------------------------------------------------

    field_sequence = []

    for field in fields:

        field_sequence.append(field)

        if (
            scenario == "revisited_fields"
            and len(field_sequence) > 1
            and random.random() < 0.30
        ):
            previous_field = random.choice(
                field_sequence[:-1]
            )

            field_sequence.append(previous_field)

    # --------------------------------------------------------
    # Generate Onboarding Events
    # --------------------------------------------------------

    events = []

    current_time = start_time
    revisit_count = 0

    for index, field in enumerate(field_sequence):

        is_revisit = field in field_sequence[:index]

        if is_revisit:
            revisit_count += 1

        duration = generate_event_duration(scenario)

        validation_status = generate_validation_status(
            scenario
        )

        failure_reason = generate_failure_reason(
            validation_status
        )

        if validation_status == "failed":
            event_status = "failed"

        else:
            event_status = "completed"

        # ----------------------------------------------------
        # Determine Next Onboarding Field
        # ----------------------------------------------------

        if index + 1 < len(field_sequence):

            next_onboarding_field = field_sequence[
                index + 1
            ]

        else:

            next_onboarding_field = ""

        # ----------------------------------------------------
        # Calculate progress
        # ----------------------------------------------------

        progress = round(
            (
                len(set(field_sequence[:index + 1]))
                / len(workflow)
            ) * 100,
            2
        )

        # ----------------------------------------------------
        # Calculate onboarding risk
        # ----------------------------------------------------

        risk_score = calculate_onboarding_risk(
            validation_status,
            duration,
            revisit_count,
            progress
        )

        risk_level = get_risk_level(
            risk_score
        )

        # ----------------------------------------------------
        # Generate Real-Time Guidance
        # ----------------------------------------------------

        if next_onboarding_field:

            guidance = (
                f"Review the current information "
                f"and proceed to {next_onboarding_field}."
            )

        else:

            guidance = (
                "Review the completed information "
                "and complete Customer Onboarding."
            )

        # ----------------------------------------------------
        # Session status
        # ----------------------------------------------------

        if scenario == "incomplete_sessions":

            session_status = "in_progress"

        elif (
            index == len(field_sequence) - 1
            and event_status == "completed"
        ):

            session_status = "completed"

        else:

            session_status = "in_progress"

        # ----------------------------------------------------
        # Create Onboarding Event
        # ----------------------------------------------------

        event = {
            "session_id": session_id,
            "customer_id": customer_id,
            "sales_agent_id": sales_agent_id,
            "industry": industry,
            "onboarding_field": field,
            "event_timestamp": current_time.isoformat(),
            "event_status": event_status,
            "event_duration_minutes": duration,
            "is_revisit": is_revisit,
            "previous_onboarding_field": (
                field_sequence[index - 1]
                if index > 0
                else ""
            ),
            "next_onboarding_field": next_onboarding_field,
            "validation_status": validation_status,
            "failure_reason": failure_reason,
            "onboarding_progress": progress,
            "onboarding_risk_score": risk_score,
            "onboarding_risk_level": risk_level,
            "real_time_guidance": guidance,
            "session_status": session_status
        }

        events.append(event)

        current_time += timedelta(
            minutes=duration
        )

    return events


# ============================================================
# GENERATE MANY ONBOARDING SESSIONS
# ============================================================

def generate_dataset(
    number_of_sessions=1000,
    industry="banking"
):

    all_events = []

    for _ in range(number_of_sessions):

        session = generate_session(
            industry=industry
        )

        all_events.extend(session)

    return all_events


# ============================================================
# SAVE DATASET
# ============================================================

def save_to_csv(events, filename):

    if not events:
        return

    fieldnames = list(events[0].keys())

    with open(
        filename,
        "w",
        newline="",
        encoding="utf-8"
    ) as file:

        writer = csv.DictWriter(
            file,
            fieldnames=fieldnames
        )

        writer.writeheader()
        writer.writerows(events)


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":

    # --------------------------------------------------------
    # Generate Banking Synthetic Onboarding Sessions
    # --------------------------------------------------------

    banking_events = generate_dataset(
        number_of_sessions=1000,
        industry="banking"
    )

    save_to_csv(
        banking_events,
        "banking_synthetic_sessions.csv"
    )

    # --------------------------------------------------------
    # Generate Telecom Synthetic Onboarding Sessions
    # --------------------------------------------------------

    telecom_events = generate_dataset(
        number_of_sessions=1000,
        industry="telecom"
    )

    save_to_csv(
        telecom_events,
        "telecom_synthetic_sessions.csv"
    )

    print(
        "Synthetic Onboarding Session generation completed."
    )

    print(
        f"Banking Onboarding Events: "
        f"{len(banking_events)}"
    )

    print(
        f"Telecom Onboarding Events: "
        f"{len(telecom_events)}"
    )