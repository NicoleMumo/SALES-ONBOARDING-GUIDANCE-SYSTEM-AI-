# Completion-risk evaluation

The completion-risk scorer was evaluated against the held-out `test` split in
`data/onboarding_dataset.csv`. A session is considered difficult when its
scenario is `incomplete_sessions`, `long_completion_times`,
`revisited_fields`, or `validation_errors`. Normal completions are the
negative class.

Run the reproducible evaluation with:

```powershell
C:/Users/nicol/AppData/Local/Programs/Python/Python313/python.exe `
  src/evaluate_completion_risk.py
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

The scorer is conservative: it does not incorrectly flag any normal session
in this test split, but it misses some difficult sessions. Most detected
sessions are driven by validation errors. Long or incomplete sessions without
validation errors often remain below the medium-risk threshold. This means
the current scorer is useful for high-confidence intervention, but should not
be treated as a complete predictor of abandonment risk.
