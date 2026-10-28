"""
Realistic synthetic onboarding-session generator (v3) for SalesMesh.

Replacement for the older v2 simulator. It retains the 28-column event
schema and uses the same industries, contexts, and validation values. The
banking flow matches the app's banking playbooks: an ID photo step
(`identification_document`) followed by document type and number, and no
selfie, liveness or face-match steps, which the app does not have. The
vocabulary is the 32 fields of the app's standard playbooks.

What v3 adds is *latent structure*: the sequences are produced by simulated
people and systems, not by per-session scenario labels.

  Agents     skill, speed, learning curve (new hires make more mistakes),
             working pattern, and a workflow style:
               - script    follows the canonical order
               - id_first  scans the ID first (OCR pre-fills name/DOB)
               - flexible  reorders neighbouring fields more often
  Customers  digital literacy, document readiness, patience, device quality;
             some come back for a second session after abandoning.
  Systems    the identity-verification / activation service has outage
             windows, so "could not be verified" failures cluster in time
             across agents, the way real outages do.
  Causality  revisits have reasons: an unverifiable ID number is often a
             misspelt name or wrong date of birth; a blurry ID photo is more
             likely on a low-end phone; deferred fields ("I'll get you the
             email later") reappear at the end of the stage.
  Outcomes   abandonment is a hazard that rises with consecutive failures,
             elapsed time vs. patience, and missing documents -- it is NOT
             drawn up front.

Splits are by agent: all of an agent's sessions go to one of train,
validation or test (about 70/15/15 of sessions), so the test set only
contains agents the model never saw in training.

`onboarding_scenario` is derived *after* the session from what happened
(descriptive only). It summarises the whole session, so never feed it to a
model as an input. Same for `real_time_guidance` (names the next field) and
`next_onboarding_field` (the label).

Every number in CONFIG is an assumption. When real (anonymised) data
arrives, replace these with measured values and regenerate.

Usage:
    python data/generate_onboarding_data.py
    python data/generate_onboarding_data.py --sessions 12000 --seed 7
    python data/generate_onboarding_data.py --noise
"""

import argparse
import csv
import math
import random
import uuid
from collections import Counter
from datetime import datetime, timedelta
from pathlib import Path

# ---------------------------------------------------------------------------
# Configuration (assumptions -- calibrate against real data when available)
# ---------------------------------------------------------------------------
CONFIG = {
    "start_date": "2026-06-01",
    "weeks": 12,
    "industry_weights": {"banking": 0.52, "telecom": 0.48},
    "banking_context_weights": {"individual": 0.78, "business": 0.22},
    "telecom_context_weights": {"mobile": 0.36, "broadband": 0.26, "tv": 0.18,
                                "iot": 0.10, "landline": 0.10},
    "agents_initial": 110,
    "agents_hired_per_week": 4,          # new hires -> learning-curve shift in later weeks
    "agent_style_weights": {"script": 0.68, "id_first": 0.22, "flexible": 0.10},
    "learning_sessions": 35,             # error multiplier decays over ~this many sessions
    "new_agent_error_multiplier": 1.9,
    "outages_per_day": 0.35,             # verification-service outage windows (Poisson)
    "outage_minutes": (20, 150),
    "outage_fail_probability": 0.55,
    "interruption_probability": 0.035,   # customer takes a call, fetches a document...
    "interruption_mean_minutes": 6.0,
    "max_attempts": 3,                   # 3rd failure -> escalation
    "returning_customer_probability": 0.35,  # abandoned customers who come back later
    "base_abandon_hazard": 0.003,
    "timezone": "+03:00",                # East Africa Time
}

# Per-field behaviour.
#   med   median minutes on the field (customer + agent)
#   sd    log-normal spread
#   fail  base failure probability at first attempt
#   why   failure-reason weights: (missing, invalid, unverified)
#   ver   depends on an external verification service (outage-sensitive)
#   cx    complexity -> likelihood the agent has to explain the field
#   doc   needs a physical document the customer may not have brought
F = {
    # banking
    "full_name":                        dict(med=0.6, sd=0.45, fail=0.02, why=(1, 3, 0), cx=0.05),
    "business_name":                    dict(med=0.8, sd=0.50, fail=0.03, why=(1, 2, 0), cx=0.10),
    "email_address":                    dict(med=0.8, sd=0.55, fail=0.06, why=(2, 5, 1), cx=0.10),
    "mobile_phone_number":              dict(med=0.5, sd=0.45, fail=0.04, why=(0, 3, 2), cx=0.05),
    "date_of_birth":                    dict(med=0.4, sd=0.40, fail=0.02, why=(0, 4, 0), cx=0.03),
    "home_address":                     dict(med=1.3, sd=0.55, fail=0.04, why=(3, 1, 0), cx=0.20),
    "identification_document_type":     dict(med=0.3, sd=0.40, fail=0.01, why=(1, 1, 0), cx=0.10, doc=True),
    "identification_document_number":   dict(med=1.0, sd=0.50, fail=0.07, why=(0, 2, 5), cx=0.10, ver=True, doc=True),
    "profession":                       dict(med=0.5, sd=0.45, fail=0.01, why=(1, 1, 0), cx=0.10),
    "income":                           dict(med=1.1, sd=0.55, fail=0.06, why=(4, 1, 0), cx=0.30),
    "assets":                           dict(med=1.5, sd=0.60, fail=0.08, why=(4, 1, 0), cx=0.35),
    "us_person_status":                 dict(med=0.3, sd=0.40, fail=0.00, why=(1, 0, 0), cx=0.45),
    "politically_exposed_person_status": dict(med=0.4, sd=0.40, fail=0.00, why=(1, 0, 0), cx=0.50),
    "terms_and_conditions_acceptance":  dict(med=1.4, sd=0.60, fail=0.00, why=(1, 0, 0), cx=0.25),
    "commercial_address":               dict(med=1.5, sd=0.55, fail=0.05, why=(3, 1, 0), cx=0.15),
    "beneficial_owner_information":     dict(med=4.0, sd=0.60, fail=0.16, why=(5, 1, 1), cx=0.60, doc=True),
    "business_relationship_purpose":    dict(med=1.0, sd=0.50, fail=0.02, why=(1, 1, 0), cx=0.30),
    "business_relationship_nature":     dict(med=1.0, sd=0.50, fail=0.02, why=(1, 1, 0), cx=0.30),
    "ownership_and_control_information": dict(med=3.5, sd=0.60, fail=0.13, why=(5, 1, 1), cx=0.55, doc=True),
    # telecom
    "contact_phone_number":             dict(med=0.5, sd=0.45, fail=0.04, why=(0, 3, 2), cx=0.05),
    "gender":                           dict(med=0.2, sd=0.35, fail=0.00, why=(1, 0, 0), cx=0.02),
    "physical_address":                 dict(med=1.2, sd=0.55, fail=0.04, why=(3, 1, 0), cx=0.20),
    # ID photo step, used by both industries
    "identification_document":          dict(med=1.5, sd=0.50, fail=0.08, why=(1, 2, 5), cx=0.15, ver=True, doc=True),
    "subscriber_type":                  dict(med=0.3, sd=0.40, fail=0.00, why=(1, 0, 0), cx=0.15),
    "service_type":                     dict(med=0.6, sd=0.50, fail=0.00, why=(1, 0, 0), cx=0.20),
    "contract_type":                    dict(med=0.8, sd=0.55, fail=0.01, why=(1, 1, 0), cx=0.35),
    "payment_method":                   dict(med=1.2, sd=0.55, fail=0.05, why=(1, 2, 3), cx=0.25, ver=True),
    "paperless_billing_preference":     dict(med=0.3, sd=0.40, fail=0.00, why=(1, 0, 0), cx=0.10),
    "phone_service_selection":          dict(med=1.0, sd=0.55, fail=0.01, why=(1, 1, 0), cx=0.25),
    "internet_service_selection":       dict(med=1.5, sd=0.55, fail=0.05, why=(1, 0, 3), cx=0.30, ver=True),
    "streaming_addons_selection":       dict(med=1.0, sd=0.55, fail=0.00, why=(1, 0, 0), cx=0.20),
    "subscriber_number":                dict(med=2.0, sd=0.55, fail=0.06, why=(0, 1, 4), cx=0.15, ver=True),
}
REASONS = ("Required information missing", "Invalid information entered",
           "Information could not be verified")

# Fields that can be postponed to the end of their stage ("I'll send it later").
DEFERRABLE = {"email_address", "assets", "income", "home_address", "physical_address",
              "commercial_address", "beneficial_owner_information",
              "ownership_and_control_information", "paperless_billing_preference"}

# Failure on key -> agent goes back to one of these fields first (with prob).
CAUSAL_BACKTRACK = {
    "identification_document_number": (["full_name", "date_of_birth", "identification_document_type"], 0.35),
    "identification_document": (["full_name", "date_of_birth"], 0.35),
    "subscriber_number": (["payment_method", "service_type"], 0.25),
    "internet_service_selection": (["physical_address"], 0.40),
}

BANKING_FIELDS = [
    ("full_name", "customer_information", None),
    ("business_name", "customer_information", lambda c: c == "business"),
    ("email_address", "customer_information", None),
    ("mobile_phone_number", "customer_information", None),
    ("date_of_birth", "customer_information", None),
    ("home_address", "customer_information", None),
    ("identification_document", "identity_verification", None),
    ("identification_document_type", "identity_verification", None),
    ("identification_document_number", "identity_verification", None),
    ("profession", "financial_profile", None),
    ("income", "financial_profile", None),
    ("assets", "financial_profile", None),
    ("us_person_status", "regulatory_declarations", None),
    ("politically_exposed_person_status", "regulatory_declarations", None),
    ("terms_and_conditions_acceptance", "account_setup", None),
    ("commercial_address", "account_setup", lambda c: c == "business"),
    ("beneficial_owner_information", "account_setup", lambda c: c == "business"),
    ("business_relationship_purpose", "account_setup", lambda c: c == "business"),
    ("business_relationship_nature", "account_setup", lambda c: c == "business"),
    ("ownership_and_control_information", "account_setup", lambda c: c == "business"),
]
BANKING_STAGES = ["customer_information", "identity_verification", "financial_profile",
                  "regulatory_declarations", "account_setup"]

TELECOM_FIELDS = [
    ("full_name", "kyc", None),
    ("email_address", "kyc", None),
    ("contact_phone_number", "kyc", None),
    ("date_of_birth", "kyc", None),
    ("gender", "kyc", None),
    ("physical_address", "kyc", None),
    ("identification_document", "kyc", None),
    ("subscriber_type", "kyc", None),
    ("service_type", "service_selection", None),
    ("contract_type", "service_selection", None),
    ("payment_method", "service_selection", None),
    ("paperless_billing_preference", "service_selection", None),
    ("phone_service_selection", "service_selection", lambda c: c in ("mobile", "landline")),
    ("internet_service_selection", "service_selection", lambda c: c in ("broadband", "tv")),
    ("streaming_addons_selection", "service_selection", lambda c: c in ("tv", "broadband")),
    ("subscriber_number", "activation", None),
]
TELECOM_STAGES = ["kyc", "service_selection", "activation"]

DEFS = {"banking": (BANKING_FIELDS, BANKING_STAGES), "telecom": (TELECOM_FIELDS, TELECOM_STAGES)}
ID_FIELDS = {"banking": ["identification_document", "identification_document_type",
                         "identification_document_number"],
             "telecom": ["identification_document"]}
OCR_PREFILLED = {"full_name", "date_of_birth"}


# ---------------------------------------------------------------------------
# Entities
# ---------------------------------------------------------------------------
def unique_id(rng, prefix, lo, hi, used):
    while True:
        candidate = f"{prefix}{rng.randint(lo, hi)}"
        if candidate not in used:
            used.add(candidate)
            return candidate


AGENT_IDS, CUSTOMER_IDS = set(), set()


class Agent:
    def __init__(self, rng, hired_day):
        self.id = unique_id(rng, "AGENT", 100, 999, AGENT_IDS)
        self.hired_day = hired_day
        self.skill = rng.betavariate(5, 2)                   # 0..1, higher = fewer errors
        self.speed = rng.lognormvariate(0, 0.22)             # >1 = slower
        self.style = weighted(rng, CONFIG["agent_style_weights"])
        self.swap_p = {"script": 0.02, "id_first": 0.03, "flexible": 0.14}[self.style]
        self.review_p = rng.betavariate(2, 8)                # habit of double-checking
        self.guidance = rng.betavariate(3, 4)                # habit of explaining
        # Mon-Fri; ~30% also cover Saturday; ~10% take a weekday off instead
        self.workdays = {0, 1, 2, 3, 4}
        if rng.random() < 0.3:
            self.workdays.add(5)
        if rng.random() < 0.1:
            self.workdays.discard(rng.randrange(5))
        self.sessions_done = 0

    def error_multiplier(self):
        learn = 1 + (CONFIG["new_agent_error_multiplier"] - 1) * math.exp(
            -self.sessions_done / CONFIG["learning_sessions"])
        return learn * (1.6 - self.skill)


class Customer:
    def __init__(self, rng, industry, context):
        self.id = unique_id(rng, "CUST", 10000, 99999, CUSTOMER_IDS)
        self.literacy = rng.betavariate(2.2, 1.8)
        ready = 0.72 if context == "business" else 0.90
        self.has_documents = rng.random() < ready
        self.patience = rng.lognormvariate(math.log(35 if industry == "banking" else 22), 0.45)
        self.low_end_device = rng.random() < 0.28            # blurrier ID photos


def weighted(rng, d):
    return rng.choices(list(d), weights=list(d.values()), k=1)[0]


def build_outages(rng, day_start):
    """Verification-service outage windows for one day, in EAT."""
    windows = []
    n = poisson(rng, CONFIG["outages_per_day"])
    for _ in range(n):
        start = day_start + timedelta(minutes=rng.uniform(8 * 60, 17 * 60))
        windows.append((start, start + timedelta(minutes=rng.uniform(*CONFIG["outage_minutes"]))))
    return windows


def poisson(rng, lam):
    k, p, L = 0, 1.0, math.exp(-lam)
    while True:
        p *= rng.random()
        if p <= L:
            return k
        k += 1


def sample_start_time(rng, day):
    """Business-hours arrival with mid-morning and afternoon peaks, lunch dip."""
    hour_weights = {8: 4, 9: 8, 10: 11, 11: 11, 12: 8, 13: 5, 14: 9, 15: 10, 16: 8, 17: 4}
    if day.weekday() == 5:
        hour_weights = {h: w for h, w in hour_weights.items() if h <= 12}
    hour = weighted(rng, hour_weights)
    return day + timedelta(hours=hour, minutes=rng.uniform(0, 60))


# ---------------------------------------------------------------------------
# Session simulation
# ---------------------------------------------------------------------------
def plan_order(rng, industry, context, agent):
    fields, stages = DEFS[industry]
    applicable = [(f, s) for f, s, cond in fields if cond is None or cond(context)]
    by_stage = {s: [f for f, st in applicable if st == s] for s in stages}
    stage_of = {f: s for f, s in applicable}

    if agent.style == "id_first":
        first = stages[0]
        ids = ID_FIELDS[industry]
        if industry == "banking":
            # identity stage moves to the front
            stages = ["identity_verification"] + [s for s in stages if s != "identity_verification"]
        else:
            by_stage[first] = [f for f in ids if f in by_stage[first]] + \
                              [f for f in by_stage[first] if f not in ids]

    order = []
    for s in stages:
        seq = by_stage[s][:]
        for i in range(len(seq) - 1):                        # local neighbour swaps
            if rng.random() < agent.swap_p:
                seq[i], seq[i + 1] = seq[i + 1], seq[i]
        order.append((s, seq))
    return order, stage_of, len(applicable)


def simulate_session(rng, industry, context, agent, customer, start, outages, returning):
    order, stage_of, total = plan_order(rng, industry, context, agent)
    err = agent.error_multiplier()
    t = start
    rows = []
    passed = set()
    consecutive_fail = 0
    abandoned = False
    escalated_any = False

    def in_outage(ts):
        return any(a <= ts <= b for a, b in outages)

    def duration_for(field, attempt, kind):
        spec = F[field]
        med = spec["med"] * agent.speed * (1.35 - 0.6 * customer.literacy)
        if agent.style == "id_first" and field in OCR_PREFILLED:
            med *= 0.35
        if returning and field in passed_before:
            med *= 0.6
        if attempt > 0:
            med *= 0.65
        if kind == "revisit":
            med *= 0.5
        d = rng.lognormvariate(math.log(max(med, 0.05)), spec["sd"])
        if spec.get("ver") and in_outage(t):
            d *= rng.uniform(2.0, 4.0)
        if rng.random() < CONFIG["interruption_probability"]:
            d += rng.expovariate(1 / CONFIG["interruption_mean_minutes"])
        return round(max(d, 0.05), 2)

    passed_before = set(F) if returning else set()

    def attempt_field(field, attempt):
        """Return (failed, reason)."""
        spec = F[field]
        if spec.get("doc") and not customer.has_documents and attempt == 0 and rng.random() < 0.6:
            return True, REASONS[0]
        if spec.get("ver") and in_outage(t) and rng.random() < CONFIG["outage_fail_probability"]:
            return True, REASONS[2]
        p = spec["fail"] * err * (0.5 ** attempt)
        if field == "identification_document" and customer.low_end_device:
            p *= 1.5
        if agent.style == "id_first" and field in OCR_PREFILLED:
            p *= 0.3
        if field in ("email_address", "home_address", "income", "assets"):
            p *= (1.5 - customer.literacy)
        if rng.random() < p:
            return True, rng.choices(REASONS, weights=spec["why"], k=1)[0]
        return False, ""

    def emit(field, kind, failed, reason, attempt):
        nonlocal t, escalated_any
        dur = duration_for(field, attempt, kind)
        spec = F[field]
        escalation = "none"
        if failed and attempt + 1 >= CONFIG["max_attempts"]:
            escalation = "required"
        if field == "politically_exposed_person_status" and rng.random() < 0.018:
            escalation = "required"
            dur = round(dur + rng.uniform(3, 10), 2)
        if escalation == "required":
            escalated_any = True

        if failed:
            action = {REASONS[0]: "requested_missing_information",
                      REASONS[1]: "corrected_information",
                      REASONS[2]: "reviewed_information"}[reason]
        elif kind == "revisit":
            action = "revisited_information"
        elif attempt > 0:
            action = "corrected_information"
        elif rng.random() < spec["cx"] * (1.2 - customer.literacy) * (0.5 + agent.guidance):
            action = "provided_guidance"
        elif kind == "stage_end":
            action = "completed_step"
        else:
            action = "entered_information"

        guidance = action == "provided_guidance" or (failed and rng.random() < 0.6 + 0.3 * agent.guidance)
        corrections = (2 if rng.random() < 0.2 else 1) if action == "corrected_information" else 0
        base_rt = {"requested_missing_information": 1.6, "reviewed_information": 1.2,
                   "corrected_information": 1.0, "revisited_information": 0.8,
                   "provided_guidance": 0.9}.get(action, 0.35)
        rt = round(rng.lognormvariate(math.log(base_rt * agent.speed), 0.5), 2)

        rows.append({
            "field": field, "kind": kind, "failed": failed, "reason": reason, "ts": t,
            "dur": dur, "action": action, "rt": rt, "guidance": guidance,
            "corrections": corrections, "escalation": escalation,
            "is_revisit": kind == "revisit" or attempt > 0,
        })
        t = t + timedelta(minutes=dur + rt)

    def abandon_now():
        elapsed = (t - start).total_seconds() / 60
        h = CONFIG["base_abandon_hazard"] + 0.03 * consecutive_fail ** 1.5
        if elapsed > customer.patience:
            h += 0.02 * (elapsed / customer.patience)
        return rng.random() < min(h, 0.9)

    for stage, seq in order:
        queue = list(seq)
        deferred = []
        already_deferred = set()
        while queue or deferred:
            if not queue:
                queue, deferred = deferred, []
            field = queue.pop(0)
            is_stage_end = not queue and not deferred

            # postpone a field the customer can't answer right now
            if field in DEFERRABLE and field not in already_deferred and queue \
                    and rng.random() < 0.05 + 0.08 * (not customer.has_documents):
                deferred.append(field)
                already_deferred.add(field)
                continue

            attempt = 0
            while True:
                failed, reason = attempt_field(field, attempt)
                emit(field, "stage_end" if is_stage_end and not failed else "enter",
                     failed, reason, attempt)
                if not failed:
                    passed.add(field)
                    consecutive_fail = 0
                    break
                consecutive_fail += 1
                if abandon_now() or (reason == REASONS[0] and F[field].get("doc")
                                     and rng.random() < 0.25):
                    abandoned = True
                    break
                if attempt + 1 >= CONFIG["max_attempts"]:
                    # escalated: supervisor override, field accepted next time
                    attempt += 1
                    emit(field, "enter", False, "", attempt)
                    passed.add(field)
                    consecutive_fail = 0
                    break
                # causal backtrack before retrying
                if field in CAUSAL_BACKTRACK and reason != REASONS[0]:
                    targets, p = CAUSAL_BACKTRACK[field]
                    targets = [x for x in targets if x in passed]
                    if targets and rng.random() < p:
                        emit(rng.choice(targets), "revisit", False, "", 0)
                attempt += 1
            if abandoned:
                break
            if abandon_now():
                abandoned = True
                break
        if abandoned:
            break
        # "let me just double-check that" at the end of a stage
        if rng.random() < agent.review_p * 0.6:
            candidates = [r["field"] for r in rows if stage_of[r["field"]] == stage]
            if candidates:
                emit(rng.choice(candidates), "revisit", False, "", 0)

    agent.sessions_done += 1
    return rows, stage_of, total, abandoned, escalated_any


def risk(validation_failed, duration, revisits, progress):
    """Same rule the app uses (kept for compatibility), on running values only."""
    r = 0.05 + 0.25 * validation_failed + 0.15 * (duration > 15) + 0.10 * revisits + 0.10 * (progress < 50)
    return min(round(r, 2), 1.0)


def risk_level(score):
    return "Low" if score < 0.30 else ("Medium" if score < 0.70 else "High")


def derive_scenario(rows, abandoned, total_minutes, long_threshold):
    if abandoned:
        return "incomplete_sessions"
    if any(r["failed"] for r in rows):
        return "validation_errors"
    if any(r["is_revisit"] for r in rows):
        return "revisited_fields"
    if total_minutes > long_threshold:
        return "long_completion_times"
    return "normal_completion"


def to_events(session_id, customer, agent, industry, context, rows, stage_of, total,
              abandoned, scenario):
    out = []
    seen_passed = set()
    revisits = 0
    tz = CONFIG["timezone"]
    for i, r in enumerate(rows):
        if r["is_revisit"]:
            revisits += 1
        if not r["failed"]:
            seen_passed.add(r["field"])
        progress = round(len(seen_passed) / total * 100, 2)
        score = risk(r["failed"], r["dur"], revisits, progress)
        nxt = rows[i + 1]["field"] if i + 1 < len(rows) else ""
        last = i == len(rows) - 1
        out.append({
            "session_id": session_id,
            "customer_id": customer.id,
            "sales_agent_id": agent.id,
            "industry": industry,
            "context_value": context,
            "onboarding_scenario": scenario,
            "onboarding_stage": stage_of[r["field"]],
            "onboarding_field": r["field"],
            "event_name": "revisited" if r["is_revisit"] else "entered",
            "event_timestamp": r["ts"].strftime("%Y-%m-%dT%H:%M:%S") + tz,
            "event_status": "failed" if r["failed"] else "completed",
            "event_duration_minutes": r["dur"],
            "is_revisit": r["is_revisit"],
            "previous_onboarding_field": rows[i - 1]["field"] if i else "",
            "next_onboarding_field": nxt,
            "validation_status": "failed" if r["failed"] else "passed",
            "failure_reason": r["reason"],
            "onboarding_progress": progress,
            "onboarding_risk_score": score,
            "onboarding_risk_level": risk_level(score),
            "agent_action": r["action"],
            "agent_response_time_minutes": r["rt"],
            "agent_guidance_given": r["guidance"],
            "agent_correction_count": r["corrections"],
            "agent_escalation": r["escalation"],
            "real_time_guidance": (f"Review the current information and proceed to {nxt}." if nxt else
                                   "Review the completed information and complete Customer Onboarding.")
            if r["guidance"] else "",
            "session_status": ("abandoned" if abandoned else "completed") if last else "in_progress",
            "split": "",
        })
    return out


# ---------------------------------------------------------------------------
# Dataset
# ---------------------------------------------------------------------------
def generate(n_sessions, seed):
    if n_sessions <= 0:
        raise ValueError("n_sessions must be greater than zero.")
    rng = random.Random(seed)
    AGENT_IDS.clear()
    CUSTOMER_IDS.clear()
    start_day = datetime.fromisoformat(CONFIG["start_date"])
    days = [start_day + timedelta(days=d) for d in range(CONFIG["weeks"] * 7)]

    agents = [Agent(rng, 0) for _ in range(CONFIG["agents_initial"])]
    hires = {d: CONFIG["agents_hired_per_week"] for d in range(7, len(days), 7)}

    # daily volume: weekdays full, Saturday half, Sunday closed; month-end rush
    def day_weight(d):
        w = {5: 0.45, 6: 0.0}.get(d.weekday(), 1.0)
        return w * (1.3 if d.day >= 25 else 1.0)
    weights = [day_weight(d) for d in days]
    per_day = [round(n_sessions * w / sum(weights)) for w in weights]

    sessions = []
    pending_returns = []      # (day_index, customer, industry, context)
    long_threshold = None
    raw = []

    for di, day in enumerate(days):
        for _ in range(hires.get(di, 0)):
            agents.append(Agent(rng, di))
        outages = build_outages(rng, day)
        on_shift = [a for a in agents if day.weekday() in a.workdays and a.hired_day <= di]
        if not on_shift or per_day[di] == 0:
            continue
        todays_returns = [p for p in pending_returns if p[0] == di]
        pending_returns = [p for p in pending_returns if p[0] != di]
        for k in range(per_day[di]):
            if todays_returns and k % 7 == 0:
                _, customer, industry, context = todays_returns.pop()
                returning = True
            else:
                industry = weighted(rng, CONFIG["industry_weights"])
                context = weighted(rng, CONFIG[f"{industry}_context_weights"])
                customer = Customer(rng, industry, context)
                returning = False
            if returning:
                customer.has_documents = True            # they came back with the documents
            agent = rng.choice(on_shift)
            start = sample_start_time(rng, day)
            rows, stage_of, total, abandoned, _ = simulate_session(
                rng, industry, context, agent, customer, start, outages, returning)
            if not rows:
                continue
            raw.append((customer, agent, industry, context, rows, stage_of, total, abandoned, day))
            if abandoned and not returning and rng.random() < CONFIG["returning_customer_probability"]:
                pending_returns.append((min(di + rng.randint(1, 10), len(days) - 1),
                                        customer, industry, context))

    totals = sorted(sum(r["dur"] + r["rt"] for r in s[4]) for s in raw)
    long_threshold = totals[int(0.9 * len(totals))]

    split_of_agent = assign_agent_splits(raw, seed)
    events = []
    for customer, agent, industry, context, rows, stage_of, total, abandoned, _day in raw:
        minutes = sum(r["dur"] + r["rt"] for r in rows)
        scenario = derive_scenario(rows, abandoned, minutes, long_threshold)
        sid = f"SESSION-{uuid.UUID(int=rng.getrandbits(128)).hex[:8].upper()}"
        ev = to_events(sid, customer, agent, industry, context, rows, stage_of, total,
                       abandoned, scenario)
        for e in ev:
            e["split"] = split_of_agent[agent.id]
        events.extend(ev)
    return events


def assign_agent_splits(raw, seed):
    """Put each agent wholly in train, validation or test, about 70/15/15 of sessions.

    Agents are shuffled with their own seeded RNG (so the session stream is not
    disturbed), then filled into the splits in that order by session count.
    """
    sessions_per_agent = Counter(agent.id for _, agent, *_ in raw)
    agent_ids = sorted(sessions_per_agent)
    random.Random(seed + 101).shuffle(agent_ids)
    total = sum(sessions_per_agent.values())
    split_of, seen = {}, 0
    for agent_id in agent_ids:
        share = seen / total
        split_of[agent_id] = "train" if share < 0.70 else ("validation" if share < 0.85 else "test")
        seen += sessions_per_agent[agent_id]
    return split_of


def add_noise(rng, events):
    """Real logs are messy: double-submitted events and a few missing reasons."""
    out = []
    for e in events:
        out.append(e)
        if rng.random() < 0.004:
            out.append(dict(e))                              # duplicate row
    for e in out:
        if e["validation_status"] == "failed" and rng.random() < 0.03:
            e["failure_reason"] = ""
    return out


def save(events, path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(events[0].keys()))
        w.writeheader()
        w.writerows(events)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sessions", type=int, default=8000)
    ap.add_argument("--seed", type=int, default=2026)
    ap.add_argument("--out", default="data/onboarding_dataset_v3.csv")
    ap.add_argument("--noise", action="store_true", help="also write <out>_raw.csv with realistic log noise")
    args = ap.parse_args()
    if args.sessions <= 0:
        ap.error("--sessions must be greater than zero.")

    events = generate(args.sessions, args.seed)
    save(events, args.out)
    print(f"wrote {args.out}: {len(events)} events, "
          f"{len({e['session_id'] for e in events})} sessions")
    if args.noise:
        output_path = Path(args.out)
        raw_path = output_path.with_name(f"{output_path.stem}_raw{output_path.suffix}")
        save(add_noise(random.Random(args.seed + 1), events), raw_path)
        print(f"wrote {raw_path}")


if __name__ == "__main__":
    main()
