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
# ONBOARDING SCENARIOS
# ============================================================

SCENARIOS = [
    "normal_completion",
    "validation_errors",
    "revisited_fields",
    "long_completion_times",
    "incomplete_sessions"
]

SCENARIO_WEIGHTS = {
    "normal_completion": 0.50,
    "validation_errors": 0.15,
    "revisited_fields": 0.15,
    "long_completion_times": 0.10,
    "incomplete_sessions": 0.10
}


# ============================================================
# IDENTIFIERS
# ============================================================

def generate_customer_id():
    return f"CUST{random.randint(10000, 99999)}"


def generate_sales_agent_id():
    return f"AGENT{random.randint(100, 999)}"


def generate_session_id():
    return f"SESSION-{uuid.uuid4().hex[:8].upper()}"


# ============================================================
# SCENARIO SELECTION
# ============================================================

def select_scenario():
    return random.choices(
        list(SCENARIO_WEIGHTS.keys()),
        weights=list(SCENARIO_WEIGHTS.values()),
        k=1
    )[0]


# ============================================================
# EVENT DURATION
# ============================================================

def generate_event_duration(scenario):

    if scenario == "long_completion_times":
        return round(random.uniform(15, 60), 2)

    if scenario == "validation_errors":
        return round(random.uniform(5, 20), 2)

    if scenario == "revisited_fields":
        return round(random.uniform(4, 18), 2)

    return round(random.uniform(1, 12), 2)


# ============================================================
# VALIDATION
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
# AGENT BEHAVIOUR
# ============================================================

def generate_agent_behaviour(
    scenario,
    validation_status,
    is_revisit,
    progress,
    event_duration
):
    """
    Generate agent behaviour based on the onboarding situation.

    Agent behaviour is intentionally scenario-dependent so that
    the synthetic dataset contains meaningful relationships
    between onboarding events and sales-agent actions.
    """

    # --------------------------------------------------------
    # Validation errors
    # --------------------------------------------------------

    if validation_status == "failed":

        action = random.choices(
            [
                "corrected_information",
                "requested_missing_information",
                "reviewed_information"
            ],
            weights=[50, 35, 15],
            k=1
        )[0]

        return (
            action,
            True,
            random.randint(1, 2),
            random.choices(
                ["none", "required"],
                weights=[85, 15],
                k=1
            )[0]
        )

    # --------------------------------------------------------
    # Revisited field
    # --------------------------------------------------------

    if is_revisit:

        action = random.choices(
            [
                "revisited_information",
                "reviewed_information",
                "provided_guidance"
            ],
            weights=[50, 35, 15],
            k=1
        )[0]

        return (
            action,
            True,
            random.choice([0, 1]),
            "none"
        )

    # --------------------------------------------------------
    # Incomplete session
    # --------------------------------------------------------

    if scenario == "incomplete_sessions":

        action = random.choices(
            [
                "provided_guidance",
                "requested_missing_information",
                "reviewed_information"
            ],
            weights=[45, 40, 15],
            k=1
        )[0]

        return (
            action,
            True,
            random.choice([0, 1]),
            random.choices(
                ["none", "required"],
                weights=[90, 10],
                k=1
            )[0]
        )

    # --------------------------------------------------------
    # Long completion time
    # --------------------------------------------------------

    if scenario == "long_completion_times":

        action = random.choices(
            [
                "reviewed_information",
                "provided_guidance",
                "entered_information",
                "completed_step"
            ],
            weights=[40, 35, 15, 10],
            k=1
        )[0]

        return (
            action,
            True,
            random.choice([0, 1]),
            "none"
        )

    # --------------------------------------------------------
    # Normal completion
    # --------------------------------------------------------

    action = random.choices(
        [
            "entered_information",
            "completed_step",
            "provided_guidance",
            "reviewed_information"
        ],
        weights=[40, 35, 15, 10],
        k=1
    )[0]

    guidance_given = action == "provided_guidance"

    return (
        action,
        guidance_given,
        0,
        "none"
    )


# ============================================================
# RISK CALCULATION
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
# GENERATE ONE SESSION
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
    # Incomplete sessions stop before final step
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
    # Create field sequence with realistic revisits
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
    # Generate events
    # --------------------------------------------------------

    events = []

    current_time = start_time
    revisit_count = 0

    for index, field in enumerate(field_sequence):

        is_revisit = field in field_sequence[:index]

        if is_revisit:
            revisit_count += 1

        duration = generate_event_duration(
            scenario
        )

        validation_status = generate_validation_status(
            scenario
        )

        failure_reason = generate_failure_reason(
            validation_status
        )

        # ----------------------------------------------------
        # Event status
        # ----------------------------------------------------

        if validation_status == "failed":
            event_status = "failed"
        else:
            event_status = "completed"

        # ----------------------------------------------------
        # Previous / next field
        # ----------------------------------------------------

        if index > 0:
            previous_field = field_sequence[index - 1]
        else:
            previous_field = ""

        if index + 1 < len(field_sequence):
            next_field = field_sequence[index + 1]
        else:
            next_field = ""

        # ----------------------------------------------------
        # Progress
        # ----------------------------------------------------

        progress = round(
            (
                len(
                    set(
                        field_sequence[:index + 1]
                    )
                )
                / len(workflow)
            ) * 100,
            2
        )

        # ----------------------------------------------------
        # Risk
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
        # AGENT BEHAVIOUR
        # ----------------------------------------------------

        (
            agent_action,
            agent_guidance_given,
            agent_correction_count,
            agent_escalation
        ) = generate_agent_behaviour(
            scenario=scenario,
            validation_status=validation_status,
            is_revisit=is_revisit,
            progress=progress,
            event_duration=duration
        )

        # ----------------------------------------------------
        # Agent response time
        # ----------------------------------------------------

        if agent_action in [
            "corrected_information",
            "requested_missing_information",
            "reviewed_information",
            "revisited_information"
        ]:

            agent_response_time = round(
                random.uniform(1, 8),
                2
            )

        elif agent_action == "provided_guidance":

            agent_response_time = round(
                random.uniform(0.5, 5),
                2
            )

        else:

            agent_response_time = round(
                random.uniform(0.2, 3),
                2
            )

        # ----------------------------------------------------
        # Guidance message
        # ----------------------------------------------------

        if agent_guidance_given:

            if next_field:

                guidance = (
                    f"Review the current information "
                    f"and proceed to {next_field}."
                )

            else:

                guidance = (
                    "Review the completed information "
                    "and complete Customer Onboarding."
                )

        else:

            guidance = ""

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
        # Create event
        # ----------------------------------------------------

        event = {
            "session_id": session_id,
            "customer_id": customer_id,
            "sales_agent_id": sales_agent_id,
            "industry": industry,
            "onboarding_scenario": scenario,
            "onboarding_field": field,
            "event_timestamp": current_time.isoformat(),
            "event_status": event_status,
            "event_duration_minutes": duration,
            "is_revisit": is_revisit,
            "previous_onboarding_field": previous_field,
            "next_onboarding_field": next_field,
            "validation_status": validation_status,
            "failure_reason": failure_reason,
            "onboarding_progress": progress,
            "onboarding_risk_score": risk_score,
            "onboarding_risk_level": risk_level,

            # ------------------------------------------------
            # Sales-agent behaviour
            # ------------------------------------------------

            "agent_action": agent_action,
            "agent_response_time_minutes": agent_response_time,
            "agent_guidance_given": agent_guidance_given,
            "agent_correction_count": agent_correction_count,
            "agent_escalation": agent_escalation,

            "real_time_guidance": guidance,
            "session_status": session_status
        }

        events.append(event)

        current_time += timedelta(
            minutes=duration
        )

    return events


# ============================================================
# GENERATE DATASET
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

    banking_events = generate_dataset(
        number_of_sessions=1000,
        industry="banking"
    )

    save_to_csv(
        banking_events,
        "banking_synthetic_sessions.csv"
    )

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