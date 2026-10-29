# 01 - Understand the app

SalesMesh helps an agent fill in a customer onboarding form. It checks each
answer, suggests a useful next field, and saves the finished form to the
company's Firebase database.

## How a session works

1. The agent signs in and chooses a customer type.
2. The app creates a form from the company's industry, customer type, and
   company settings.
3. The agent confirms consent before entering personal information.
4. The app checks answers as the agent types.
5. The app may suggest another field. The agent can still choose a different
   field.
6. The app allows submission only when required answers are valid. Empty
   optional answers are left out.
7. The app saves the answers and marks the session complete. It shows success
   only after Firebase confirms the save.

The model only suggests what to fill in next. It does not check answers,
decide which fields are required, or approve a submission.

## Accounts and companies

Firebase signs users in, but a sign-in alone does not grant a role.

- A trusted Firebase owner gives an admin the claims `admin: true` and
  `companyId: "<company-id>"`. Registering in the app does not make someone an
  admin.
- An admin invites agents by email. The app checks the invitation and company
  membership when an agent joins.
- Company data is stored under `companies/{companyId}/`.
- Agent membership is stored under `companyMemberships/{uid}`.
- Agent email verification is turned off in the current app for testing. Turn
  it back on and test it before production use.

See [02 - Connect Firebase](02-connect-firebase.md) for setup and access checks.

## Main code files

| File or folder | What it does |
| --- | --- |
| `MainActivity.kt` | Sign-in, account checks, and screen routing. |
| `OnboardingPlaybook.kt` | Chooses fields for an industry and customer type. |
| `validation/` | Field definitions and answer checks. |
| `OnboardingFormController.kt` | Connects form state, suggestions, progress, and saving. |
| `NextFieldPredictor.kt` | Runs the TensorFlow Lite model. |
| `OnboardingScreen.kt` | Shows the form, checks, progress, and suggestions. |
| `FirebaseFieldEntryStore.kt` | Saves sessions and answers to Firebase. |
| `AdminDataStore.kt` and `AdminDashboardScreen.kt` | Admin settings and agent invitations. |
| `SessionHistoryScreen.kt` | Shows saved session history. |

Open the project root in Android Studio. The app is in `app/`. Python data
tools, model code and the training notebook are in `ml/`.

The model files and their settings are in `ml/models/`. The Gradle task
`syncOnboardingModelAssets` copies the next-field model, its settings and the
risk model into the app. Keep the model and settings together.
