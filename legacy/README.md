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

Moved here on 2026-10-29 (each file keeps the path it had before, without the
`legacy/` prefix; `git log --follow` shows its history):

- `src/demo.py` is a command-line demo of the next-field model. Nothing
  referred to it.
- `src/evaluate_next_field.py` was the next-field evaluation script. The
  notebook's app-style test replaced it: it uses the same rows, the app's
  allowed fields, and also measures the app's own fallback rule.
- `src/evaluate_completion_risk.py` scored the old hand-set risk rule against
  a threshold. `ml/consistency.py` replaced it: it trains the risk model the
  app now uses and measures the hand-set rule on the same agents.
- `tests/test_evaluation_baselines.py` tested only the two scripts above. It
  imports them from their old `src/` paths, so it is kept as a record, not as
  a working test.
- `src/consistency/anomaly.py` and `data/anomaly_report.json` are the Isolation
  Forest anomaly detector and its results. It failed the pass mark set before
  any result was seen (67-74% of planted deviations caught against 80% needed,
  and 10.5% of normal sessions flagged on one dataset against 10% allowed). On
  sessions whose only fault was step order it caught 8-14%, while the plain
  out-of-order count caught 97-98%. The app uses that plain count instead. The
  script imports the old `features` and `playbooks` modules, which are now part
  of `ml/consistency.py`, so it no longer runs as it is.

- `app/removed-code.md` keeps pieces of Kotlin removed from files that are
  still in use (an old model path, the selfie and face-match questions,
  duplicate session-store functions, a deprecated alias), with the reason for
  each.

The active project is organized around `app/`, `ml/`, `docs/`, and `tests/`.
`ml/` replaced the former `data/`, `notebooks/` and `src/` folders on
2026-10-29.
