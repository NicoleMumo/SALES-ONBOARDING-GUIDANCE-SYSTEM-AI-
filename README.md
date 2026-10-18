# SalesMesh

SalesMesh is an Android assistant for guided customer onboarding. It validates
entries, predicts the next onboarding field with TensorFlow Lite, detects
completion risk, and stores sessions in Firebase.

## Documentation

Read the guides in order:

1. [Project overview](docs/01-overview.md)
2. [Onboarding reference](docs/02-onboarding-reference.md)
3. [Android app](docs/03-android-app.md)
4. [Model](docs/04-model.md)
5. [Evaluation](docs/05-evaluation.md)
6. [Development guide](docs/06-development.md)

## Quick start

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Firebase setup requires a local `app/google-services.json` file. See the
[Android app guide](docs/03-android-app.md) for details.


## Mentor roadmap status

### Merged into main

- Dataset reproducibility: PR #123.
- Android onboarding workflow integration: PR #124.

### Pushed branches awaiting review

- Risk duration fix: `fix/per-event-risk-duration-pr`.
- Model evaluation baselines: `chore/model-risk-evaluation-pr-clean`.
- Firebase rules and emulator setup: `chore/database-authentication-pr-clean`.

### Work still needed

- **Dataset:** verify the committed CSV has the expected session and agent splits. Keep results described as synthetic.
- **Model:** run evaluations for both split types, complete held-out-field tests, save the results, and measure latency on a named physical Android device. The baseline code has not produced final evaluation results.
- **Android app:** connect sign-in to Firebase Authentication and use trusted admin claims for routing. Add and run Firebase Emulator end-to-end tests and CI, then verify the build and tests from a clean checkout.
- **Database/authentication:** run the rules tests in the Firebase Emulator, connect app access to authenticated UIDs, set up trusted admin claims, and decide and document the data retention period.

Do not use real customer data until authentication, database ownership rules, and their emulator tests are working. Synthetic metrics do not prove real-world performance.
