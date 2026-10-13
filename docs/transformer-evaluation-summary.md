# Transformer evaluation summary (issue #53)

The full details are in `notebooks/onboarding_transformer.ipynb` (section 8)
and `docs/MODEL_README.md`. This file is the short answer to issue #53: how
well the model predicts the next field, tested on data it never saw during
training.

## Results (tested on 12,415 examples the model never trained on)

| Metric | Goal | Actual |
|---|---:|---:|
| Top-1 accuracy | 85% | 99.46% |
| Top-3 accuracy | 95% | 99.69% |
| Top-5 accuracy | 98% | 99.80% |
| Macro F1 | 80% | 99.09% |

All four goals were passed.

## Seen vs. brand-new sequences

| | Top-1 | Macro F1 |
|---|---:|---:|
| Sequences like ones seen before (11,758 of them) | 99.48% | 99.17% |
| Sequences never seen before (657 of them) | 99.09% | 95.28% |

## Why these numbers can be trusted (the model is not just memorizing)

- **The test score is not lower than the training score.** If the model
  were just memorizing, the test score would drop. Instead it's basically
  the same (even a tiny bit higher) - the opposite of memorizing.
- **It still does well on sequences it has never seen before** (99.09%
  accuracy, almost identical to the 99.48% on familiar ones). A model
  that only memorized the training data would do much worse here.
- **This got better as more data was added**, not worse. On an earlier,
  smaller test (215 sessions), the model did a lot worse on new sequences
  than familiar ones (a 21.8-point gap). With more data, that gap almost
  disappeared. That's what real learning looks like - memorizing would
  not improve just from adding more examples.

## One weak spot

The `full_name` field is only predicted correctly 73% of the time (out of
45 cases). This is because of a small amount of randomness built into the
practice data on purpose, not a flaw in the model. See
`docs/MODEL_README.md` for more on this.
