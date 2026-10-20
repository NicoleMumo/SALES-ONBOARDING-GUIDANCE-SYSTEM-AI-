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

## Build and test

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```
