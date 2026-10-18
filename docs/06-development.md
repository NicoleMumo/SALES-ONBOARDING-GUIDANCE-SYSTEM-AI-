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

## Regenerate the synthetic dataset

Run `notebooks/onboarding_dataset_generator.ipynb` from top to bottom. Its
settings cell controls the session count, industry, random seed, and output
path. The notebook uses the Python standard library for generation and can be
run locally or in Google Colab. Reusing the same seed with the same notebook
version reproduces the generated rows and split assignments.

The generated CSV includes a session-level `split` and an agent-level
`agent_split`, each with train, validation, and test assignments. Use the
appropriate column when evaluating the corresponding split protocol. To run
the notebook-generator regression tests from the project root:

```powershell
python tests\test_dataset_generator_notebook.py
```

Keep generated directories such as `.gradle`, `build`, and `app/build`, plus
local configuration such as `local.properties`, out of commits.

## Contributing

Keep changes focused, add or update tests with behavior changes, and update
the relevant numbered guide when the workflow or architecture changes.
