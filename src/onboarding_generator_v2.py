"""
Synthetic onboarding-session generator, v2.

Same schema, same 33-field vocabulary, same two industries as the original
generator. What changed, and why:

1. Field order within a stage is now FIXED (a canonical script order), not
   random.shuffle(). This mirrors how a real guided onboarding flow works --
   the agent follows a consistent order -- and removes the single biggest
   source of unpredictable "next field" outcomes in the original data,
   which was pure uniform-random shuffling with no correlate anywhere else
   in the row.

2. Validation failures now trigger a RETRY of the same field as the next
   step (a bounded number of times), instead of validation_status being
   decorative and disconnected from what happens next. This is realistic
   (you don't move on from a field that failed validation) and it's
   directly visible in the input sequence itself as a repeated token, so
   a sequence model can learn it without needing any extra side channel.

3. "Revisited fields" now goes back to the IMMEDIATELY PRECEDING field
   (a realistic "let me double check that" pattern), not
   random.choice() over the entire history. This is also directly visible
   as a short repeat pattern in the sequence.

4. Session context (customer_type for banking, service_type for telecom)
   is recorded as its own explicit column, exposed as a first-class
   feature -- exactly the kind of thing a real onboarding system would
   know from the start of the session (selected product/account type),
   not something from the future. This resolves what would otherwise be
   irreducible ambiguity at the first context-conditional field.

Everything else (agent behaviour, timings, risk scoring, the
`real_time_guidance` text) is kept for realism but is still NOT meant to
be fed to the next-field model as an input feature where it would leak
the label (`real_time_guidance` still literally names the next field for
display purposes, exactly like the original data -- it must be excluded
from model inputs, same as before).
"""

import csv
import random
import uuid
from datetime import datetime, timedelta


BANKING_FIELDS = [
    {"field": "full_name",                          "stage": "customer_information"},
    {"field": "email_address",                       "stage": "customer_information"},
    {"field": "mobile_phone_number",                 "stage": "customer_information"},
    {"field": "date_of_birth",                        "stage": "customer_information"},
    {"field": "home_address",                         "stage": "customer_information"},
    {"field": "identification_document_type",         "stage": "identity_verification"},
    {"field": "identification_document_number",       "stage": "identity_verification"},
    {"field": "selfie_liveness_check",                "stage": "identity_verification"},
    {"field": "face_match_verification",              "stage": "identity_verification"},
    {"field": "profession",                           "stage": "financial_profile"},
    {"field": "income",                               "stage": "financial_profile"},
    {"field": "assets",                               "stage": "financial_profile"},
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
    {"field": "subscriber_number",              "stage": "activation"},
]

TELECOM_STAGE_ORDER = [
    "kyc",
    "service_selection",
    "activation",
]

FIELD_DEFINITIONS = {"banking": BANKING_FIELDS, "telecom": TELECOM_FIELDS}
STAGE_ORDER = {"banking": BANKING_STAGE_ORDER, "telecom": TELECOM_STAGE_ORDER}

SCENARIOS = ["normal_completion", "validation_errors", "revisited_fields",
             "long_completion_times", "incomplete_sessions"]
SCENARIO_WEIGHTS = {"normal_completion": 0.50, "validation_errors": 0.15,
                     "revisited_fields": 0.15, "long_completion_times": 0.10,
                     "incomplete_sessions": 0.10}

MAX_VALIDATION_RETRIES = 2          # a field can fail at most this many times before it passes
VALIDATION_FAILURE_PROBABILITY = 0.18

# Deliberately un-resolvable noise: a low-rate, uniformly-random step back to
# ANY earlier field (not just the previous one), with nothing in the row
# that predicts it. This is intentional -- real agents occasionally jump
# back for reasons no feature captures, and a model that's supposed to
# generalize (not memorize) should be validated against data that still has
# some genuine noise in it, the same way the original real dataset did.
UNRESOLVABLE_REVISIT_PROBABILITY = 0.04


def generate_customer_id():
    return f"CUST{random.randint(10000, 99999)}"


def generate_sales_agent_id():
    return f"AGENT{random.randint(100, 999)}"


def generate_session_id():
    return f"SESSION-{uuid.uuid4().hex[:8].upper()}"


def generate_session_context(industry):
    if industry == "banking":
        customer_type = random.choices(["individual", "business"], weights=[80, 20], k=1)[0]
        return {"customer_type": customer_type}
    if industry == "telecom":
        service_type = random.choices(["mobile", "broadband", "tv", "iot", "landline"],
                                       weights=[35, 25, 20, 10, 10], k=1)[0]
        return {"service_type": service_type}
    raise ValueError("Industry must be 'banking' or 'telecom'")


def context_label(industry, context):
    """A single string identifying the session's context branch -- this is
    the explicit feature exposed to the model, known from the start of the
    session (not derived from anything in the future)."""
    if industry == "banking":
        return context["customer_type"]
    return context["service_type"]


def select_applicable_fields(industry, context):
    """
    This session's target field list: every field whose condition (if any)
    passes and whose probability (if any) is met, ordered by the industry's
    FIXED stage order and FIXED within-stage order (definition order) --
    no shuffling. This is the main change from v1: a consistent script
    order is what makes "next field" learnable.
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

    by_stage = {stage: [] for stage in stage_order}
    field_to_stage = {d["field"]: d["stage"] for d in definitions}
    for field in selected:
        by_stage[field_to_stage[field]].append(field)

    ordered_fields = []
    stage_of_field = {}
    for stage in stage_order:
        # NOTE: no random.shuffle() here -- definition order is preserved.
        for field in by_stage[stage]:
            stage_of_field[field] = stage
        ordered_fields.extend(by_stage[stage])

    return ordered_fields, stage_of_field


def select_scenario():
    return random.choices(list(SCENARIO_WEIGHTS.keys()), weights=list(SCENARIO_WEIGHTS.values()), k=1)[0]


def generate_event_duration(scenario):
    if scenario == "long_completion_times":
        return round(random.uniform(15, 60), 2)
    if scenario == "validation_errors":
        return round(random.uniform(5, 20), 2)
    if scenario == "revisited_fields":
        return round(random.uniform(4, 18), 2)
    return round(random.uniform(1, 12), 2)


def generate_failure_reason():
    return random.choice(["Required information missing", "Invalid information entered",
                           "Information could not be verified"])


def generate_agent_behaviour(validation_status, is_revisit, scenario):
    if validation_status == "failed":
        action = random.choices(
            ["corrected_information", "requested_missing_information", "reviewed_information"],
            weights=[50, 35, 15], k=1)[0]
        return (action, True, random.randint(1, 2),
                random.choices(["none", "required"], weights=[85, 15], k=1)[0])

    if is_revisit:
        action = random.choices(
            ["revisited_information", "reviewed_information", "provided_guidance"],
            weights=[50, 35, 15], k=1)[0]
        return (action, True, random.choice([0, 1]), "none")

    if scenario == "incomplete_sessions":
        action = random.choices(
            ["provided_guidance", "requested_missing_information", "reviewed_information"],
            weights=[45, 40, 15], k=1)[0]
        return (action, True, random.choice([0, 1]),
                random.choices(["none", "required"], weights=[90, 10], k=1)[0])

    if scenario == "long_completion_times":
        action = random.choices(
            ["reviewed_information", "provided_guidance", "entered_information", "completed_step"],
            weights=[40, 35, 15, 10], k=1)[0]
        return (action, True, random.choice([0, 1]), "none")

    action = random.choices(
        ["entered_information", "completed_step", "provided_guidance", "reviewed_information"],
        weights=[40, 35, 15, 10], k=1)[0]
    guidance_given = action == "provided_guidance"
    return (action, guidance_given, 0, "none")


def calculate_onboarding_risk(validation_status, event_duration, revisit_count, session_progress):
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
    context_value = context_label(industry, context)
    target_fields, stage_of_field = select_applicable_fields(industry, context)

    start_time = datetime.now()

    if scenario == "incomplete_sessions" and len(target_fields) > 1:
        number_of_fields = random.randint(1, len(target_fields) - 1)
        base_fields = target_fields[:number_of_fields]
    else:
        base_fields = target_fields.copy()

    # ------------------------------------------------------------------
    # Build the actual step-by-step sequence, resolving validation
    # retries and revisits with explicit, rule-based logic rather than
    # free-floating randomness.
    # ------------------------------------------------------------------
    field_sequence = []       # every field actually shown, in order
    validation_status_seq = []  # validation_status recorded at that step
    is_revisit_seq = []

    target_field_count = max(len(target_fields), 1)
    revisit_count = 0

    for position, field in enumerate(base_fields):
        # -------- low-rate, deliberately UNRESOLVABLE revisit --------
        # Only in "revisited_fields" scenario sessions, and only ~4% of
        # steps within them -- nothing in the row predicts this, by design.
        # This keeps a small, honest amount of irreducible noise in the
        # data instead of a purely deterministic (and therefore unrealistic)
        # generator.
        if (scenario == "revisited_fields" and len(field_sequence) > 0
                and random.random() < UNRESOLVABLE_REVISIT_PROBABILITY):
            revisited_field = random.choice(field_sequence)
            field_sequence.append(revisited_field)
            validation_status_seq.append("passed")
            is_revisit_seq.append(True)
            revisit_count += 1

        # -------- enter the actual field, with bounded validation retries --------
        attempt = 0
        while True:
            will_fail = (
                scenario == "validation_errors"
                and attempt < MAX_VALIDATION_RETRIES
                and random.random() < VALIDATION_FAILURE_PROBABILITY
            )
            field_sequence.append(field)
            validation_status_seq.append("failed" if will_fail else "passed")
            is_revisit_seq.append(attempt > 0)
            if will_fail:
                attempt += 1
                continue
            break

    # ------------------------------------------------------------------
    # Emit events
    # ------------------------------------------------------------------
    events = []
    current_time = start_time

    for index, field in enumerate(field_sequence):
        validation_status = validation_status_seq[index]
        is_revisit = is_revisit_seq[index]
        failure_reason = generate_failure_reason() if validation_status == "failed" else ""
        event_status = "failed" if validation_status == "failed" else "completed"

        duration = generate_event_duration(scenario)

        previous_field = field_sequence[index - 1] if index > 0 else ""
        next_field = field_sequence[index + 1] if index + 1 < len(field_sequence) else ""

        progress = round((len(set(field_sequence[:index + 1])) / target_field_count) * 100, 2)
        risk_score = calculate_onboarding_risk(validation_status, duration, revisit_count, progress)
        risk_level = get_risk_level(risk_score)

        (agent_action, agent_guidance_given, agent_correction_count,
         agent_escalation) = generate_agent_behaviour(validation_status, is_revisit, scenario)

        if agent_action in ["corrected_information", "requested_missing_information",
                             "reviewed_information", "revisited_information"]:
            agent_response_time = round(random.uniform(1, 8), 2)
        elif agent_action == "provided_guidance":
            agent_response_time = round(random.uniform(0.5, 5), 2)
        else:
            agent_response_time = round(random.uniform(0.2, 3), 2)

        if agent_guidance_given:
            guidance = (f"Review the current information and proceed to {next_field}."
                        if next_field else
                        "Review the completed information and complete Customer Onboarding.")
        else:
            guidance = ""

        if scenario == "incomplete_sessions":
            session_status = "in_progress"
        elif index == len(field_sequence) - 1 and event_status == "completed":
            session_status = "completed"
        else:
            session_status = "in_progress"

        event_name = "revisited" if is_revisit else "entered"

        event = {
            "session_id": session_id, "customer_id": customer_id, "sales_agent_id": sales_agent_id,
            "industry": industry, "context_value": context_value,
            "onboarding_scenario": scenario,
            "onboarding_stage": stage_of_field[field], "onboarding_field": field,
            "event_name": event_name, "event_timestamp": current_time.isoformat(),
            "event_status": event_status, "event_duration_minutes": duration,
            "is_revisit": is_revisit, "previous_onboarding_field": previous_field,
            "next_onboarding_field": next_field, "validation_status": validation_status,
            "failure_reason": failure_reason, "onboarding_progress": progress,
            "onboarding_risk_score": risk_score, "onboarding_risk_level": risk_level,
            "agent_action": agent_action, "agent_response_time_minutes": agent_response_time,
            "agent_guidance_given": agent_guidance_given, "agent_correction_count": agent_correction_count,
            "agent_escalation": agent_escalation, "real_time_guidance": guidance,
            "session_status": session_status,
        }
        events.append(event)
        current_time += timedelta(minutes=duration)

    return events


def generate_dataset(number_of_sessions=1000, industry="banking"):
    all_events = []
    for _ in range(number_of_sessions):
        all_events.extend(generate_session(industry=industry))
    return all_events


def save_to_csv(events, filename):
    if not events:
        return
    fieldnames = list(events[0].keys())
    with open(filename, "w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(events)