import csv
import json
import tempfile
import unittest
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
NOTEBOOK_PATH = ROOT / "notebooks" / "onboarding_dataset_generator.ipynb"
GENERATOR_CELL_IDS = (
    "generator-imports-constants",
    "generator-core-functions",
    "generator-session",
    "generator-dataset-functions",
)


def load_notebook_generator():
    notebook = json.loads(NOTEBOOK_PATH.read_text(encoding="utf-8"))
    cells = {
        cell["id"]: cell
        for cell in notebook["cells"]
        if cell["cell_type"] == "code"
    }
    namespace = {}
    for cell_id in GENERATOR_CELL_IDS:
        source = "".join(cells[cell_id]["source"])
        exec(compile(source, f"{NOTEBOOK_PATH}::{cell_id}", "exec"), namespace)
    return namespace, cells


class DatasetGeneratorNotebookTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.generator, cls.cells = load_notebook_generator()

    def test_notebook_has_standalone_generator_and_no_python_module(self):
        self.assertTrue(NOTEBOOK_PATH.is_file())
        self.assertFalse((ROOT / "src" / "onboarding_generator_v2.py").exists())
        self.assertIn("generate_dataset", self.generator)
        self.assertIn("save_to_csv", self.generator)

    def test_same_seed_produces_identical_events(self):
        generate_dataset = self.generator["generate_dataset"]
        first = generate_dataset(number_of_sessions=80, industry="both", seed=42)
        second = generate_dataset(number_of_sessions=80, industry="both", seed=42)

        self.assertEqual(first, second)

    def test_generated_splits_keep_sessions_and_agents_together(self):
        events = self.generator["generate_dataset"](
            number_of_sessions=120,
            industry="both",
            seed=7,
        )
        session_splits = defaultdict(set)
        agent_splits = defaultdict(set)

        for event in events:
            session_splits[event["session_id"]].add(event["split"])
            agent_splits[event["sales_agent_id"]].add(event["agent_split"])

        self.assertEqual(len(session_splits), 120)
        self.assertTrue(all(len(splits) == 1 for splits in session_splits.values()))
        self.assertTrue(all(len(splits) == 1 for splits in agent_splits.values()))
        self.assertEqual(
            {event["industry"] for event in events},
            {"banking", "telecom"},
        )
        self.assertEqual(
            {event["split"] for event in events},
            {"train", "validation", "test"},
        )
        self.assertEqual(
            {event["agent_split"] for event in events},
            {"train", "validation", "test"},
        )

    def test_saved_csv_is_reproducible_and_includes_both_splits(self):
        generate_dataset = self.generator["generate_dataset"]
        save_to_csv = self.generator["save_to_csv"]
        events = generate_dataset(number_of_sessions=20, industry="both", seed=19)
        with tempfile.TemporaryDirectory() as directory:
            first_path = Path(directory) / "first" / "dataset.csv"
            second_path = Path(directory) / "second" / "dataset.csv"

            save_to_csv(events, first_path)
            save_to_csv(generate_dataset(20, "both", 19), second_path)

            self.assertEqual(first_path.read_bytes(), second_path.read_bytes())
            with first_path.open(newline="", encoding="utf-8") as dataset_file:
                rows = list(csv.DictReader(dataset_file))

        self.assertIn("split", rows[0])
        self.assertIn("agent_split", rows[0])

    def test_invalid_generation_settings_are_rejected(self):
        generate_dataset = self.generator["generate_dataset"]
        with self.assertRaisesRegex(ValueError, "greater than zero"):
            generate_dataset(number_of_sessions=0)
        with self.assertRaisesRegex(ValueError, "between zero and one"):
            generate_dataset(number_of_sessions=10, split_ratios={})
        with self.assertRaisesRegex(ValueError, "sum to 1"):
            generate_dataset(number_of_sessions=10, split_ratios={"train": 0.8})


if __name__ == "__main__":
    unittest.main()
