# 04 - Training data and AI

This guide covers the simulated data, next-field model, completion-risk
guidance, and on-device speed test.

## Create and check data

Run these commands from the project folder:

```powershell
python data/generate_onboarding_data.py --sessions 8000 --seed 2026
python data/check_dataset.py data/onboarding_dataset_v3.csv
```

The generator uses Python's built-in libraries. It creates simulated banking
and telecom sessions. The simulated agent behavior, errors, delays, and
abandonment are assumptions, not customer measurements. The CSV is not
committed to Git.

Each CSV row is one event. The file has 28 columns, including session, agent,
industry, field, validation, progress, and outcome details. Events from the
same session stay in the same train, validation, or test split. Agents can
appear in more than one split, so the test does not show performance on
completely new agents.

The model predicts `next_onboarding_field`. Do not use that answer, final
session status, or fields that reveal the session outcome as model inputs.

The full field list and all 28 column names are in the generator and notebook.
In simple terms, the columns describe sessions, agents, fields, answer checks,
progress, and the final result.

## Android prediction test

The old 93.70% Top-1 result tested all known fields. It is not the score for
the app's remaining-field list.

The notebook now has a separate test that rebuilds the app's standard forms
and candidate fields. It also checks that candidate-masked TensorFlow Lite and
Keras outputs match. **These new notebook cells have not been run.**

The app first builds a list of fields that are not yet valid. If a required
field is still incomplete, those fields are the allowed suggestions. If all
required fields are valid, the app does not ask the model for a suggestion.
The model receives the interaction history, industry, customer type, latest
answer-check result, and allowed fields. It scores only those allowed fields.
The customer's answers are not sent to the model. If no useful model result is
available, the app guides the agent to the first incomplete required field.

In the test, **candidate coverage** means how often the correct next field is
in that allowed list. **Top-1** means the model's first suggestion is correct;
Top-3 means the correct field appears among its first three suggestions.

A local replay found 14,917 test cases where the app would ask for a
prediction. The correct next field was in the allowed list for 14,489 cases
(97.13%); 428 were outside it. This is candidate coverage, not model
accuracy. A field outside the allowed list counts as a miss; it is not removed
from the test. The model scores and comparison still need to be run in
`notebooks/onboarding_transformer.ipynb`, from top to bottom.

The replay covers the standard forms only. It does not include company custom
fields, company rule changes, or edits that make an earlier answer invalid.
Its results are an estimate of app behavior, not a full reproduction.

The model and its settings must be kept as a matching pair in `src/models/`.
Gradle copies both to the Android app. Do not replace only one file.

## Completion-risk guidance

Completion-risk guidance is separate from next-field prediction. It is a
rule-based warning based on answer checks, time spent, revisits, and required
field progress. It is not a proven probability that a customer will stop.

To evaluate it, choose a threshold using validation data:

```powershell
python src/evaluate_completion_risk.py --split validation --threshold 0.30
```

Then keep that threshold fixed for the test data:

```powershell
python src/evaluate_completion_risk.py --split test --threshold 0.30
```

The results compare the warning with the simulated session outcome. They do
not show real customer behavior.

## Measure Android model speed

Run this on a connected physical Android device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.example.salesonboarding.TfliteNextFieldPredictorBenchmark"
```

The test runs 50 warm-ups and 500 measured predictions. It reports the device,
Android version, median time, and 95th-percentile time in Logcat under
`NextFieldInferenceBenchmark`. Record the device, model version, and test date.
No physical-device result is recorded yet. This measures the model after it
loads; it does not measure the full app or Firebase.
