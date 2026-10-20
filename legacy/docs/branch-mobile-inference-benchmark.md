# Mobile inference benchmark

The Android app now includes a benchmark for the production
`TfliteNextFieldPredictor`. It measures the complete TensorFlow Lite
prediction call, including input preparation and
`Interpreter.runForMultipleInputsOutputs`, while excluding model loading.

## Run

Use a physical Android device or emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.salesonboarding.TfliteNextFieldPredictorBenchmark
```

The benchmark output is reported by AndroidX Benchmark as minimum, median,
and maximum execution times. Model construction is intentionally outside the
measured block because the app creates the predictor once and reuses it for
the onboarding session.

## Acceptance target

For a responsive onboarding experience, the median prediction time should be
below 100 ms on the target device. Record the device model, Android version,
and benchmark output with evaluation results; emulator timings should not be
treated as representative of production hardware.
