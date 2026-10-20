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

The risk scorer combines validation errors, long sessions, field revisits,
and low progress. Run it with:

```powershell
python src/evaluate_completion_risk.py
```

On the held-out test split it achieved 71.22% accuracy, 100% precision, and
44.78% recall. It is conservative: it avoids false alarms but misses some
difficult sessions.

## Mobile inference

Run the Android benchmark on a device or emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.salesonboarding.TfliteNextFieldPredictorBenchmark
```

The target is a median prediction time below 100 ms on the target device.
Record the device, Android version, and benchmark output.
