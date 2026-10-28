import csv
import importlib.util
import re
import tempfile
import unittest
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load module from {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


GENERATOR = load_module(
    "onboarding_data_generator", ROOT / "data" / "generate_onboarding_data.py"
)
CHECKER = load_module("check_dataset", ROOT / "data" / "check_dataset.py")
PLAYBOOK_SOURCE = (
    ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "salesonboarding"
    / "OnboardingPlaybook.kt"
)


def app_banking_fields():
    """Banking field order for (individual, business), read from the Kotlin playbook."""
    source = PLAYBOOK_SOURCE.read_text(encoding="utf-8")
    individual = re.search(r"val bankingIndividualFields\s*=\s*listOf\((.*?)\)", source, re.S)
    business = re.search(
        r"val bankingBusinessFields\s*=\s*listOf\((.*?)\)\s*\+\s*"
        r"bankingIndividualFields\.drop\(1\)\s*\+\s*listOf\((.*?)\)",
        source,
        re.S,
    )
    if individual is None or business is None:
        raise AssertionError("Banking field lists not found in OnboardingPlaybook.kt")
    ids = lambda text: re.findall(r'"([a-z_]+)"', text)
    individual_ids = ids(individual.group(1))
    business_ids = ids(business.group(1)) + individual_ids[1:] + ids(business.group(2))
    return individual_ids, business_ids


def generator_banking_fields(context):
    return [
        field
        for field, _stage, condition in GENERATOR.BANKING_FIELDS
        if condition is None or condition(context)
    ]


class OnboardingDataGeneratorTests(unittest.TestCase):
    def test_same_seed_produces_identical_events(self):
        first = GENERATOR.generate(80, seed=42)
        second = GENERATOR.generate(80, seed=42)

        self.assertEqual(first, second)

    def test_business_name_is_only_generated_for_business_banking(self):
        events = GENERATOR.generate(80, seed=42)
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
        events = GENERATOR.generate(120, seed=7)
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

    def test_banking_form_matches_the_app_playbook(self):
        individual, business = app_banking_fields()

        self.assertEqual(generator_banking_fields("individual"), individual)
        self.assertEqual(generator_banking_fields("business"), business)

    def test_no_agent_appears_in_more_than_one_split(self):
        events = GENERATOR.generate(200, seed=7)

        self.assertEqual(CHECKER.agents_in_more_than_one_split(events), [])

    def test_removed_identity_steps_are_not_generated(self):
        removed = {"selfie_liveness_check", "face_match_verification"}
        events = GENERATOR.generate(200, seed=11)

        self.assertFalse(removed & set(GENERATOR.F))
        self.assertFalse(removed & {field for field, _, _ in GENERATOR.BANKING_FIELDS})
        self.assertFalse(removed & {event["onboarding_field"] for event in events})

    def test_generator_rejects_nonpositive_session_count(self):
        with self.assertRaisesRegex(ValueError, "greater than zero"):
            GENERATOR.generate(0, seed=1)

    def test_csv_round_trip_keeps_v3_schema(self):
        events = GENERATOR.generate(30, seed=19)
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "data" / "dataset.csv"
            GENERATOR.save(events, output_path)

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

        results = CHECKER.next_field_baselines(rows)

        self.assertEqual(results["B1 (context, field)"], 0.0)
        self.assertEqual(results["B2 (+ previous field, validation)"], 0.0)
        self.assertEqual(results["B3 (+ agent id)"], 0.0)


if __name__ == "__main__":
    unittest.main()
