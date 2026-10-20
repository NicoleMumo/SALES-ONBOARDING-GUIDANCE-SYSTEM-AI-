# Archived material

This folder keeps superseded or reference-only project material out of the
active source paths. Do not use these files for the current build or model
training.

- `data/onboarding_dataset_pre_v3.csv` is the previous generated dataset.
- `data/vocabulary/` belongs to the retired preprocessing pipeline.
- `notebooks/onboarding_dataset_generator_pre_v3.ipynb` is the previous data
  generator. Use `data/generate_onboarding_data.py` instead.
- `data_pipeline/` and `docs/` contain the older preprocessing pipeline and
  project notes.
- `previous-checkout/` preserves the old checkout's Git history.
- `working-copy-fragment/` preserves an additional partial copy of that old
  project. Its `.git` directory was empty, so it is not a standalone checkout.
  Neither archive directory is a subproject of the active app.
- `misc/` holds unreferenced temporary files retained for safekeeping.

The active project is organized around `app/`, `data/`, `docs/`, `notebooks/`,
`src/`, and `tests/`.
