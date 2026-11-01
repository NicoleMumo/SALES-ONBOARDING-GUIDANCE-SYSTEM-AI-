import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "ml"))

import dataset  # noqa: E402
from consistency import Step  # noqa: E402
from risk_ceiling import EXTRA_FEATURE_NAMES, extra_features, hidden_traits  # noqa: E402


class ExtraFeatureTests(unittest.TestCase):
    def test_values_follow_the_published_names(self):
        steps = [
            Step(2.0, True, False, 10.0),
            Step(4.0, False, False, 10.0),
            Step(1.0, False, True, 10.0),
            Step(3.0, True, False, 20.0),
        ]
        values = dict(zip(EXTRA_FEATURE_NAMES, extra_features(steps)))
        self.assertEqual(len(values), len(EXTRA_FEATURE_NAMES))
        self.assertEqual(values["last_failed"], 0.0)
        self.assertEqual(values["failures_last3"], 2.0)
        self.assertEqual(values["revisits_last3"], 1.0)
        self.assertEqual(values["max_consecutive_failures"], 2.0)
        self.assertEqual(values["steps_since_failure"], 1.0)
        self.assertAlmostEqual(values["mean_duration_last3"], 8.0 / 3)
        self.assertEqual(values["remaining_percent"], 80.0)

    def test_no_steps_is_rejected(self):
        with self.assertRaises(ValueError):
            extra_features([])


class HiddenTraitTests(unittest.TestCase):
    def test_recording_traits_leaves_every_event_unchanged(self):
        plain = dataset.generate(150, 5)
        traits, recorded = hidden_traits(5, 150)
        self.assertEqual(plain, recorded)
        self.assertEqual(set(traits), {event["session_id"] for event in plain})


if __name__ == "__main__":
    unittest.main()
