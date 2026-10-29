# 04 - Training data and AI

This guide covers the simulated data, next-field model, completion-risk
guidance, and on-device speed test.

## Create and check data

Run these commands from the project folder:

```powershell
python ml/dataset.py generate --sessions 8000 --seed 2026
python ml/dataset.py check ml/data/onboarding_dataset_v3.csv
```

The generator uses Python's built-in libraries. It creates simulated banking
and telecom sessions. The simulated agent behavior, errors, delays, and
abandonment are assumptions, not customer measurements. The CSV is not
committed to Git.

Each CSV row is one event. The file has 28 columns, including session, agent,
industry, field, validation, progress, and outcome details. The data is split
by agent: all sessions of an agent are in one split (about 70% train, 15%
validation, 15% test). The test therefore only has agents the model never saw
in training. The banking form in the data matches the app's banking form,
including the ID photo step. It has no selfie, liveness or face-match steps,
because the app has none. A test in `tests/` fails if the two forms drift apart.

The model predicts `next_onboarding_field`. Do not use that answer, final
session status, or fields that reveal the session outcome as model inputs.

The full field list and all 28 column names are in the generator and notebook.
In simple terms, the columns describe sessions, agents, fields, answer checks,
progress, and the final result.

## Android prediction test

An older 93.70% Top-1 figure is no longer used. It was measured on a different
banking form, with the same agents in training and test, and on all known
fields instead of the app's allowed list.

The notebook has a test that rebuilds the app's standard forms and allowed
fields, compares the model with simpler methods on the same steps, and checks
that the TensorFlow Lite file gives the same answers as the trained model.

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

## Results

All numbers are on simulated data, from agents not seen in training, in the
steps where the app asks for a suggestion (16,533 steps). The correct next
field was in the allowed list in 97.9% of them, so 97.9% is the best any method
can score here. A field outside the allowed list counts as a miss.

| Method | Right next field |
| --- | --- |
| App's own rule: first unfinished required field | 86.5% |
| Lookup on customer type and current field | 86.5% |
| Lookup on customer type, previous field, current field, last check | 93.0% |
| Transformer model (Top-1) | **95.0%** |

- On the same steps, the model was right and the app's rule wrong 1,449 times;
  the app's rule was right and the model wrong 41 times. Against the lookup
  table the counts were 355 and 30. Both gaps are far larger than chance (the
  test statistic is above 16; a value above 1.96 is already unlikely by chance).
- On field histories not seen in training the model scored 94.2%. It was weakest
  in sessions longer than 20 steps (91.5%, only 130 steps).
- On all 32 fields (not only the allowed ones): Top-1 95.0%, Top-3 98.7%,
  Top-5 99.6%, Macro F1 95.0%.
- The TensorFlow Lite file matches the trained model: the largest difference
  was under 0.000001, with no different top suggestions, both for all fields and
  for the app's allowed lists.
- The model loads and gives a prediction in the app on an Android emulator.

Two full runs gave 95.2% and 95.0% for the model, so quote it as about 95%.
Everything comes from one simulator that the project's authors wrote. The
simulator's order of fields is the same as the app's standard order, which is
part of why the app's own rule already scores 86.5%.

**Pass mark.** The pass mark was written after the first results were seen, so
it describes what was achieved; it is not a prediction made in advance. The
model passes if, in at least two full runs, it beats the app's own rule by at
least 5 points and beats the lookup table on the same steps by more than chance,
and the TensorFlow Lite file matches the trained model with no different top
suggestions. Both runs met this.

The test covers the standard forms only. It does not include company custom
fields, company rule changes, or edits that make an earlier answer invalid.
Its results are an estimate of app behavior, not a full reproduction.

The model and its settings must be kept as a matching pair in `ml/models/`.
Gradle copies both to the Android app. Do not replace only one file.

## Completion-risk guidance

Completion-risk guidance is separate from next-field prediction. It is a
warning based on answer checks, time spent, revisits, and required field
progress. It is not a proven probability that a customer will stop.

The app uses a trained logistic regression saved in `ml/models/risk_model.json`.
If that file is missing or refused, it falls back to the older hand-set rule.
To train the model and compare it with the hand-set rule on agents never seen
in training:

```powershell
.\.venv-ml\Scripts\python.exe ml/consistency.py `
  ml/data/onboarding_dataset_v3.csv --out ml/models/risk_model.json `
  --report ml/models/risk_model_report.json
```

The warning thresholds are measured on validation agents; test agents are
scored once. The results compare the warning with the simulated session
outcome. They do not show real customer behavior. The older evaluation script
for the hand-set rule is in `legacy/`.

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
