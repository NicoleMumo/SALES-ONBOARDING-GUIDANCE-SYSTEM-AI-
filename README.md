# SalesMesh

SalesMesh is an Android app that helps sales agents onboard customers for
banking and telecom services. The app checks form answers and suggests which
field to complete next. Company admins manage agents, forms, and sessions.

## Before you start

- The app uses Kotlin, Jetpack Compose, Firebase, and TensorFlow Lite.
- The identification-document screen can read printed text from a selected
  photo on the device. OCR does not verify document authenticity or save the
  photo or extracted text.
- Identity checks are manual. The app has no paid KYC service, selfie,
  liveness, face-match, sanctions, or automated approval feature.
- The training data is simulated. It does not come from real customers.
- The model's results come from simulated data only. They do not show how well
  it works with real agents or customers.
- Firebase access rules and privacy plans still need testing before real
  customer data can be used.
- Agent email verification is turned off for testing.

## Build and test

1. Open this folder in Android Studio and sync Gradle.
2. Set up Firebase using [02 - Connect Firebase](docs/02-connect-firebase.md).
   Add your own `app/google-services.json`; it is not included in this project.
3. In PowerShell, build the app:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

4. Run the Android unit tests:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```

Do not add Firebase service-account keys or real customer data to this
repository.

## Main folders

| Folder or file | What it contains |
| --- | --- |
| `app/` | Android app, tests, and model files used by the app. |
| `ml/` | Simulated training data (`dataset.py`), the next-field model's layers, the risk model and consistency measure (`consistency.py`), and the training notebook. |
| `ml/models/` | Model files and settings the app loads. |
| `tests/` | Python tests. |
| `database.rules.json` | Firebase database access rules. These still need to be tested and deployed. |
| `docs/` | Five current guides listed below. |
| `legacy/` | Older files kept for reference. |

## Create training data

Run these commands from the project folder:

```powershell
python ml/dataset.py generate --sessions 8000 --seed 2026
python ml/dataset.py check ml/data/onboarding_dataset_v3.csv
```

The generated CSV is local and is not committed to Git. Run the notebook
`ml/onboarding_transformer.ipynb` in Colab or a Python 3.11 environment
to train and test the model.

Upload the whole CSV (about 34 MB) and wait for the upload to finish. The
notebook stops with a message if the file is cut off.

## How well the model works

The model suggests the next field. It was tested on simulated sessions from
agents it never saw in training, using only the fields the app allows at each
step (16,533 steps):

| Method | Right next field |
| --- | --- |
| App's own rule, no AI: first unfinished required field | 86.5% |
| Lookup table on the previous field and last check | 93.0% |
| Transformer model | **95.0%** |
| Best possible (the right field was an allowed choice) | 97.9% |

On the same steps the model was right and the app's rule wrong 1,449 times,
and the reverse 41 times. Two full runs gave 95.0% and 95.2%, so read the
model score as "about 95%". These numbers are from simulated data. They do
not show real-world accuracy. See
[04 - Training data and AI](docs/04-training-data-and-ai.md) for the details.

## Current guides

Read these in order:

1. [01 - Understand the app](docs/01-understand-the-app.md) — what the app
   does and how its main parts work.
2. [02 - Connect Firebase](docs/02-connect-firebase.md) — connect Firebase
   and prepare accounts.
3. [03 - Forms and saved data](docs/03-forms-and-saved-data.md) — form rules,
   answer checks, and saved sessions.
4. [04 - Training data and AI](docs/04-training-data-and-ai.md) — training
   data, predictions, model tests, and speed checks.
5. [05 - Testing and next steps](docs/05-testing-and-next-steps.md) — what is
   done and what still needs testing.

Older notes are kept in [legacy](legacy/README.md).
