# 6. Development guide

## Requirements

- Android Studio with the repository's Gradle wrapper
- An Android device or emulator for instrumentation tests
- Python with the packages listed by the project environment for dataset
  evaluation

## Common commands

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

The completion-risk evaluator reads `data/onboarding_dataset.csv` by default.
Pass another dataset with `--dataset` and choose a split with `--split`.

Keep generated directories such as `.gradle`, `build`, and `app/build`, plus
local configuration such as `local.properties`, out of commits.

## Contributing

Keep changes focused, add or update tests with behavior changes, and update
the relevant numbered guide when the workflow or architecture changes.
