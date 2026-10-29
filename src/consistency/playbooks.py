"""The app's standard field order, copied from the Kotlin playbooks.

`tests/test_onboarding_data_generator.py` reads the Kotlin source and fails if
the generator drifts from it; `tests/test_consistency_anomaly.py` checks this
copy the same way. Change `OnboardingPlaybook.kt` and these together.
"""

BANKING_INDIVIDUAL = [
    "full_name", "email_address", "mobile_phone_number", "date_of_birth",
    "home_address", "identification_document", "identification_document_type",
    "identification_document_number", "profession", "income", "assets",
    "us_person_status", "politically_exposed_person_status",
    "terms_and_conditions_acceptance",
]
BANKING_BUSINESS = [
    "full_name", "business_name", *BANKING_INDIVIDUAL[1:],
    "commercial_address", "beneficial_owner_information",
    "business_relationship_purpose", "business_relationship_nature",
    "ownership_and_control_information",
]
TELECOM_BASE = [
    "full_name", "email_address", "contact_phone_number", "date_of_birth",
    "gender", "physical_address", "identification_document", "subscriber_type",
    "service_type", "contract_type", "payment_method",
    "paperless_billing_preference",
]


def standard_order(industry, context):
    """The order the app guides an agent through, for this form."""
    if industry == "banking":
        if context == "individual":
            return BANKING_INDIVIDUAL
        if context == "business":
            return BANKING_BUSINESS
        raise ValueError(f"No banking playbook for context {context!r}")
    if industry == "telecom":
        fields = list(TELECOM_BASE)
        if context in {"mobile", "landline"}:
            fields.append("phone_service_selection")
        elif context in {"broadband", "tv"}:
            fields.extend(["internet_service_selection", "streaming_addons_selection"])
        elif context != "iot":
            raise ValueError(f"No telecom playbook for context {context!r}")
        return fields + ["subscriber_number"]
    raise ValueError(f"No playbook for industry {industry!r}")
