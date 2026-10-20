# 5. Evaluation

## Next-field prediction

On 12,415 unseen examples:

| Metric | Result |
|---|---:|
| Top-1 accuracy | 99.46% |
| Top-3 accuracy | 99.69% |
| Macro F1 | 99.09% |

These results are documented in the evaluation notebook and are based on
synthetic onboarding sequences.

## Completion risk

The risk scorer combines validation errors, long individual events, field
revisits, and low progress. It does not compare total session duration against
an event-level duration threshold. Android and Python use the same rule.
Labels come from synthetic generator scenarios, so these scores do not prove
real-world abandonment prediction.

Run both held-out splits with:

```powershell
python src/evaluate_completion_risk.py --split-column split --seed 42 --output results/completion-risk-session.json
python src/evaluate_completion_risk.py --split-column agent_split --seed 42 --output results/completion-risk-agent.json
```

The historical 71.22% accuracy, 100% precision, and 44.78% recall were
calculated using total session duration. Rerun both commands before reporting
current results. Each output includes the split, confusion matrix, PR-AUC,
recall at precision of at least 0.80, Brier score, and calibration bins.

## Mobile inference

Run the Android benchmark on a device or emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.salesonboarding.TfliteNextFieldPredictorBenchmark
```

The target is a median prediction time below 100 ms on the target device.
Record the device, Android version, and benchmark output.
