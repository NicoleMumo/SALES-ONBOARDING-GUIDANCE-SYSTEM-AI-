# Completion-risk evaluation

The completion-risk scorer was evaluated against the held-out `test` split in
`data/onboarding_dataset.csv`. A session is considered difficult when its
scenario is `incomplete_sessions`, `long_completion_times`,
`revisited_fields`, or `validation_errors`. Normal completions are the
negative class.

The duration feature counts whether any individual event exceeded 15 minutes;
it does not compare summed session duration with an event-level threshold.
The same rule is used by the Android scorer. The ground-truth labels still
come from the synthetic generator's scenario, so this is not independent
evidence that the score predicts real abandonment.

Run each reproducible evaluation with:

```powershell
python src/evaluate_completion_risk.py --split-column split --seed 42 --output results/completion-risk-session.json
python src/evaluate_completion_risk.py --split-column agent_split --seed 42 --output results/completion-risk-agent.json
```

## Test-set result

| Metric | Result |
| --- | ---: |
| Sessions | 900 |
| Accuracy | 71.22% |
| Precision | 100.00% |
| Recall | 44.78% |

| | Predicted difficult | Predicted normal |
| --- | ---: | ---: |
| Actually difficult | 210 | 259 |
| Actually normal | 0 | 431 |

The figures above are the previous session-total-duration evaluation and are
retained as historical results; rerun both commands after the per-event fix
before citing current risk performance. Each output JSON includes the selected
split, confusion matrix, average precision, recall at precision of at least
0.80, Brier score, and calibration bins. Do not interpret these synthetic
metrics as real customer performance.
