import csv
import sys
import tempfile
import unittest
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "ml"))

import dataset  # noqa: E402
from consistency import standard_order  # noqa: E402

FORMS = {
    "banking": ("individual", "business"),
    "telecom": ("mobile", "broadband", "tv", "iot", "landline"),
}


def generator_fields(industry, context):
    fields, _stages = dataset.DEFS[industry]
    return [
        field
        for field, _stage, condition in fields
        if condition is None or condition(context)
    ]


class OnboardingDataGeneratorTests(unittest.TestCase):
    def test_same_seed_produces_identical_events(self):
        first = dataset.generate(80, seed=42)
        second = dataset.generate(80, seed=42)

        self.assertEqual(first, second)

    def test_business_name_is_only_generated_for_business_banking(self):
        events = dataset.generate(80, seed=42)
        business_name_events = [
            event for event in events if event["onboarding_field"] == "business_name"
        ]

        self.assertTrue(business_name_events)
        self.assertTrue(
            all(
                event["industry"] == "banking"
                and event["context_value"] == "business"
                for event in business_name_events
            )
        )

    def test_events_and_session_splits_are_consistent(self):
        events = dataset.generate(120, seed=7)
        session_events = defaultdict(list)
        session_splits = defaultdict(set)
        for event in events:
            session_events[event["session_id"]].append(event)
            session_splits[event["session_id"]].add(event["split"])

        self.assertTrue(all(len(splits) == 1 for splits in session_splits.values()))
        self.assertEqual(
            {event["split"] for event in events},
            {"train", "validation", "test"},
        )
        for rows in session_events.values():
            rows.sort(key=lambda row: row["event_timestamp"])
            for index, event in enumerate(rows):
                previous = rows[index - 1]["onboarding_field"] if index else ""
                following = (
                    rows[index + 1]["onboarding_field"]
                    if index + 1 < len(rows)
                    else ""
                )
                self.assertEqual(event["previous_onboarding_field"], previous)
                self.assertEqual(event["next_onboarding_field"], following)

    def test_every_form_matches_the_app_order(self):
        """Compared with ml/consistency.py's copy, which test_consistency.py checks
        against the Kotlin playbooks, so the generator cannot drift from the app."""
        for industry, contexts in FORMS.items():
            for context in contexts:
                with self.subTest(industry=industry, context=context):
                    self.assertEqual(
                        standard_order(industry, context), generator_fields(industry, context)
                    )

    def test_no_agent_appears_in_more_than_one_split(self):
        events = dataset.generate(200, seed=7)

        self.assertEqual(dataset.agents_in_more_than_one_split(events), [])

    def test_removed_identity_steps_are_not_generated(self):
        removed = {"selfie_liveness_check", "face_match_verification"}
        events = dataset.generate(200, seed=11)

        self.assertFalse(removed & set(dataset.F))
        self.assertFalse(removed & {field for field, _, _ in dataset.BANKING_FIELDS})
        self.assertFalse(removed & {event["onboarding_field"] for event in events})

    def test_generator_rejects_nonpositive_session_count(self):
        with self.assertRaisesRegex(ValueError, "greater than zero"):
            dataset.generate(0, seed=1)

    def test_csv_round_trip_keeps_v3_schema(self):
        events = dataset.generate(30, seed=19)
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "data" / "dataset.csv"
            dataset.save(events, output_path)

            with output_path.open(newline="", encoding="utf-8") as dataset_file:
                reader = csv.DictReader(dataset_file)
                rows = list(reader)

        self.assertEqual(len(reader.fieldnames), 28)
        self.assertEqual(len(rows), len(events))
        self.assertEqual(rows[0]["session_id"], events[0]["session_id"])

    def test_checker_falls_back_to_coarser_lookup_baselines(self):
        rows = [
            {
                "split": "train",
                "context_value": "mobile",
                "previous_onboarding_field": "",
                "onboarding_field": "name",
                "validation_status": "passed",
                "sales_agent_id": "a1",
                "next_onboarding_field": "email",
            },
            {
                "split": "train",
                "context_value": "mobile",
                "previous_onboarding_field": "name",
                "onboarding_field": "email",
                "validation_status": "passed",
                "sales_agent_id": "a1",
                "next_onboarding_field": "phone",
            },
            {
                "split": "test",
                "context_value": "mobile",
                "previous_onboarding_field": "",
                "onboarding_field": "new_field",
                "validation_status": "passed",
                "sales_agent_id": "a2",
                "next_onboarding_field": "phone",
            },
        ]

        results = dataset.next_field_baselines(rows)

        self.assertEqual(results["B1 (context, field)"], 0.0)
        self.assertEqual(results["B2 (+ previous field, validation)"], 0.0)
        self.assertEqual(results["B3 (+ agent id)"], 0.0)


if __name__ == "__main__":
    unittest.main()
