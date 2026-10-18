# 3. Android app

The Android app is in the `app` module and uses Kotlin, Jetpack Compose,
Firebase Realtime Database, and TensorFlow Lite.

## Important code

```text
app/src/main/
  assets/                         TFLite model and preprocessing metadata
  java/com/example/salesonboarding/
    MainActivity.kt               App entry point and flow
    LoginScreen.kt                Agent/admin sign-in
    OnboardingScreen.kt           Compose onboarding UI
    OnboardingFormController.kt   Form state, saving, validation, guidance
    NextFieldPredictor.kt         TFLite prediction adapter
    validation/                   Field rules and live validation
    risk/                         Completion-risk scoring
```

Business logic should stay outside composables so it can be tested with JVM
unit tests.

## Firebase

Add the project-specific `app/google-services.json` locally, enable Realtime
Database, and configure its security rules. It is intentionally not committed.
Sessions are stored under `onboardingSessions/{sessionId}`.

The repository includes `database.rules.json`. It allows agents to access only
sessions whose `agentUid` matches their Firebase UID. Admin access requires a
trusted `admin: true` custom claim. The current app login is still a demo flow;
it does not sign in to Firebase or verify an admin claim. The database rules
and login flow are therefore not ready for real customer data.

Audit events should contain only the event type, field ID, and timestamp.
Customer values belong in the saved session entries, not in event logs or
per-keystroke analytics. The app currently does not enforce a data retention
period.

Run the Realtime Database Emulator rule tests from the repository root with
`npm install` followed by `npm run test:database-rules`. The committed
`app/google-services.example.json` is only a build placeholder; it cannot
connect to a real Firebase project.

## Build and test

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```
