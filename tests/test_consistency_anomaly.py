import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src" / "consistency"))

from features import (  # noqa: E402
    SESSION_FEATURE_NAMES as FEATURE_NAMES,
    out_of_order_steps,
    session_features,
)
from playbooks import standard_order  # noqa: E402

PLAYBOOK_SOURCE = (
    ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "salesonboarding"
    / "OnboardingPlaybook.kt"
)


class OutOfOrderTests(unittest.TestCase):
    def test_the_standard_order_itself_has_nothing_out_of_order(self):
        expected = ["a", "b", "c", "d"]

        self.assertEqual(0, out_of_order_steps(expected, expected))

    def test_one_early_step_counts_once_not_once_per_step_after_it(self):
        """`d` jumped the queue; `b` and `c` are not at fault for following it."""
        self.assertEqual(1, out_of_order_steps(["a", "d", "b", "c"], ["a", "b", "c", "d"]))

    def test_one_late_step_also_counts_once(self):
        self.assertEqual(1, out_of_order_steps(["b", "c", "d", "a"], ["a", "b", "c", "d"]))

    def test_two_misplaced_steps_count_twice(self):
        self.assertEqual(2, out_of_order_steps(["c", "d", "a", "b"], ["a", "b", "c", "d"]))

    def test_repeating_a_field_is_not_counted_as_out_of_order(self):
        self.assertEqual(0, out_of_order_steps(["a", "b", "a", "c"], ["a", "b", "c"]))

    def test_fields_outside_the_standard_order_are_ignored(self):
        self.assertEqual(0, out_of_order_steps(["a", "custom_field", "b"], ["a", "b"]))

    def test_a_reversed_session_counts_many(self):
        self.assertEqual(3, out_of_order_steps(["d", "c", "b", "a"], ["a", "b", "c", "d"]))


class SessionFeatureTests(unittest.TestCase):
    def event(self, field, duration="2.0", status="passed", revisit="False"):
        return {
            "onboarding_field": field,
            "event_duration_minutes": duration,
            "validation_status": status,
            "is_revisit": revisit,
        }

    def test_vector_length_matches_the_published_names(self):
        events = [self.event("full_name")]

        self.assertEqual(
            len(FEATURE_NAMES), len(session_features(events, "banking", "individual"))
        )

    def test_counts_retries_revisits_and_pace(self):
        events = [
            self.event("full_name", duration="4.0"),
            self.event("email_address", duration="2.0", status="failed", revisit="True"),
        ]

        values = dict(zip(FEATURE_NAMES, session_features(events, "banking", "individual")))

        self.assertEqual(2.0, values["steps"])
        self.assertEqual(1.0, values["retries"])
        self.assertEqual(1.0, values["revisits"])
        self.assertEqual(3.0, values["minutes_per_step"])
        self.assertEqual(0.0, values["out_of_order_steps"])

    def test_extra_steps_counts_only_beyond_the_playbook_length(self):
        short = [self.event("full_name")]
        long_session = [self.event(f"step_{index}") for index in range(40)]

        self.assertEqual(
            0.0, dict(zip(FEATURE_NAMES, session_features(short, "banking", "individual")))["extra_steps"]
        )
        self.assertGreater(
            dict(zip(FEATURE_NAMES, session_features(long_session, "banking", "individual")))["extra_steps"],
            0.0,
        )


class PlaybookMatchesTheAppTests(unittest.TestCase):
    """This copy of the app's field order must not drift from the Kotlin source."""

    def app_banking_fields(self):
        source = PLAYBOOK_SOURCE.read_text(encoding="utf-8")
        individual = re.search(
            r"val bankingIndividualFields\s*=\s*listOf\((.*?)\)", source, re.S
        )
        business = re.search(
            r"val bankingBusinessFields\s*=\s*listOf\((.*?)\)\s*\+\s*"
            r"bankingIndividualFields\.drop\(1\)\s*\+\s*listOf\((.*?)\)",
            source,
            re.S,
        )
        self.assertIsNotNone(individual, "banking individual fields not found")
        self.assertIsNotNone(business, "banking business fields not found")
        ids = lambda text: re.findall(r'"([a-z_]+)"', text)
        individual_ids = ids(individual.group(1))
        return individual_ids, ids(business.group(1)) + individual_ids[1:] + ids(business.group(2))

    def test_banking_orders_match_the_kotlin_playbooks(self):
        individual, business = self.app_banking_fields()

        self.assertEqual(individual, standard_order("banking", "individual"))
        self.assertEqual(business, standard_order("banking", "business"))

    def test_every_telecom_context_has_an_order(self):
        for context in ("mobile", "broadband", "tv", "iot", "landline"):
            self.assertIn("subscriber_number", standard_order("telecom", context))

    def test_an_unknown_form_is_rejected_rather_than_guessed(self):
        with self.assertRaises(ValueError):
            standard_order("insurance", "individual")
        with self.assertRaises(ValueError):
            standard_order("banking", "charity")


if __name__ == "__main__":
    unittest.main()
