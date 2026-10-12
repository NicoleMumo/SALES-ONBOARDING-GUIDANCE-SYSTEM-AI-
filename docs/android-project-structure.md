# Android project structure

The Android application is in the `app` module and uses Jetpack Compose for
the onboarding experience.

```text
app/
  src/main/
    AndroidManifest.xml
    assets/                 TensorFlow Lite model and preprocessing metadata
    java/com/example/salesonboarding/
      MainActivity.kt       Application entry point
      OnboardingScreen.kt   Compose onboarding UI
      OnboardingFormController.kt
                             Form state, saving, validation and guidance
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
