# Archived material

This folder keeps superseded or reference-only project material out of the
active source paths. Do not use these files for the current build or model
training.

- `data/onboarding_dataset_pre_v3.csv` is the previous generated dataset.
- `data/vocabulary/` belongs to the retired preprocessing pipeline.
- `notebooks/onboarding_dataset_generator_pre_v3.ipynb` is the previous data
  generator. Use `data/generate_onboarding_data.py` instead.
- `notebooks/onboarding_dataset_generator_legacy.ipynb` and
  `tests/test_dataset_generator_notebook.py` are an older generator and its
  test. The current generator and tests are under `data/` and `tests/`.
- `data_pipeline/` and `docs/` contain the older preprocessing pipeline and
  project notes.
- `previous-checkout/` is a local nested Git checkout and is not included in
  this repository. Its archived source copy is under `working-copy-fragment/`.
- `working-copy-fragment/` preserves an additional partial copy of that old
  project. Its `.git` directory was empty, so it is not a standalone checkout.
  Neither archive directory is a subproject of the active app.
- `misc/` holds unreferenced temporary files retained for safekeeping.

The active project is organized around `app/`, `data/`, `docs/`, `notebooks/`,
`src/`, and `tests/`.
