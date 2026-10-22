# SalesMesh

SalesMesh is an Android app that helps sales agents onboard customers for
banking and telecom services. The app checks form answers and suggests which
field to complete next. Company admins manage agents, forms, and sessions.

## Before you start

- The app uses Kotlin, Jetpack Compose, Firebase, and TensorFlow Lite.
- The identification-document screen can read printed text from a selected
  photo on the device. OCR does not verify document authenticity or save the
  photo or extracted text.
- Smile ID is only being evaluated. The app has no live KYC, selfie, liveness,
  face-match, sanctions, or automated approval service.
- The training data is simulated. It does not come from real customers.
- The new test for the model's Android prediction flow has not been run.
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
| `data/` | Tools to create and check simulated training data. |
| `notebooks/` | Model training, testing, and export notebook. |
| `src/models/` | Main model files and model settings. |
| `tests/` | Python tests. |
| `database.rules.json` | Firebase database access rules. These still need to be tested and deployed. |
| `docs/` | Five current guides listed below. |
| `legacy/` | Older files kept for reference. |

## Create training data

Run these commands from the project folder:

```powershell
python data/generate_onboarding_data.py --sessions 8000 --seed 2026
python data/check_dataset.py data/onboarding_dataset_v3.csv
```

The generated CSV is local and is not committed to Git. Run the notebook
`notebooks/onboarding_transformer.ipynb` in Colab or a Python 3.11 environment
to train and test the model.

The old 93.70% Top-1 result tested all known fields. It does not show how well
the model works with the app's remaining-field list. The new app-style test is
in the notebook but has not been run. See
[04 - Training data and AI](docs/04-training-data-and-ai.md).

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
