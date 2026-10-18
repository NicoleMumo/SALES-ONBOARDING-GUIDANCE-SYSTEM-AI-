import unittest

import pandas as pd

from src.evaluate_completion_risk import score_sessions
from src.evaluate_next_field import canonical_prediction


class EvaluationBaselineTests(unittest.TestCase):
    def test_canonical_workflows_follow_selected_industry_and_context(self):
        self.assertEqual(
            "email_address",
            canonical_prediction(["full_name"], "banking", "individual"),
        )
        self.assertEqual(
            "commercial_address",
            canonical_prediction(
                ["full_name", "email_address", "mobile_phone_number", "date_of_birth",
                 "home_address", "identification_document_type", "identification_document_number",
                 "selfie_liveness_check", "face_match_verification", "profession", "income",
                 "assets", "us_person_status", "politically_exposed_person_status",
                 "terms_and_conditions_acceptance"],
                "banking",
                "business",
            ),
        )
        self.assertEqual(
            "internet_service_selection",
            canonical_prediction(
                ["full_name", "email_address", "contact_phone_number", "date_of_birth",
                 "gender", "physical_address", "identification_document", "subscriber_type",
                 "service_type", "contract_type", "payment_method", "paperless_billing_preference"],
                "telecom",
                "broadband",
            ),
        )

    def test_risk_duration_uses_each_event_not_session_sum(self):
        rows = pd.DataFrame([
            {"session_id": "s1", "event_timestamp": "2026-01-01T00:00:00",
             "onboarding_scenario": "normal_completion", "event_duration_minutes": 8,
             "validation_status": "passed", "is_revisit": False, "onboarding_progress": 50},
            {"session_id": "s1", "event_timestamp": "2026-01-01T00:01:00",
             "onboarding_scenario": "normal_completion", "event_duration_minutes": 8,
             "validation_status": "passed", "is_revisit": False, "onboarding_progress": 100},
            {"session_id": "s2", "event_timestamp": "2026-01-01T00:02:00",
             "onboarding_scenario": "normal_completion", "event_duration_minutes": 16,
             "validation_status": "passed", "is_revisit": False, "onboarding_progress": 100},
        ])

        scored = score_sessions(rows).loc["s1"]

        self.assertEqual(0, scored["long_events"])
        self.assertAlmostEqual(0.05, scored["score"])
        long_event = score_sessions(rows).loc["s2"]
        self.assertEqual(1, long_event["long_events"])
        self.assertAlmostEqual(0.20, long_event["score"])


if __name__ == "__main__":
    unittest.main()
