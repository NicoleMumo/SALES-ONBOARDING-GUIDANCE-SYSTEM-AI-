# SalesMesh

SalesMesh is an Android app for companies whose sales agents sign up customers
in the field, for bank accounts or telecom services in Kenya. Onboarding done by
many agents tends to be inconsistent: questions are skipped, asked in different
orders, or answered wrongly. SalesMesh gives every agent the company's standard
form, checks each answer, suggests the next question with an on-device AI model,
warns when a customer may give up, and shows admins how consistently the team
follows the process.

## Status

- **Simulated data only.** The models were trained and tested on simulated
  sessions, never on real customers or agents. Their results show the methods
  work on a realistic simulation, not how well they work in the field.
- **Not ready for real customer data.** The Firebase access rules still need to
  be tested and deployed, agent email verification is turned off for testing,
  and there is no data-retention plan yet.
- **Identity checks are manual.** The app reads text from a document photo to
  help typing, but it does no KYC, selfie, liveness or face-match check and
  approves no one.

## What it does

**For agents:** sign in to their company; choose the customer type; record the
customer's consent; fill in the form section by section, with each answer
checked when they leave the box; see the AI's "Suggested next" question and a
warning if the customer may not finish; review every answer; submit. Unfinished
sessions are saved as drafts and can be continued later.

**For admins:** an overview of how the team is doing (share of sessions that
followed the standard, finishing rate, time, the most corrected questions,
agents compared, a weekly trend); invite and manage agents; read all sessions;
add company questions and choose which questions are required; set the company's
industry.

## Results (simulated data, agents never seen in training)

| What | Result |
| --- | --- |
| Next-question suggestion | Transformer **95.0%** right, against 86.5% for the app's rule without AI and 93.0% for a lookup table (best possible 97.9%) |
| Risk warning | Trained model ROC-AUC **0.71 to 0.74** on three datasets, against 0.55 to 0.59 for the old hand-set rule (0.5 is guessing) |
| Consistency measure | Plain counting (at most 2 corrected answers, at most 2 questions out of order); an anomaly-detection model was tried and failed its pass mark |

The details, limits and how each result was tested are in
[03 - AI, data and results](docs/03-ai-data-and-results.md).

## Quick start

1. Open this folder in Android Studio (JDK 17) and let Gradle sync.
2. Connect your own Firebase project and save its `google-services.json` as
   `app/google-services.json` (not included). See
   [01 - Set up and run](docs/01-set-up-and-run.md).
3. Build and test from PowerShell:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   .\gradlew.bat :app:testDebugUnitTest
   python -m unittest discover -s tests
   ```

Never add Firebase service-account keys or real customer data to this
repository.

## Project layout

| Folder or file | What it contains |
| --- | --- |
| `app/` | The Android app (Kotlin, Jetpack Compose, Firebase, TensorFlow Lite) and its tests. |
| `ml/` | The AI side: simulated-data generator and checker, the models' code, the training notebook, `requirements.txt`. |
| `ml/models/` | The model files the app loads (copied into the app by the build). |
| `firebase/` | Database access rules (still to be tested and deployed) and an early rules test that needs rewriting. |
| `tests/` | Python tests. |
| `docs/` | The three guides below. |
| `legacy/` | Earlier and replaced files, kept with notes on why ([legacy/README.md](legacy/README.md)). |

The Gradle files in the top folder (`build.gradle.kts`, `settings.gradle.kts`,
`gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/`) are required there by
Android Studio.

## Guides

1. [01 - Set up and run](docs/01-set-up-and-run.md): Firebase, the first admin
   and agent, building, testing, and the access checks to do before real data.
2. [02 - How the app works](docs/02-how-the-app-works.md): the agent's session,
   the admin's pages, the consistency measure, what is saved, and where things
   are in the code.
3. [03 - AI, data and results](docs/03-ai-data-and-results.md): the simulated
   data, the two models and the rejected one, all results, retraining, and what
   is not yet shown.

## Credits

- Sign-in photo: "Minimalist wooden desk and laptop with empty chair", Kaboompics.
- Fonts: Jost and Cormorant Garamond, SIL Open Font License 1.1 (licence texts
  in `app/src/main/assets/licenses/`).
