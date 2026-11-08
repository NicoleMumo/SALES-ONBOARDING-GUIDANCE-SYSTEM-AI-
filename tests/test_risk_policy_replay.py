import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "ml"))

from risk_policy_replay import HIGH, LOW, MEDIUM, Pacer  # noqa: E402


def shown(*levels):
    pacer = Pacer()
    return [pacer.next(level) for level in levels]


class PacerMatchesTheAppTests(unittest.TestCase):
    """The same cases as RiskWarningPacerTest in the Android tests: the replay must pace like the app."""

    def test_the_first_few_answers_never_show_a_warning(self):
        self.assertEqual([LOW] * 4, shown(HIGH, HIGH, HIGH, HIGH))

    def test_red_comes_only_after_amber_and_a_sustained_high_score(self):
        self.assertEqual([LOW, LOW, LOW, LOW, MEDIUM, MEDIUM, HIGH],
                         shown(LOW, LOW, LOW, LOW, HIGH, HIGH, HIGH))

    def test_a_single_high_score_shows_only_amber(self):
        self.assertEqual([LOW, LOW, LOW, LOW, MEDIUM, LOW], shown(LOW, LOW, LOW, LOW, HIGH, LOW))

    def test_the_warning_drops_as_soon_as_the_score_drops(self):
        self.assertEqual(MEDIUM, shown(LOW, LOW, LOW, LOW, HIGH, HIGH, HIGH, MEDIUM)[-1])
        self.assertEqual(LOW, shown(LOW, LOW, LOW, LOW, HIGH, HIGH, HIGH, LOW)[-1])


if __name__ == "__main__":
    unittest.main()
