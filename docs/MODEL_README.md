# Onboarding Next-Field Transformer — Final

Predicts the next field a banking/telecom onboarding session will ask for,
given the fields entered so far. Trained on a synthetic dataset built
specifically so the task is learnable and legitimately close to
deterministic (see "Why the data looks like this" below) — not something
pulled off the shelf.

**Start here:** `onboarding_transformer_final.ipynb`. It is the one
complete deliverable — data loading, training, evaluation, generalization
proof, edge-case testing, and defensive error handling, all in one
self-contained notebook. Everything else in this package supports it.

## Scope — what this project is and isn't

**Onboarding** is the whole process of getting a new customer set up:
collecting details, verifying identity, picking a plan, activating the
account. **This project is one component of that**: a model that predicts
what field should come next, given what's been filled in so far. It does
not collect, store, or verify real data, handle payments, or provide a
user interface — it's a guidance/auto-suggest component, not a standalone
onboarding system.

## Is it deployable?

**Not yet**, for three concrete reasons:
1. Trained and evaluated entirely on synthetic data — never validated
   against real customer sessions.
2. Until this version, no error handling existed for bad input and no
   confidence thresholding for when the model should defer to a human.
   Section 10 of the notebook fixes the error-handling half of this; a
   confidence threshold for low-certainty predictions is still open.
3. Real onboarding data is sensitive (IDs, addresses) — production use
   needs a privacy/security review this project hasn't had.

Reasonable next step: shadow-test it against real session logs (log
predictions without acting on them) before it drives any real decision.

## Files in this package

### Dataset
| File | Purpose |
|---|---|
| `onboarding_generator_v2.py` | Generates the synthetic session data. Run `generate_dataset(n, industry)` for a fresh draw. |
| `onboarding_dataset.csv` | 6,000 sessions (3,000 banking + 3,000 telecom), 89,271 rows, pre-split by session into train/validation/test. |
| `README_dataset.md` | Full column-by-column schema. |

### Model
| File | Purpose |
|---|---|
| **`onboarding_transformer_final.ipynb`** | **The main deliverable.** Upload the dataset CSV and run top to bottom. |
| `model_def.py` | Transformer backbone layers (also pasted into the notebook — kept standalone for `demo.py`). |
| `field_similarity_head.py` | The flexible output head (also pasted into the notebook — kept standalone for `demo.py`). |
| `onboarding_model_flexible.keras` | The trained model (19 epochs, best weights) — so you can demo without retraining. |
| `onboarding_model.tflite` | **TensorFlow Lite export, for Android.** Same trained weights, converted for on-device inference (see "TensorFlow Lite export" below). |
| `preprocessing_meta.json` | Vocabularies needed to encode new input the same way training data was encoded. |
| `demo.py` | Live, step-by-step terminal demo — predicts a fresh session field by field, plus a robustness-check mode. |
| `simple_model_compare.ipynb` | Tests whether a much simpler (non-Transformer) model performs comparably — it doesn't, see below. |

## TensorFlow Lite export (for Android)

`onboarding_model.tflite` is generated and verified inside Section 14 of
the notebook. Two separate, real conversion problems were found and fixed
there — not hand-waved:

1. **`MultiHeadAttention` → TFLite gives `NaN`.** The straightforward
   conversion path (`TFLiteConverter.from_keras_model`) runs but silently
   outputs `NaN` for every prediction. Isolated to `layers.MultiHeadAttention`
   itself (a bare instance, converted alone, with or without a mask, comes
   out `NaN`) — a TFLite/TensorFlow limitation on this TF version, not a bug
   in this model. **Fix:** for export only, each Transformer block's
   attention is recomputed with plain ops (`einsum`/`matmul`/`softmax`)
   using the *same trained weights* — no retraining.
2. **Attaching a named signature (`"serving_default"`) also gives `NaN`.**
   Confirmed independently of problem 1, across three different ways of
   attaching a signature (`tf.saved_model.save` with a plain `tf.Module`,
   with the Keras model itself, and passing `trackable_obj=model` to the
   converter with no disk save at all) — all three corrupt the weights
   during conversion. The only path that gives correct numbers skips the
   signature step, so **this file has no named signature.**

Both fixes are checked against the real model on actual test rows before
the file is trusted (max probability difference ≈ 1e-6, zero Top-1
mismatches across the sample checked).

**Inputs** (batch size fixed at 1): `input_ids` `[1,30]` int32,
`attention_mask` `[1,30]` **int32** (0/1, not bool — matches a typical
Android wrapper), `industry_id`/`context_id`/`validation_id` each `[1]`
int32 — all encoded the same way as training (see `preprocessing_meta.json`
for the vocabularies). **Output:** `output_0`, `[1,33]` float32, same class
order as `field_to_id` (output index `i` = class `i+1`).

**Because there's no named signature**, call this file through the plain
tensor-based `Interpreter` API on Android (`run(...)` /
`runForMultipleInputsOutputs(...)`, addressing tensors by name or index),
not `interpreter.getSignatureRunner("serving_default")` — that call will
fail against this file, correctly, since no such signature exists.

## Architecture

Token embedding (64-dim) + learned positional embedding → 2 Transformer
blocks (4 heads, feed-forward 128, dropout 0.15) → pool the last real
token (sequences are left-padded, so this is always position `-1`,
verified against the data, not assumed) → concatenate with
industry/context/validation-status embeddings → dense layer → **field
description-matching head** (see below) instead of a fixed output layer.

### Why a description-matching head, not a plain softmax

A standard classifier's last layer is `logits = session_vector @ W + b`,
with one independently-learned vector per class, fixed at training time —
no way to add a class later. Here, every field (known or new) is described
in one sentence; a shared, trainable text encoder turns any description
into a vector; prediction = similarity between the session vector and
each candidate's vector. Adding a field means adding one sentence to the
catalog in the notebook — no retraining. Proven on the real trained model
(notebook section 11): a field never seen in training scored a real,
sensible value (not a crash, not zero), ranking competitively against the
33 trained fields.

This came at **no accuracy cost** versus a fixed output layer — tested
side by side, see Performance below.

## Why the data looks like this

The original data generator chose field order within a stage with
`random.shuffle()`, independent of every other column. Simulated at scale
(414,435 transitions), the resulting ceiling was **71.4% Top-1 at best**,
with 9.2% of cases being exact coin-flips by construction — no model could
exceed that, not from lack of trying.

The generator was rebuilt: field order within a stage is now fixed (a
consistent script, which real guided onboarding flows plausibly follow),
validation failures trigger a visible retry of the same field instead of
being decorative, and session context (individual/business, or telecom
service type) is exposed as an explicit feature, known at the start of a
real session. A small amount of genuinely unresolvable noise is
deliberately retained (~4% chance, in one scenario type only, of an
unexplainable revisit) so the benchmark isn't suspiciously perfect.

**This is a real, disclosed design decision, not a hidden one.** State it
plainly if asked: the high accuracy reflects a dataset built to be close
to deterministic, not a claim about how real customers behave.

## Performance (full 19-epoch run, 58,453 train / 12,415 test transitions)

| Metric | Target | Achieved |
|---|---:|---:|
| Top-1 | 85% | 99.46% |
| Top-3 | 95% | 99.69% |
| Top-5 | 98% | 99.80% |
| Macro F1 | 80% | 99.09% |

**The number that matters more than any of these:** accuracy on sequences
never seen during training, vs. ones that were.

| | Top-1 | Macro F1 |
|---|---:|---:|
| Seen prefixes (n=11,758) | 99.48% | 99.17% |
| **Novel prefixes (n=657)** | **99.09%** | **95.28%** |

Train/test gap: **-0.12pp** (test is marginally higher — no overfitting
signature). On a much smaller 215-session run earlier in this project,
this same seen/novel gap was 21.8pp — confirming the gap shrinks with more
data, which is the expected signature of a genuine small-data effect, not
a broken architecture.

## Edge-case testing (notebook section 9)

Headline accuracy hides real variation. Tested and found:

- **Top-1 holds steady (~96-100%) across sequence length**, but **Macro F1
  degrades as sessions get longer** (97% at the first field, down to ~79%
  for 11-20-field sessions) — later, rarer fields are handled worse, even
  though the aggregate number doesn't show it.
- **100% Top-1 on very long sequences (n=69) and the "last field is a
  retry" subset is not memorization** — checked directly: both subsets are
  mostly or entirely prefixes never seen in training, and accuracy holds
  on just the novel ones. The real explanation is structural: late in a
  session, very few fields remain possible, so the problem gets
  mechanically easier, regardless of model quality.
- **Only one field is genuinely weak: `full_name`** (73% recall, n=45). It
  can only appear as a *target* through the rare unresolvable-revisit
  noise, since it's always the first field entered — small, inherently
  noisy signal by design, not a modeling flaw. (An earlier, wrongly-scoped
  evaluation reported 32% Macro F1 across 5 "rare" classes — that number
  used scikit-learn's default label set on a filtered subset, which
  silently includes stray misclassifications into unrelated classes. The
  correctly-scoped number, restricted to the classes actually present, is
  96.8%; see the methodology note in notebook section 9c.)
- **Two real failure modes, found by deliberately trying to break it**:
  an unrecognized field name crashed with a raw `KeyError`, and sequences
  longer than 30 fields were silently truncated with no warning. **Both
  are fixed in section 10** — a clear, catchable error for the first, an
  explicit warning for the second. `demo.py` includes the same fix
  (`--robustness` flag).

## Is a Transformer overkill? — tested, not assumed

A Gradient Boosted Trees model (`simple_model_compare.ipynb`, 7 hand-made
features: last 3 fields, industry, context, validation status, sequence
length) trained in under 2 seconds:

| | Overall Top-1 | Novel-prefix Top-1 |
|---|---:|---:|
| Transformer | 99.46% | **99.09%** |
| Simple tree model | 98.20% | **80.21%** |

Nearly matches on familiar data, drops sharply (18pp) on genuinely new
sequences — concrete justification for the Transformer's complexity: it's
not about the headline score, it's about generalization.

## Accessibility (screen readers)

Model output is a plain field name string — screen-reader support is a
front-end concern (an `aria-live` region on whatever UI displays the
suggestion), not something the model needs to handle.

## Known limitations

- **Not production-deployed.** Synthetic data only; see "Is it deployable?" above.
- **The text encoder is a simple bag-of-words average**, not a full
  language model — it matches on shared vocabulary between descriptions,
  not deep meaning. `"cellphone number"` won't automatically match
  `"mobile phone number"` unless the words literally overlap.
- **New fields get a real score but no learned sense of sequence timing**
  — the model knows how well a new field's description matches the
  session's general context, not *when* in a session it should appear.
  Full accuracy on a new field still needs eventual retraining with real
  examples of it in context.
- **No confidence thresholding yet** — the model always returns its best
  guess, even when genuinely uncertain. A production version should defer
  to a human below some confidence threshold rather than guess.
- Before production use: re-run the ambiguity check (notebook section 4)
  against real session logs to see how close real data's irreducible
  ambiguity is to this synthetic dataset's.
