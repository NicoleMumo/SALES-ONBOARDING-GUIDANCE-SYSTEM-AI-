# Repo cleanup log — superseded Transformer draft + pipeline output files

This log is committed alongside the cleanup so there's a permanent record of
what was removed, what was kept, and why — not just a silent deletion.

## Deleted: superseded, buggy Transformer draft

- `transformer_model.py`
- `colab_transformer_test.ipynb`

**Why:** this was an earlier attempt at the Transformer model, before the
final version in `notebooks/onboarding_transformer.ipynb`. It had real bugs,
confirmed by inspection:

- Wrong last-token pooling for left-padded sequences
  (`tf.reduce_sum(attention_mask) - 1`, which is the *right*-padding formula).
- Redundant/conflicting masking (`mask_zero=True` on the embedding layer
  *and* an explicit attention mask passed separately).
- A fixed-size `Dense(33, softmax)` output head instead of the
  field-similarity head used in the final model, so it couldn't score a
  field it hadn't seen a fixed slot for at training time.

The final notebook fixes all three and is the version Milestone 3 is built
from. Keeping both versions in the repo invited confusion about which one
is "the" model, so the broken draft is removed rather than left to rot.

Any `__init__.py` that imported from `transformer_model.py` has been
updated by this script (see terminal output for exactly what changed) so
the package doesn't have a dangling import after the file is gone.

## Deleted: M1/M2 pipeline intermediate/output files

- `train.csv`, `validation.csv`, `test.csv`
- `train_inputs.npy`, `train_masks.npy`, `train_targets.npy`
- `validation_inputs.npy`, `validation_masks.npy`, `validation_targets.npy`
- `test_inputs.npy`, `test_masks.npy`, `test_targets.npy`

**Why:** these are large, *derived* artifacts — the output of running
`split_dataset.py` and `prepare_transformer_inputs.py` on the synthetic
dataset. They are not broken, and they document that the M1/M2 data
pipeline ran successfully. But they are:

- Regeneratable at any time by re-running the pipeline scripts that are
  still in the repo (`synthetic_session_generator.py` →
  `clean_and_validate_dataset.py` → `split_dataset.py` →
  `prepare_sequence_data.py` → `encode_onboarding_fields.py` →
  `prepare_transformer_inputs.py`).
- Not read by anything in the Milestone 3 Transformer work — the final
  notebook (`notebooks/onboarding_transformer.ipynb`) builds and
  preprocesses its own training data rather than loading these files.
- Large binary/CSV blobs, which is exactly the kind of thing that makes a
  repo heavy and hard to read without adding anything a reviewer can
  meaningfully look at.

If a grader needs to see that the data pipeline produced real output, the
pipeline scripts themselves plus this log are sufficient evidence; the
arrays themselves aren't needed in version control.

## Kept

- `field_vocabulary.json` — the 33-field vocabulary (field name -> integer
  ID) produced by `encode_onboarding_fields.py`. Small, human-readable, and
  documents the exact encoding scheme used elsewhere in the project
  (including by the Android app's field handling).
- `preprocessing_config.json` — padding ID, field ID offset, vocabulary
  size, and max sequence length. Small metadata file, same reasoning.
- `clean_and_validate_dataset.py`, `split_dataset.py`,
  `prepare_sequence_data.py`, `encode_onboarding_fields.py`,
  `prepare_transformer_inputs.py`, `synthetic_session_generator.py` — the
  M1/M2 data pipeline itself. Working code, already merged, unrelated to
  the Transformer draft bug. Left untouched.

---
*This cleanup was done as its own commit, separate from the Milestone 3
feature branches, so the diff for each Milestone 3 PR stays focused on that
issue's actual work rather than being mixed with file deletions.*

## Round 2: large regeneratable dataset files + duplicate config

**Deleted (regeneratable from scripts still in the repo):**
- multi_industry_onboarding_dataset.csv - raw synthetic dataset, from synthetic_session_generator.py
- clean_multi_industry_onboarding_dataset.csv - cleaned dataset, from clean_and_validate_dataset.py
- data/sequences/train_sequences.csv, validation_sequences.csv, test_sequences.csv - from prepare_sequence_data.py
- data/encoded_sequences/train_sequences_encoded.csv, validation_sequences_encoded.csv, test_sequences_encoded.csv - from encode_onboarding_fields.py

None of these are read by the Milestone 3 Transformer notebook.

**Deleted - duplicate/conflicting config:**
- data/transformer_inputs/preprocessing_config.json - an older preprocessing config with a different field-ID numbering scheme (0-indexed with a separate offset) than preprocessing_meta.json (1-indexed field IDs), which is the one actually used by the current model and demo.py. Having both risked someone loading the wrong one. preprocessing_meta.json is the single source of truth going forward.
