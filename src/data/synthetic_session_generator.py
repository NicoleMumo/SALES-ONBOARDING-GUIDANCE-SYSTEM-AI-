import csv
import random
import uuid
from datetime import datetime, timedelta


# ============================================================
# INDIVIDUAL ONBOARDING FIELDS (replaces broad workflow stages)
#
# Each field is tagged with the broad "onboarding_stage" it
# belongs to (kept because it's genuinely evidenced — e.g. the
# telecom source data only records KYC/installation/activation
# at that granularity) and an optional "condition" that decides
# whether the field applies to a given session's context
# (customer_type for banking, service_type for telecom).
#
# See prior analysis message for the evidence behind each field.
# ============================================================

BANKING_FIELDS = [
    {"field": "full_name",                          "stage": "customer_information"},
    {"field": "email_address",                       "stage": "customer_information"},
    {"field": "mobile_phone_number",                 "stage": "customer_information"},
    {"field": "date_of_birth",                        "stage": "customer_information"},
    {"field": "home_address",                         "stage": "customer_information"},
    # geolocation removed: GeolocationInsertion exists in the event log,
    # but capturing device/location during onboarding is a fraud/device-risk
    # signal in these flows, and the project explicitly excludes
    # concept:fraud as out of scope. Flagging rather than keeping it.
    {"field": "identification_document_type",         "stage": "identity_verification"},
    {"field": "identification_document_number",       "stage": "identity_verification"},
    {"field": "selfie_liveness_check",                "stage": "identity_verification"},
    {"field": "face_match_verification",              "stage": "identity_verification"},
    {"field": "profession",                           "stage": "financial_profile"},
    {"field": "income",                               "stage": "financial_profile"},
    {"field": "assets",                               "stage": "financial_profile", "probability": 0.5},
    {"field": "us_person_status",                     "stage": "regulatory_declarations"},
    {"field": "politically_exposed_person_status",    "stage": "regulatory_declarations"},
    {"field": "terms_and_conditions_acceptance",      "stage": "account_setup"},
    {"field": "commercial_address",                   "stage": "account_setup",
     "condition": lambda ctx: ctx["customer_type"] == "business"},
    {"field": "beneficial_owner_information",         "stage": "account_setup",
     "condition": lambda ctx: ctx["customer_type"] == "business"},
    {"field": "business_relationship_purpose",        "stage": "account_setup",
     "condition": lambda ctx: ctx["customer_type"] == "business"},
    {"field": "business_relationship_nature",         "stage": "account_setup",
     "condition": lambda ctx: ctx["customer_type"] == "business"},
    {"field": "ownership_and_control_information",    "stage": "account_setup",
     "condition": lambda ctx: ctx["customer_type"] == "business"},
]

BANKING_STAGE_ORDER = [
    "customer_information",
    "identity_verification",
    "financial_profile",
    "regulatory_declarations",
    "account_setup",
]

TELECOM_FIELDS = [
    {"field": "full_name",                    "stage": "kyc"},
    {"field": "email_address",                 "stage": "kyc"},
    {"field": "contact_phone_number",          "stage": "kyc"},
    {"field": "date_of_birth",                  "stage": "kyc"},
    {"field": "gender",                         "stage": "kyc"},
    {"field": "physical_address",               "stage": "kyc"},
    {"field": "identification_document",        "stage": "kyc"},
    {"field": "subscriber_type",                "stage": "kyc"},
    {"field": "service_type",                   "stage": "service_selection"},
    {"field": "contract_type",                  "stage": "service_selection"},
    {"field": "payment_method",                 "stage": "service_selection"},
    {"field": "paperless_billing_preference",   "stage": "service_selection"},
    {"field": "phone_service_selection",        "stage": "service_selection",
     "condition": lambda ctx: ctx["service_type"] in ("mobile", "landline")},
    {"field": "internet_service_selection",     "stage": "service_selection",
     "condition": lambda ctx: ctx["service_type"] in ("broadband", "tv")},
    {"field": "streaming_addons_selection",     "stage": "service_selection",
     "condition": lambda ctx: ctx["service_type"] in ("tv", "broadband")},
    # installation_confirmation / activation_confirmation removed:
    # these were invented pseudo-fields standing in for the
    # installation/activation stages themselves (the timeline source
    # only records those as stage-level status+duration+failure_reason,
    # never an individual field being entered). subscriber_number is
    # the one genuinely evidenced activation-time field (see step_failure_reason
    # text referencing IMEI/ICCID validation).
    {"field": "subscriber_number",              "stage": "activation"},
]

# "installation" is intentionally absent: per current source evidence it
# does not decompose into an individual field distinct from the stage
# itself, so it contributes no onboarding_field rows. It can still be
# tracked as a session-level scenario/timing concept elsewhere if needed.
TELECOM_STAGE_ORDER = [
    "kyc",
    "service_selection",
    "activation",
]

FIELD_DEFINITIONS = {
    "banking": BANKING_FIELDS,
    "telecom": TELECOM_FIELDS,
}

STAGE_ORDER = {
    "banking": BANKING_STAGE_ORDER,
    "telecom": TELECOM_STAGE_ORDER,
}


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
# SESSION CONTEXT (drives which conditional fields apply)
# ============================================================

def generate_session_context(industry):

    if industry == "banking":
        customer_type = random.choices(
            ["individual", "business"],
            weights=[80, 20],
            k=1
        )[0]
        return {"customer_type": customer_type}

    if industry == "telecom":
        service_type = random.choices(
            ["mobile", "broadband", "tv", "iot", "landline"],
            weights=[35, 25, 20, 10, 10],
            k=1
        )[0]
        return {"service_type": service_type}

    raise ValueError("Industry must be 'banking' or 'telecom'")


def select_applicable_fields(industry, context):
    """
    Build this session's target field list: every field whose
    condition (if any) passes, and whose probability (if any)
    is met, ordered by the industry's stage order with the
    fields within each stage shuffled for realistic variation.
    """

    definitions = FIELD_DEFINITIONS[industry]
    stage_order = STAGE_ORDER[industry]

    selected = []

    for definition in definitions:

        condition = definition.get("condition")
        if condition is not None and not condition(context):
            continue

        probability = definition.get("probability", 1.0)
        if random.random() > probability:
            continue

        selected.append(definition["field"])

    # Group by stage, preserving each field's definition order
    # within its stage, then shuffle within each stage only.
    by_stage = {stage: [] for stage in stage_order}
    field_to_stage = {
        d["field"]: d["stage"] for d in definitions
    }

    for field in selected:
        by_stage[field_to_stage[field]].append(field)

    ordered_fields = []
    stage_of_field = {}

    for stage in stage_order:
        stage_fields = by_stage[stage]
        random.shuffle(stage_fields)
        for field in stage_fields:
            stage_of_field[field] = stage
        ordered_fields.extend(stage_fields)

    return ordered_fields, stage_of_field


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

    if industry not in ("banking", "telecom"):
        raise ValueError("Industry must be 'banking' or 'telecom'")

    if scenario is None:
        scenario = select_scenario()

    session_id = generate_session_id()
    customer_id = generate_customer_id()
    sales_agent_id = generate_sales_agent_id()

    context = generate_session_context(industry)
    target_fields, stage_of_field = select_applicable_fields(
        industry, context
    )

    start_time = datetime.now()

    # --------------------------------------------------------
    # Incomplete sessions stop before the final target field
    # --------------------------------------------------------

    if scenario == "incomplete_sessions" and len(target_fields) > 1:

        number_of_fields = random.randint(
            1,
            len(target_fields) - 1
        )

        fields = target_fields[:number_of_fields]

    else:
        fields = target_fields.copy()

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

    # Denominator for progress: the full target set for this
    # session's context, not the truncated "fields" list, so
    # incomplete sessions correctly show partial progress.
    target_field_count = max(len(target_fields), 1)

    for index, field in enumerate(field_sequence):

        is_revisit = field in field_sequence[:index]

        if is_revisit:
            revisit_count += 1

        duration = generate_event_duration(scenario)
        validation_status = generate_validation_status(scenario)
        failure_reason = generate_failure_reason(validation_status)

        event_status = "failed" if validation_status == "failed" else "completed"

        previous_field = field_sequence[index - 1] if index > 0 else ""
        next_field = (
            field_sequence[index + 1]
            if index + 1 < len(field_sequence)
            else ""
        )

        progress = round(
            (
                len(set(field_sequence[:index + 1]))
                / target_field_count
            ) * 100,
            2
        )

        risk_score = calculate_onboarding_risk(
            validation_status, duration, revisit_count, progress
        )
        risk_level = get_risk_level(risk_score)

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

        if agent_action in [
            "corrected_information",
            "requested_missing_information",
            "reviewed_information",
            "revisited_information"
        ]:
            agent_response_time = round(random.uniform(1, 8), 2)

        elif agent_action == "provided_guidance":
            agent_response_time = round(random.uniform(0.5, 5), 2)

        else:
            agent_response_time = round(random.uniform(0.2, 3), 2)

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

        if scenario == "incomplete_sessions":
            session_status = "in_progress"
        elif (
            index == len(field_sequence) - 1
            and event_status == "completed"
        ):
            session_status = "completed"
        else:
            session_status = "in_progress"

        event_name = "revisited" if is_revisit else "entered"

        event = {
            "session_id": session_id,
            "customer_id": customer_id,
            "sales_agent_id": sales_agent_id,
            "industry": industry,
            "onboarding_scenario": scenario,
            "onboarding_stage": stage_of_field[field],
            "onboarding_field": field,
            "event_name": event_name,
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

            "agent_action": agent_action,
            "agent_response_time_minutes": agent_response_time,
            "agent_guidance_given": agent_guidance_given,
            "agent_correction_count": agent_correction_count,
            "agent_escalation": agent_escalation,

            "real_time_guidance": guidance,
            "session_status": session_status
        }

        events.append(event)

        current_time += timedelta(minutes=duration)

    return events


# ============================================================
# GENERATE DATASET
# ============================================================

def generate_dataset(number_of_sessions=1000, industry="banking"):

    all_events = []

    for _ in range(number_of_sessions):
        session = generate_session(industry=industry)
        all_events.extend(session)

    return all_events


# ============================================================
# SAVE DATASET
# ============================================================

def save_to_csv(events, filename):

    if not events:
        return

    fieldnames = list(events[0].keys())

    with open(filename, "w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
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

    telecom_events = generate_dataset(
        number_of_sessions=1000,
        industry="telecom"
    )

    # Save industry-specific datasets (kept separate, per spec)
    save_to_csv(banking_events, "banking_synthetic_sessions.csv")
    save_to_csv(telecom_events, "telecom_synthetic_sessions.csv")

    # Combine both into one multi-industry dataset (3rd file).
    # "industry" and "onboarding_stage" columns preserve which
    # industry/stage each row's onboarding_field belongs to,
    # since banking and telecom field vocabularies don't overlap.
    multi_industry_events = banking_events + telecom_events

    save_to_csv(
        multi_industry_events,
        "multi_industry_onboarding_dataset.csv"
    )

    print("Synthetic Multi-Industry Dataset generation completed.")
    print(f"Banking Onboarding Events: {len(banking_events)}")
    print(f"Telecom Onboarding Events: {len(telecom_events)}")
    print(f"Total Multi-Industry Events: {len(multi_industry_events)}")