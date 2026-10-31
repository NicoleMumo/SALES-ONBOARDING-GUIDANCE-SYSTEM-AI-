# SalesMesh

An Android app that helps sales agents sign up customers (bank accounts or
telecom services in Kenya) the same way every time. It gives every agent the
company's standard form, checks each answer, suggests the next question with an
on-device AI model, warns when a customer may give up, and shows admins how
consistently the team follows the process.

> **Status: research prototype.** The AI models were trained and tested on
> simulated sessions, not real customers. The app is not ready for real customer
> data yet (see [Known limits](#known-limits)).

## Features

**Agents**
- Sign in to their company, choose the customer type, record the customer's consent
- Fill in the form section by section; each answer is checked when the agent leaves the box
- See a suggested next question and a warning if the customer may not finish
- Review, submit, or save a draft and continue later

**Admins**
- Overview: sessions started, finished, orderly, filled, and unfinished
- Invite and switch agents on or off
- Read every session; choose which questions are required; add the company's own
- Set the company's industry

## How the folders fit together

```
 ml/  (Python)                      firebase/                 android/  (Kotlin app)
 make simulated data  ──train──►  database rules  ◄──read/write──  the app
 train the two models                                        ▲
        │                                                    │
        └── models/ ──── copied in by the build ─────────────┘
```

| Folder | What is in it |
| --- | --- |
| `android/` | The app (Kotlin, Jetpack Compose, Firebase, TensorFlow Lite) and its build files. **Open this folder in Android Studio.** |
| `ml/` | Python code that makes the simulated data and trains the two AI models. `ml/models/` holds the trained files the app loads. |
| `firebase/` | The database access rules and a test for them. |
| `tests/` | Python tests for `ml/`. |
| `docs/` | The guides (below). |
| `legacy/` | Old and replaced files, kept for reference only. |
| `.github/` | Automatic checks that run on every pull request. |

## Getting started

You need Android Studio (JDK 17), Python 3, and your own Firebase project.

1. Open the `android` folder in Android Studio and let Gradle sync.
2. Save your Firebase file as `android/app/google-services.json` (not included).
   Steps: [Set up and run](docs/01-set-up-and-run.md).
3. Run the checks:

   ```powershell
   cd android
   .\gradlew.bat :app:assembleDebug
   .\gradlew.bat :app:testDebugUnitTest
   cd ..
   python -m unittest discover -s tests
   ```

Never add Firebase service-account keys or real customer data to this repository.

## Results (simulated data)

| What | Result |
| --- | --- |
| Next-question suggestion | AI model right **95.0%** of the time, against 86.5% for a fixed rule |
| Risk warning | Trained model ROC-AUC **0.71 to 0.74**, against 0.55 to 0.59 for the old hand-set rule (0.5 is guessing) |
| Consistency measure | Plain counting, not AI: at most 2 corrected answers and 2 questions out of order |

How these were tested, and their limits: [AI, data and results](docs/03-ai-data-and-results.md).

## Known limits

- Models were never tested on real agents or customers.
- The Firebase rules still need a full test and deployment before real data.
- Agent email verification is off for testing.
- Identity checks are manual: the app reads text from a document photo to help
  typing, but does no KYC, selfie or face-match check.
- No data-retention plan yet.

## Guides

1. [Set up and run](docs/01-set-up-and-run.md): Firebase, first admin and agent, building, testing
2. [How the app works](docs/02-how-the-app-works.md): screens, what is saved, where things are in the code
3. [AI, data and results](docs/03-ai-data-and-results.md): the data, the models, retraining

## Credits

- Sign-in photo: "Minimalist wooden desk and laptop with empty chair", Kaboompics.
- Font: Jost, SIL Open Font License 1.1 (licence text in `android/app/src/main/assets/licenses/`).
