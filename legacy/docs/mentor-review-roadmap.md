# Archived mentor roadmap

This is the original detailed roadmap, kept for history. Its status and file
locations may be out of date. Use [`../../docs/03-ai-data-and-results.md`](../../docs/03-ai-data-and-results.md)
for current results and what is not yet shown, and [`../../docs/02-how-the-app-works.md`](../../docs/02-how-the-app-works.md)
for the current app description.

---

# SalesMesh project roadmap

This roadmap arranges the mentor review's recommendations into exactly four
chore-sized work items: **dataset, model, app, and database**. Each chore lists
its objective, work, files, and completion criteria. The review examined
`main` at commit `8f94d62` from 13 October 2026 and did not execute the project.
Treat its findings as items to verify against the current branch, not as
confirmed problems in the current code.

## Shared first step — verify the current state

Before starting the four chores, check the current branch for:

- Authentication and admin-role enforcement.
- Firebase configuration and database rules.
- Whether event records contain customer-entered values.
- Industry/context fields, model input history, and validation state.
- Production callers of risk scoring and the risk-duration calculation.
- Existing playbooks, tests, and changes that may already address the report.

Mark each item **confirmed**, **partly addressed**, **not present**, or **not
yet verified**. This avoids duplicating work based on the older snapshot. This
verification is a short prerequisite, not a fifth chore.

### Verification against the current checkout (18 October 2026)

| Area | Status | Finding |
|---|---|---|
| Dataset seed and grouped splits | **confirmed addressed** | The notebook generator accepts a seed and writes session-level `split` plus agent-level `agent_split`; the generator regression tests pass. |
| Authentication and admin role | **partly addressed** | Firebase email/password sign-in and a trusted `admin` token-claim check are now in the app. Auth account provisioning and a real admin claim still require a secured Firebase Admin SDK process. |
| Firebase rules and project configuration | **partly addressed** | Default-deny rules, emulator config, and allowed/denied rules tests are in the repository; rules have not been run in the emulator or deployed. |
| Customer values in events | **partly addressed** | The old event store persisted only a presence flag, not the value itself. The event API now has no value argument or presence flag. Submitted values remain in the session record. |
| Industry/context and model inputs | **partly addressed** | The app now asks for context, builds fields in generator order, records settled entry order including retries, and passes actual validation state. Android compilation is not yet verified. |
| Risk scoring | **partly addressed** | A live validation callback now creates per-field risk events, and both scorers count individually long events. Updated metrics have not yet been generated. |
| Existing playbooks and tests | **confirmed present** | Industry playbooks and JVM tests existed. Context-specific playbooks and regression tests have been added; the Android suite could not be run in this environment. |

### Work completed in this checkout

- **Chore 1 — dataset:** already complete on `chore/dataset-reproducibility`.
- **Chore 2 — model and risk:** per-event duration is fixed and baseline/risk evaluation commands are added. Results still need to be generated, the model retrained for `agent_split`, and unseen-field and physical-device evaluations remain open.
- **Chore 3 — Android app:** workflow/context, event privacy, consent, risk wiring, and graceful model fallback are implemented with unit-test coverage added. End-to-end emulator flow and a verified Android build remain open.
- **Chore 4 — database and authentication:** sign-in, claim-gated role routing, UID ownership rules, and emulator tests are added. Claim provisioning, emulator execution, and deployment remain open.

Changes are currently uncommitted in the dataset branch's working tree because
the other chore branches are checked out in linked worktrees outside the
writable project directory. Keep the changes separated into the prescribed
branches and commits before opening PRs.

## Chore 1 — Make dataset generation reproducible

**Goal:** Make dataset experiments repeatable and clarify what synthetic data
can—and cannot—prove.

### Do first

- Add a configurable random seed to dataset generation.
- Write the train/validation/test split into generated output and preserve
  session-level separation.
- Add an agent-level split so sessions from one agent do not appear in both
  training and test data.
- Document that the data is synthetic and scenario-generated; do not present
  its results as evidence of real-world performance.

### Improve after establishing baselines

- Introduce realistic variation such as agent-specific field order, optional
  fields, skipped fields, retries, and out-of-order entries.
- Keep generated scenarios and labels explicit so evaluation can explain how
  each example was produced.

**Complete when:** the dataset can be regenerated from a documented command
and seed, and the output includes reproducible split assignments.

### Files in scope

- `notebooks/onboarding_dataset_generator.ipynb` — sole synthetic-data
  generator; keep the implementation and configuration here.
- `data/onboarding_dataset.csv` — generated dataset; regenerate rather than
  hand-edit.
- `data/vocabulary/field_vocabulary.json` — shared field vocabulary.
- `notebooks/onboarding_transformer.ipynb` — data preparation, training, and
  exploratory evaluation; keep the reproducible pipeline in scripts where
  practical.
- `docs/04-training-data-and-ai.md` — dataset schema and field
  meanings.
- `docs/common-field-vocabulary.md` — field ID conventions.
- `docs/industry-onboarding-sequences.md` and
  `docs/industry-onboarding-workflows.md` — expected workflow sequences that
  generated examples should reflect.
- `legacy/data_pipeline/README.md`,
  `legacy/data_pipeline/synthetic_session_generator.py`,
  `legacy/data_pipeline/clean_and_validate_dataset.py`,
  `legacy/data_pipeline/encode_onboarding_fields.py`,
  `legacy/data_pipeline/prepare_sequence_data.py`,
  `legacy/data_pipeline/prepare_transformer_inputs.py`, and
  `legacy/data_pipeline/split_dataset.py` — archived pipeline references only;
  do not treat these scripts as the current source.
- `tests/test_dataset_generator_notebook.py` — regression tests execute the
  notebook's generator cells directly; tests do not provide a second
  generator implementation.
- **New files to add if needed:** pinned Python dependencies in
  `requirements.txt` and additional Python tests under `tests/`. Do not add a
  separate `.py` dataset generator; the notebook is the source of truth.

## Chore 2 — Validate model and risk value

**Goal:** Evaluate whether the Transformer and risk logic add value over
simpler alternatives before making stronger claims about their performance.

### Next-field prediction

- Compare the Transformer with canonical workflow order, a majority lookup,
  and an n-gram/Markov baseline.
- Report top-1 and top-3 accuracy and macro-F1. Include retry/revisit cases
  separately so performance on non-trivial steps is visible.
- Evaluate with both session-level and agent-level splits.
- Test the “new field without retraining” claim with multiple held-out fields,
  not just one example.

### Completion risk

- Check that a per-event duration threshold uses individual event durations,
  not total session duration. Keep the Python and Kotlin scoring behavior in
  agreement.
- Re-run evaluation after any correction. Report a confusion matrix, PR-AUC,
  recall at a stated precision, and calibration.
- Explain that scenario-derived features and labels make the evaluation
  synthetic and potentially circular; do not imply validation on real customer
  sessions.

### Evidence and reproducibility

- Save evaluation outputs in a machine-readable results file and summarize
  them in the model documentation.
- Record the random seeds and split used for each result.
- Measure inference on a named physical Android device and report median and
  p95 latency.

**Complete when:** results are reproducible from the documented dataset and
evaluation commands, include simple baselines, and state limitations clearly.

### Remaining work and deferred validation

**Finish the current synthetic evaluation**

- [ ] In Google Colab, open
  `notebooks/onboarding_transformer.ipynb` from branch
  `chore/model-risk-evaluation-pr` and upload the refreshed
  `data/onboarding_dataset.csv` as `onboarding_dataset.csv`.
- [ ] Run the notebook from the top through section 11. Confirm the five
  unseen-field scores are finite and save the output. These scores show only
  that descriptions can be scored; they are not accuracy evidence.
- [ ] For the second split, set `SPLIT_COLUMN = "agent_split"`, restart the
  Colab runtime, and run through section 11 again.
- [ ] Review and submit the model-evaluation branch as its own pull request.
  Review and submit `fix/per-event-risk-duration` separately; do not combine
  the risk bug fix with the model-evaluation chore.
- [ ] Measure inference on a physical Android device. Emulator timings are
  useful for a smoke check but do not complete the physical-device measurement.

**Deferred — real-world validation (requires suitable data)**

- [ ] Obtain approved real onboarding sessions with trustworthy completion or
  abandonment outcomes and privacy review. The synthetic dataset cannot
  provide this evidence.
- [ ] Agree on the outcome definition, choose any risk threshold using a
  validation set, and report final metrics on a separate untouched test set.
- [ ] Keep real-world performance claims and production decisions on hold
  until this validation is complete. Do not tune against the test set.

### Files in scope

- `src/model_def.py` and `src/field_similarity_head.py` — model and field
  similarity head.
- `src/evaluate_completion_risk.py` — Python risk evaluation and duration
  features.
- `src/demo.py` — existing Python model/demo entry point.
- `notebooks/onboarding_transformer.ipynb` — existing training/export and
  evaluation notebook.
- `src/models/onboarding_model_flexible.keras`,
  `src/models/onboarding_model.tflite`, and
  `src/models/preprocessing_meta.json` — current model artifacts and metadata.
- `app/src/main/assets/onboarding_model.tflite` and
  `app/src/main/assets/preprocessing_meta.json` — Android copies; keep these
  synchronized with the chosen canonical artifacts.
- `app/src/main/java/com/example/salesonboarding/NextFieldPredictor.kt` —
  Android model adapter.
- `app/src/main/java/com/example/salesonboarding/risk/CompletionRisk.kt`,
  `CompletionRiskScorer.kt`, and `CompletionRiskGuidance.kt` — Kotlin risk
  event, scoring, and user-facing guidance logic.
- `app/src/test/java/com/example/salesonboarding/NextFieldPredictorTest.kt`,
  `app/src/test/java/com/example/salesonboarding/risk/CompletionRiskTest.kt`,
  and
  `app/src/test/java/com/example/salesonboarding/risk/CompletionRiskGuidanceTest.kt`
  — model-adapter and risk regression tests.
- `app/src/androidTest/java/com/example/salesonboarding/TfliteNextFieldPredictorBenchmark.kt`
  — on-device inference benchmark.
- `docs/transformer-task.md`, `docs/transformer-evaluation-summary.md`,
  `docs/MODEL_README.md` and `docs/04-training-data-and-ai.md` — task, model,
  risk, and performance
  evidence to update.
- **New files to add if needed:** baseline/evaluation scripts such as
  `src/evaluate_next_field.py`, a machine-readable `results/metrics.json`,
  pinned dependencies in `requirements.txt`, and Python tests under `tests/`.
  These do not currently exist at the repository root.

## Chore 3 — Connect and test the Android app

**Goal:** Make the app use the correct workflow, protect customer information,
and provide a repeatable build and test path.

### Workflow and AI integration

- Build the ordered field list from the selected industry and customer context;
  use field IDs that match the model vocabulary.
- Add or verify context selection where the workflow supports multiple
  contexts.
- Track actual entry order, retries, and revisits. Pass the real last
  validation result to the predictor.
- Connect risk scoring to live onboarding events using per-step duration,
  validation outcome, revisit state, and completion progress.
- Keep deterministic workflow guidance as a fallback when a prediction is
  unavailable or invalid.

### Privacy and account experience

- Remove customer-entered values from analytics and audit events. Keep
  necessary values in the intended onboarding record, not in per-keystroke
  event logs.
- Explain consent, purpose, and data handling before collecting sensitive
  onboarding information.
- Use the authenticated identity and trusted authorization result when
  selecting agent or admin experiences; a client-side role choice must not
  grant admin access.

### Tests and maintainability

- Add tests proving representative banking and telecom sessions pass the
  expected field sequence, context, retries, and validation state to the
  predictor.
- Test that risk guidance can be reached from a realistic session and that
  model/Firebase failures are visible and recoverable.
- Add a repeatable end-to-end agent-flow test using the Firebase Emulator
  Suite.
- Add CI for Android unit tests and Python checks.
- Consider Navigation Compose and screen-level ViewModels after security and
  workflow correctness are stable; avoid a broad rewrite before then.
- Expand `README.md` with a quick start, a concise explanation of where AI is
  used, a results summary, and a demo screenshot.
- Complete `.gitignore` coverage for local build, IDE, Python cache, and
  notebook checkpoint files. Add the agreed project licence.

**Complete when:** the real app flow, predictor inputs, risk events, and error
states are covered by automated tests and do not leak field values into logs;
automated checks pass from a clean checkout.

### Files in scope

- `app/src/main/java/com/example/salesonboarding/MainActivity.kt` —
  application flow and screen selection.
- `app/src/main/java/com/example/salesonboarding/OnboardingPlaybook.kt`,
  `OnboardingScreen.kt`, and `OnboardingFormController.kt` — workflow
  selection, onboarding UI, form state, validation, persistence, model inputs,
  and event handling.
- `app/src/main/java/com/example/salesonboarding/IndustrySelectionScreen.kt`,
  `AgentHomeScreen.kt`, and `LoginScreen.kt` — agent entry flow,
  industry/context selection, and sign-in UI.
- `app/src/main/java/com/example/salesonboarding/AdminDashboardScreen.kt`
  and `AdminDataStore.kt` — admin interface, agent/profile management,
  configurable field rules, and admin data access.
- `app/src/main/java/com/example/salesonboarding/FirebaseFieldEntryStore.kt`
  and `SalesMeshApplication.kt` — session storage and Firebase application
  setup.
- `app/src/main/java/com/example/salesonboarding/NextFieldPredictor.kt` and
  `app/src/main/java/com/example/salesonboarding/risk/CompletionRisk.kt`,
  `app/src/main/java/com/example/salesonboarding/risk/CompletionRiskScorer.kt`,
  and
  `app/src/main/java/com/example/salesonboarding/risk/CompletionRiskGuidance.kt`
  — app-side AI and risk behavior.
- `app/src/main/java/com/example/salesonboarding/validation/FieldRuleConfiguration.kt`,
  `KnownFields.kt`, `LiveValidationSession.kt`, `ValidationErrorDisplay.kt`,
  and `ValidationModels.kt` — app validation and field configuration.
- `app/src/main/java/com/example/salesonboarding/ui/theme/Theme.kt` — shared
  Compose theme used across app screens.
- `app/src/test/java/com/example/salesonboarding/AdminDataStoreTest.kt`,
  `IndustrySelectionValidatorTest.kt`, `LoginFormValidatorTest.kt`,
  `NextFieldPredictorTest.kt`, `OnboardingFormControllerTest.kt`, and
  `OnboardingPlaybookTest.kt` — files in
  `app/src/test/java/com/example/salesonboarding/`; existing data-store, flow,
  form, and predictor unit tests.
- `app/src/test/java/com/example/salesonboarding/risk/CompletionRiskGuidanceTest.kt`
  and
  `app/src/test/java/com/example/salesonboarding/risk/CompletionRiskTest.kt`
  — risk unit tests.
- `app/src/test/java/com/example/salesonboarding/validation/LiveValidationSessionTest.kt`,
  `app/src/test/java/com/example/salesonboarding/validation/ValidationErrorDisplayTest.kt`,
  and
  `app/src/test/java/com/example/salesonboarding/validation/ValidationRulesTest.kt`
  — validation unit tests.
- `app/src/androidTest/java/com/example/salesonboarding/TfliteNextFieldPredictorBenchmark.kt`
  — device benchmark.
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`,
  `app/src/main/res/values/colors.xml`,
  `app/src/main/res/values/themes.xml`, and `app/build.gradle.kts` — Android
  app configuration, resources, and dependencies.
- `docs/01-understand-the-app.md`, `docs/onboarding-process.md`,
  `docs/03-forms-and-saved-data.md`,
  `docs/onboarding-fields-requirements.md`, and
  `docs/industry-onboarding-workflows.md` — app behavior and data contracts
  to keep aligned with implementation.
- **New files to add if needed:** an app-side auth/repository or screen
  ViewModels only if required by the selected design; Firebase emulator
  integration tests under `app/src/androidTest/` or a dedicated rules-test
  directory. These do not currently exist.
- **Additional files:** `README.md` and `.gitignore` (existing);
  `.github/workflows/ci.yml` and a licence file (to add; CI does not currently
  exist at the repository root).

## Chore 4 — Secure database and authentication

**Goal:** Enforce user identity, admin privileges, and record ownership.
**Blocking dependency:** Complete and verify this chore before using real
customer information.

- Integrate Firebase Authentication for account sign-in instead of accepting
  locally validated credentials.
- Determine admin status from a trusted mechanism, such as an
  administrator-managed custom claim. Do not trust a role button or a
  client-editable profile field.
- Add version-controlled Realtime Database security rules and Firebase CLI
  configuration.
- Restrict agent access to that agent's sessions and restrict agent/profile
  management and rule changes to authorized admins.
- Store the authenticated agent UID on each session so ownership can be
  enforced by rules.
- Test permitted and denied reads/writes with the Firebase Emulator Suite
  before deploying rules.
- Document what sensitive information is stored, why it is needed, who can
  access it, and how long it is retained.

**Complete when:** unauthenticated users cannot read or write protected
records, agents cannot access another agent's sessions or admin functions,
admin privileges are server-enforced, and emulator rule tests pass.

### Files in scope

- `app/src/main/java/com/example/salesonboarding/FirebaseFieldEntryStore.kt`
  — onboarding session and event reads/writes.
- `app/src/main/java/com/example/salesonboarding/AdminDataStore.kt` — agent,
  field-rule, and admin data access.
- `app/src/main/java/com/example/salesonboarding/LoginScreen.kt` and
  `MainActivity.kt` — sign-in integration and trusted role-based routing.
- `app/src/main/java/com/example/salesonboarding/AdminDashboardScreen.kt` —
  admin-only actions and screens.
- `app/src/main/java/com/example/salesonboarding/OnboardingFormController.kt`
  — session identity and event payloads; ensure event logging excludes field
  values.
- `app/src/main/java/com/example/salesonboarding/SalesMeshApplication.kt` —
  Firebase initialization and database behavior.
- `app/build.gradle.kts` — Firebase Authentication dependency/configuration
  if it is not already present.
- `docs/02-connect-firebase.md`, `docs/03-forms-and-saved-data.md`, and
  `docs/01-understand-the-app.md` — setup, saved data, and
  authentication status documentation.
- **New files to add:** `database.rules.json` for Realtime Database rules,
  `firebase.json` for Firebase CLI/emulator configuration, and emulator rules
  tests (for example, `database.rules.test.js` with the required Node test
  manifest/configuration). These files are not currently present at the
  repository root. Add a trusted admin-claim provisioning procedure using a
  secured Admin SDK environment; never place service-account credentials in
  the Android app or repository.

- **Repository hygiene:** Keep one canonical source for model and
  preprocessing assets, or generate/copy app assets from it with a documented
  build task. Document known limitations, privacy/data handling, and measured
  device latency honestly. These are included in Chores 2 and 3, not separate
  work items.

## Branch and change conventions

No GitHub issues are required for this work. Implement each roadmap chore on
its own branch, using the existing `type/short-description` branch style:

| Roadmap work | Branch |
| --- | --- |
| Chore 1 — dataset | `chore/dataset-reproducibility` |
| Chore 2 — model and risk evaluation | `chore/model-risk-evaluation` |
| Chore 3 — Android app | `chore/android-app-integration` |
| Chore 4 — database and authentication | `chore/database-authentication` |

Keep different kinds of changes separate from those chore branches:

- `style/<short-description>` with `style:` commits for formatting or visual
  styling only; do not include behavior changes.
- `fix/<short-description>` with `fix:` commits for bug fixes; keep these
  separate from formatting-only changes and unrelated chore work.
- Use `chore:` commit messages for maintenance and roadmap work that does not
  add a user-facing feature or fix a bug.

For example, a visual polish pass belongs on `style/admin-dashboard` with
`style:` commits. A broken admin save behavior belongs on
`fix/admin-field-save` with `fix:` commits, not on the style branch. If either
change is discovered while working on a chore, leave it out of that chore
branch and handle it separately.

The four chore sections are the complete roadmap breakdown; shared current
state verification is a prerequisite, not a fifth chore. Dataset and model
work can use synthetic data independently. Chore 4 must be completed before
any real customer data is used. Chore 3's Firebase end-to-end test depends on
the emulator/rules setup in Chore 4.
