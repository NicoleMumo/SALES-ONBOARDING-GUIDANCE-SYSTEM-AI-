# Android project structure

The Android application is in the `app` module and uses Jetpack Compose for
the onboarding experience.

The app uses a consistent green-and-neutral visual style across sign-in,
agent home, onboarding, completion, and admin screens. Onboarding progress is
based on validated required fields; optional fields do not inflate or block
the progress indicator. Agent profiles are added by administrators. The
current sign-in callback is a UI/demo flow, not Firebase Authentication, so
account registration and credential verification still require an
authentication backend.

```text
app/
  src/main/
    AndroidManifest.xml
    assets/                 TensorFlow Lite model and preprocessing metadata
    java/com/example/salesonboarding/
      SalesMeshApplication.kt Enables Firebase's persistent offline cache
      MainActivity.kt       Application entry point
      OnboardingPlaybook.kt Industry flows and versioned field sequence
      OnboardingScreen.kt   Playbook, readiness and onboarding UI
      OnboardingFormController.kt
                             Form state, saving, validation and guidance
      FirebaseFieldEntryStore.kt Session, playbook and audit persistence
      AdminDashboardScreen.kt Agent, field-rule and team insights UI
      NextFieldPredictor.kt Transformer prediction adapter
      validation/           Reusable field rules and live validation
      risk/                 Completion-risk scoring and assistance
      ui/theme/             Compose theme
    res/                    Android strings, colors and platform theme
  src/test/                 JVM unit tests for app logic
```

The root project contains the shared Gradle configuration and includes the
`:app` module. The Gradle wrapper is checked in so the project can be opened
and built with the repository's declared Gradle version.

New screens should remain in the `com.example.salesonboarding` package or a
feature-specific subpackage. Business logic should stay outside composables
so it can be tested with JVM unit tests.
