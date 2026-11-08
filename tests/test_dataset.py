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


class GeneratorVersionTests(unittest.TestCase):
    # Fingerprint of generate(150, 5) taken before v4 was added: v3 must not drift.
    V3_FINGERPRINT = "8cd83a4f7591892dcaebc4bca360fd4a323f801df0e42e887aabdd744993962c"

    def test_v3_is_the_default_and_reproduces_the_original_generator(self):
        import hashlib
        import json

        events = dataset.generate(150, 5)
        digest = hashlib.sha256(json.dumps(events, sort_keys=True, default=str).encode()).hexdigest()
        self.assertEqual(self.V3_FINGERPRINT, digest)

    def test_in_v4_clients_struggle_before_they_give_up(self):
        events = dataset.generate(1500, 11, version="v4")
        sessions = defaultdict(list)
        for event in events:
            sessions[event["session_id"]].append(event)
        failed = {"abandoned": [], "completed": []}
        for rows in sessions.values():
            rows.sort(key=lambda event: event["event_timestamp"])
            if len(rows) >= 5:
                outcome = rows[-1]["session_status"]
                failed[outcome] += [row["validation_status"] == "failed" for row in rows[-4:-1]]
        share = {k: sum(v) / len(v) for k, v in failed.items()}
        self.assertGreater(share["abandoned"], 2 * share["completed"])

    def test_an_unknown_version_is_rejected(self):
        with self.assertRaises(ValueError):
            dataset.generate(10, 1, version="v9")


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


class ExportedSessionsTests(unittest.TestCase):
    # The columns of the app's Training CSV (SessionCsvExport.TRAINING_COLUMNS).
    APP_COLUMNS = [
        "session_id", "sales_agent_id", "industry", "context_value", "onboarding_field",
        "event_name", "event_timestamp", "event_status", "event_duration_minutes",
        "agent_response_time_minutes", "is_revisit", "previous_onboarding_field",
        "next_onboarding_field", "validation_status", "failure_reason",
        "onboarding_progress", "session_status", "split",
    ]

    def write_export(self, path, agents=20, sessions_per_agent=3):
        with open(path, "w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=self.APP_COLUMNS)
            writer.writeheader()
            for a in range(agents):
                for s in range(sessions_per_agent):
                    for step, (field, status) in enumerate([("full_name", "in_progress"),
                                                            ("date_of_birth", "completed")]):
                        writer.writerow({
                            "session_id": f"S-{a}-{s}", "sales_agent_id": f"AGENT-{a:012X}",
                            "industry": "banking", "context_value": "individual",
                            "onboarding_field": field, "event_name": "entered",
                            "event_timestamp": f"2026-01-01T08:{step:02d}:00Z", "event_status": "completed",
                            "event_duration_minutes": "1.00", "agent_response_time_minutes": "0.00",
                            "is_revisit": "False", "previous_onboarding_field": "",
                            "next_onboarding_field": "", "validation_status": "passed",
                            "failure_reason": "", "onboarding_progress": "50.00",
                            "session_status": status, "split": "",
                        })

    def test_split_puts_each_sales_agent_in_one_split(self):
        with tempfile.TemporaryDirectory() as folder:
            source, out = Path(folder) / "export.csv", Path(folder) / "export_split.csv"
            self.write_export(source)

            counts = dataset.split_exported_sessions(source, out)
            rows = dataset.read_dataset(out)

        splits_of_agent = defaultdict(set)
        for row in rows:
            splits_of_agent[row["sales_agent_id"]].add(row["split"])
        self.assertTrue(all(len(s) == 1 for s in splits_of_agent.values()))
        self.assertEqual({"train", "validation", "test"}, {row["split"] for row in rows})
        self.assertEqual(60, sum(counts.values()))

    def test_an_export_feeds_the_risk_model_rows(self):
        from consistency import load_split_rows

        with tempfile.TemporaryDirectory() as folder:
            source, out = Path(folder) / "export.csv", Path(folder) / "export_split.csv"
            self.write_export(source)
            dataset.split_exported_sessions(source, out)
            by_split = load_split_rows(out)

        self.assertEqual(60, sum(len(rows) for rows in by_split.values()))


class FirebaseCopiesTests(unittest.TestCase):
    DAY = 24 * 60 * 60 * 1000

    def record(self, status, updated_at, agent="AGENT-0123456789AB"):
        return {
            "industry": "banking", "context": "individual", "agentCode": agent, "fieldCount": 4,
            "startedAt": 0, "status": status, "updatedAt": updated_at,
            "steps": {
                "-b": {"fieldId": "contact_phone_number", "passed": False, "t": 120_000},
                "-a": {"fieldId": "full_name", "passed": True, "t": 60_000},
                "-c": {"fieldId": "contact_phone_number", "passed": True, "t": 240_000},
            },
        }

    def test_copies_become_rows_like_the_apps_training_csv(self):
        export = {"trainingData": {
            "TDONE": self.record("completed", 240_000),
            "TOLD": self.record("in_progress", 240_000),
            "TNEW": self.record("in_progress", 2 * self.DAY),  # one day old: not abandoned yet
        }}

        rows = dataset.rows_from_firebase(export, now_ms=3 * self.DAY)

        self.assertEqual({"TDONE", "TOLD"}, {row["session_id"] for row in rows})
        done = [row for row in rows if row["session_id"] == "TDONE"]
        self.assertEqual(["full_name", "contact_phone_number", "contact_phone_number"],
                         [row["onboarding_field"] for row in done])
        self.assertEqual(["1.00", "1.00", "2.00"], [row["event_duration_minutes"] for row in done])
        self.assertEqual(["False", "False", "True"], [row["is_revisit"] for row in done])
        self.assertEqual(["25.00", "25.00", "50.00"], [row["onboarding_progress"] for row in done])
        self.assertEqual("completed", done[-1]["session_status"])
        self.assertEqual("abandoned", [row for row in rows if row["session_id"] == "TOLD"][-1]["session_status"])
        self.assertEqual(dataset.APP_TRAINING_COLUMNS, list(rows[0]))

    def test_an_export_of_training_data_alone_is_also_read(self):
        rows = dataset.rows_from_firebase({"TDONE": self.record("completed", 0)}, now_ms=0)
        self.assertEqual(3, len(rows))

    def test_conversion_writes_a_split_csv_the_risk_model_can_read(self):
        import json
        from consistency import load_split_rows

        export = {"trainingData": {
            f"T{agent}{n}": self.record("completed", 0, agent=f"AGENT-{agent:012X}")
            for agent in range(20) for n in range(3)
        }}
        with tempfile.TemporaryDirectory() as folder:
            source, out = Path(folder) / "export.json", Path(folder) / "real.csv"
            source.write_text(json.dumps(export), encoding="utf-8")
            counts = dataset.from_firebase(source, out, now_ms=0)
            by_split = load_split_rows(out)

        self.assertEqual(60, sum(counts.values()))
        self.assertEqual({"train", "validation", "test"}, set(by_split))


if __name__ == "__main__":
    unittest.main()
