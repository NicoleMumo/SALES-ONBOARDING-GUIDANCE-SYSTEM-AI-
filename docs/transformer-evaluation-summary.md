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

## Interpretation and limitations

These are historical scores on synthetic, scenario-generated data with
mostly fixed field order. They show performance on this generator, not on
real agents or customers. Similar scores on seen and unseen prefixes do not
establish that the Transformer adds value over simpler rules. The baseline
commands below now compare canonical order, a majority lookup, a Markov
baseline, and the saved Transformer. Regenerate and save those results before
making a comparative model claim.

## One weak spot

The `full_name` field is only predicted correctly 73% of the time (out of
45 cases). This is because of a small amount of randomness built into the
practice data on purpose, not a flaw in the model. See
`docs/MODEL_README.md` for more on this.
## Reproducible comparison commands

`src/evaluate_next_field.py` compares the saved Transformer against the
canonical workflow, global-majority, four-feature majority lookup, and
two-field Markov baselines. It reports top-1, top-3, and macro-F1 for all
transitions and retry/revisit transitions. Run both split protocols after
installing `requirements.txt`:

```powershell
python src/evaluate_next_field.py --split-column split --seed 42 --output results/next-field-session.json
python src/evaluate_next_field.py --split-column agent_split --seed 42 --output results/next-field-agent.json
```

The session-level command includes the local Keras Transformer artifact when
it is available. The agent-level command deliberately reports baselines only:
retrain the notebook with `SPLIT_COLUMN = "agent_split"` before comparing the
Transformer on that split. Metrics describe only the scenario-generated
synthetic data. The commands have not been run here because pandas,
scikit-learn, and TensorFlow are not installed.
