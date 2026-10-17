# 4. Model

The Transformer predicts the next onboarding field from the recent field
history plus industry, customer context, and validation status.

## Android inputs

The TensorFlow Lite model uses:

- `input_ids`: `[1, 30]` int32
- `attention_mask`: `[1, 30]` int32
- `industry_id`, `context_id`, and `validation_id`: `[1]` int32

The output is 33 field scores. Android calls the model through the plain
`Interpreter` API because the exported model has no named signature.

The model and `preprocessing_meta.json` are in `app/src/main/assets`.

## Data and model files

The current training and evaluation flow uses:

- `data/onboarding_dataset.csv` - the canonical synthetic dataset
- `notebooks/onboarding_dataset_generator.ipynb` - the sole generator for synthetic dataset runs
- `notebooks/onboarding_transformer.ipynb` - trains and evaluates the model
- `src/models/onboarding_model_flexible.keras` - saved Keras model
- `src/models/onboarding_model.tflite` - mobile TensorFlow Lite export
- `src/models/preprocessing_meta.json` - model vocabularies and preprocessing

The generator uses a configurable seed and writes both a session-level `split`
and an agent-level `agent_split`. All events in one session stay in the same
`split`, and all sessions for one agent stay in the same `agent_split`.
Generated rows are synthetic, scenario-based examples; passing structural
checks does not establish real-world model performance.

The earlier pipeline generated `banking_synthetic_sessions.csv`,
`telecom_synthetic_sessions.csv`, and
`multi_industry_onboarding_dataset.csv`. Those scripts remain under
`legacy/data_pipeline/` for historical reference and are not the current
training path.

## Limitations

The model was trained and evaluated on synthetic data. The high evaluation
scores reflect the structured dataset and should not be treated as proof of
production performance. Real-data validation, privacy review, and confidence
thresholding are still required before production use.
