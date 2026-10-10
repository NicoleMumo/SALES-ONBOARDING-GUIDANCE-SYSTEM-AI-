# Earlier milestone: data pipeline (not used by the Transformer)

These scripts are the completed data pipeline from an earlier milestone
(generate -> clean -> split -> sequence -> encode -> prepare-for-transformer).
They're kept here as a record of that work, but **the Milestone 3 Transformer
model does not use them.**

The Transformer (`notebooks/onboarding_transformer.ipynb`) trains from a
separate, self-contained dataset and generator instead:
`src/onboarding_generator_v2.py` and `data/onboarding_dataset.csv`.

## Files

- `synthetic_session_generator.py` — generates raw synthetic onboarding sessions
- `clean_and_validate_dataset.py` — cleans and validates the raw dataset
- `split_dataset.py` — stratified train/validation/test split by session
- `prepare_sequence_data.py` — builds next-field prefix/target sequences
- `encode_onboarding_fields.py` — builds the field vocabulary and encodes sequences
- `prepare_transformer_inputs.py` — pads sequences and builds attention masks

The large files these scripts produce were removed from the repo (see
`docs/CLEANUP_LOG.md`) since they're regeneratable and unused by current
work. `data/vocabulary/field_vocabulary.json`, which `encode_onboarding_fields.py`
produces, was kept since it's small and still a useful reference.
