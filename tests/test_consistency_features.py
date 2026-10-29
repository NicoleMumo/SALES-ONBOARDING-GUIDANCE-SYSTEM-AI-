import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src" / "consistency"))

from features import (  # noqa: E402
    FEATURE_NAMES,
    Step,
    app_rule_score,
    features,
    rows_from_sessions,
    session_steps,
)


def step(duration=1.0, passed=True, revisit=False, progress=50.0):
    return Step(duration, passed, revisit, progress)


class FeatureTests(unittest.TestCase):
    def test_vector_length_matches_the_published_names(self):
        self.assertEqual(len(features([step()], True, False)), len(FEATURE_NAMES))

    def test_counts_and_rates(self):
        steps = [
            step(duration=2.0, passed=True, progress=10.0),
            step(duration=20.0, passed=False, revisit=True, progress=10.0),
            step(duration=3.0, passed=False, progress=20.0),
        ]

        values = dict(zip(FEATURE_NAMES, features(steps, is_banking=True, is_business=False)))

        self.assertEqual(3.0, values["events"])
        self.assertEqual(2.0, values["failures"])
        self.assertEqual(2.0, values["consecutive_failures"])
        self.assertEqual(1.0, values["revisits"])
        self.assertEqual(25.0, values["elapsed_minutes"])
        self.assertEqual(20.0, values["longest_event_minutes"])
        self.assertEqual(1.0, values["long_events"])
        self.assertEqual(20.0, values["progress_percent"])
        self.assertAlmostEqual(2 / 3, values["failure_rate"])
        self.assertAlmostEqual(1 / 3, values["revisit_rate"])
        self.assertAlmostEqual(25 / 3, values["minutes_per_event"])
        self.assertAlmostEqual(20 / 25, values["progress_per_minute"])
        self.assertEqual(1.0, values["is_banking"])
        self.assertEqual(0.0, values["is_business"])

    def test_consecutive_failures_counts_only_the_latest_run(self):
        steps = [step(passed=False), step(passed=False), step(passed=True)]

        values = dict(zip(FEATURE_NAMES, features(steps, False, False)))

        self.assertEqual(2.0, values["failures"])
        self.assertEqual(0.0, values["consecutive_failures"])

    def test_zero_elapsed_time_does_not_divide_by_zero(self):
        values = dict(zip(FEATURE_NAMES, features([step(duration=0.0)], False, False)))

        self.assertEqual(0.0, values["progress_per_minute"])

    def test_no_steps_is_rejected(self):
        with self.assertRaises(ValueError):
            features([], False, False)
        with self.assertRaises(ValueError):
            app_rule_score([])

    def test_no_feature_name_describes_the_session_outcome(self):
        banned = ("status", "abandon", "scenario", "outcome", "next_", "guidance")
        for name in FEATURE_NAMES:
            self.assertFalse(
                any(word in name for word in banned), f"{name} looks like an outcome"
            )


class AppRuleTests(unittest.TestCase):
    """The rule the app ships today, reproduced for a fair comparison."""

    def test_clean_short_session_scores_the_base_only(self):
        self.assertAlmostEqual(0.05, app_rule_score([step(progress=80.0)]))

    def test_each_condition_adds_its_published_weight(self):
        self.assertAlmostEqual(0.30, app_rule_score([step(passed=False, progress=80.0)]))
        self.assertAlmostEqual(0.20, app_rule_score([step(duration=16.0, progress=80.0)]))
        self.assertAlmostEqual(0.15, app_rule_score([step(revisit=True, progress=80.0)]))
        self.assertAlmostEqual(0.15, app_rule_score([step(progress=49.0)]))

    def test_long_event_threshold_is_exclusive(self):
        self.assertAlmostEqual(0.05, app_rule_score([step(duration=15.0, progress=80.0)]))
        self.assertAlmostEqual(0.20, app_rule_score([step(duration=15.01, progress=80.0)]))

    def test_score_never_exceeds_one(self):
        steps = [step(duration=99.0, passed=False, revisit=True, progress=0.0) for _ in range(20)]

        self.assertEqual(1.0, app_rule_score(steps))


class RowBuildingTests(unittest.TestCase):
    def event(self, duration="1.0", status="passed", revisit="False", progress="10.0",
              session_status="in_progress"):
        return {
            "event_duration_minutes": duration,
            "validation_status": status,
            "is_revisit": revisit,
            "onboarding_progress": progress,
            "session_status": session_status,
        }

    def test_steps_are_read_from_csv_text(self):
        steps = session_steps([self.event(duration="2.5", status="failed", revisit="True")])

        self.assertEqual(2.5, steps[0].duration_minutes)
        self.assertFalse(steps[0].validation_passed)
        self.assertTrue(steps[0].is_revisit)

    def test_one_row_per_step_except_the_last(self):
        events = [self.event(), self.event(), self.event(session_status="abandoned")]

        rows = rows_from_sessions([("s1", "a1", "banking", "individual", events)])

        self.assertEqual(2, len(rows))
        self.assertEqual([1, 2], [row["position"] for row in rows])
        self.assertTrue(all(row["abandoned"] for row in rows))

    def test_a_one_step_session_produces_no_rows(self):
        events = [self.event(session_status="abandoned")]

        self.assertEqual([], rows_from_sessions([("s1", "a1", "banking", "individual", events)]))

    def test_rows_never_see_a_later_step(self):
        """Row k must depend only on steps 1..k, so a later change cannot alter it."""
        early = [self.event(duration="1.0"), self.event(duration="1.0")]
        late = early + [self.event(duration="99.0", status="failed")]

        first = rows_from_sessions([("s1", "a1", "telecom", "mobile", early)])
        second = rows_from_sessions([("s1", "a1", "telecom", "mobile", late)])

        self.assertEqual(first[0]["features"], second[0]["features"])
        self.assertEqual(first[0]["app_rule"], second[0]["app_rule"])


if __name__ == "__main__":
    unittest.main()
