import ast
import json
from pathlib import Path

import numpy as np
import pandas as pd


ENCODED_DIR = Path("data/encoded_sequences")
OUTPUT_DIR = Path("data/transformer_inputs")
VOCABULARY_PATH = Path("data/vocabulary/field_vocabulary.json")

TRAIN_INPUT = ENCODED_DIR / "train_sequences_encoded.csv"
VALIDATION_INPUT = ENCODED_DIR / "validation_sequences_encoded.csv"
TEST_INPUT = ENCODED_DIR / "test_sequences_encoded.csv"

PAD_ID = 0
FIELD_ID_OFFSET = 1


def parse_sequence(value):
	"""Convert a CSV list representation into integer field IDs."""

	if pd.isna(value):
		raise ValueError("Found a missing input sequence.")

	sequence = ast.literal_eval(value)

	if not isinstance(sequence, list):
		raise ValueError(f"Expected a list but received: {type(sequence)}")

	return [int(field_id) for field_id in sequence]


def load_dataset(path):
	"""Load an encoded sequence dataset."""

	df = pd.read_csv(path)
	required_columns = {"input_sequence_ids", "next_field_id"}
	missing_columns = required_columns - set(df.columns)

	if missing_columns:
		raise ValueError(
			f"{path} is missing required columns: {sorted(missing_columns)}"
		)

	sequences = [parse_sequence(sequence) for sequence in df["input_sequence_ids"]]
	targets = df["next_field_id"].astype(int).tolist()

	return sequences, targets


def get_max_sequence_length(*datasets):
	"""Find the longest input sequence across all datasets."""

	sequence_lengths = [
		len(sequence)
		for sequences, _ in datasets
		for sequence in sequences
	]

	if not sequence_lengths:
		raise ValueError("Cannot determine a maximum length from empty datasets.")

	return max(sequence_lengths)


def validate_vocabulary(vocabulary):
	"""Ensure vocabulary IDs match the zero-based encoder contract."""

	ids = sorted(int(field_id) for field_id in vocabulary.values())

	if ids != list(range(len(ids))):
		raise ValueError("Vocabulary IDs must be contiguous and start at 0.")


def validate_encoded_ids(datasets, vocabulary_size):
	"""Validate IDs before shifting them to reserve the padding ID."""

	for sequences, targets in datasets:
		all_sequence_ids = [field_id for sequence in sequences for field_id in sequence]
		all_ids = all_sequence_ids + targets

		if all_ids and (
			min(all_ids) < 0 or max(all_ids) >= vocabulary_size
		):
			raise ValueError(
				"Encoded field IDs must be within the vocabulary range "
				f"0-{vocabulary_size - 1}."
			)


def shift_field_ids(sequences):
	"""Shift zero-based field IDs so zero is reserved for padding."""

	return [
		[field_id + FIELD_ID_OFFSET for field_id in sequence]
		for sequence in sequences
	]


def shift_targets(targets):
	"""Shift zero-based target IDs so zero is reserved for padding."""

	return [target + FIELD_ID_OFFSET for target in targets]


def pad_sequences(sequences, max_length):
	"""Left-pad sequences and create a matching attention mask."""

	padded = np.full(
		(len(sequences), max_length),
		PAD_ID,
		dtype=np.int32,
	)
	attention_masks = np.zeros(
		(len(sequences), max_length),
		dtype=np.int32,
	)

	for index, sequence in enumerate(sequences):
		if len(sequence) > max_length:
			raise ValueError("Sequence length exceeds maximum sequence length.")

		start_position = max_length - len(sequence)
		padded[index, start_position:] = sequence
		attention_masks[index, start_position:] = 1

	return padded, attention_masks


def validate_inputs(inputs, attention_masks, targets, vocabulary_size, dataset_name):
	"""Validate padded inputs, masks, and shifted targets."""

	if inputs.ndim != 2 or attention_masks.shape != inputs.shape:
		raise ValueError(f"{dataset_name}: inputs and masks must have matching 2D shapes.")

	if len(inputs) != len(targets):
		raise ValueError(f"{dataset_name}: input and target counts do not match.")

	if np.any((inputs == PAD_ID) != (attention_masks == 0)):
		raise ValueError(f"{dataset_name}: padding and attention masks do not match.")

	real_field_ids = inputs[inputs != PAD_ID]
	if len(real_field_ids) > 0 and (
		real_field_ids.min() < 1 or real_field_ids.max() > vocabulary_size
	):
		raise ValueError(f"{dataset_name}: input field ID is outside the vocabulary range.")

	targets = np.asarray(targets)
	if len(targets) == 0:
		raise ValueError(f"{dataset_name}: dataset has no targets.")

	if targets.min() < 1 or targets.max() > vocabulary_size:
		raise ValueError(f"{dataset_name}: target ID is outside the vocabulary range.")

	print(f"{dataset_name}: validation passed ({len(inputs)} sequences)")


def save_dataset(name, inputs, masks, targets):
	"""Save Transformer inputs, masks, and targets as NumPy arrays."""

	np.save(OUTPUT_DIR / f"{name}_inputs.npy", inputs)
	np.save(OUTPUT_DIR / f"{name}_masks.npy", masks)
	np.save(OUTPUT_DIR / f"{name}_targets.npy", np.asarray(targets, dtype=np.int32))


def main():
	print("Preparing Transformer inputs...")

	OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

	with open(VOCABULARY_PATH, "r", encoding="utf-8") as file:
		vocabulary = json.load(file)

	validate_vocabulary(vocabulary)
	vocabulary_size = len(vocabulary)
	datasets = [
		load_dataset(TRAIN_INPUT),
		load_dataset(VALIDATION_INPUT),
		load_dataset(TEST_INPUT),
	]
	validate_encoded_ids(datasets, vocabulary_size)

	max_sequence_length = get_max_sequence_length(*datasets)
	prepared_datasets = []

	for sequences, targets in datasets:
		shifted_sequences = shift_field_ids(sequences)
		shifted_targets = shift_targets(targets)
		prepared_datasets.append(
			(*pad_sequences(shifted_sequences, max_sequence_length), shifted_targets)
		)

	for name, (inputs, masks, targets) in zip(
		("train", "validation", "test"),
		prepared_datasets,
	):
		validate_inputs(inputs, masks, targets, vocabulary_size, name.title())
		save_dataset(name, inputs, masks, targets)

	preprocessing_config = {
		"padding_id": PAD_ID,
		"field_id_offset": FIELD_ID_OFFSET,
		"vocabulary_size": vocabulary_size,
		"max_sequence_length": max_sequence_length,
	}

	with open(OUTPUT_DIR / "preprocessing_config.json", "w", encoding="utf-8") as file:
		json.dump(preprocessing_config, file, indent=4)

	print(f"Transformer inputs saved to {OUTPUT_DIR}")


if __name__ == "__main__":
	main()
