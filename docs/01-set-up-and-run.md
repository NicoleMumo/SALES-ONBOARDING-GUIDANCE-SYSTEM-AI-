# 01 - Set up and run

This guide gets SalesMesh running: Firebase, the first admin and agent, the
build, and the tests. Finish the access checks at the end before any real
customer information is used.

## What you need

- Android Studio with JDK 17. The app targets Android 15 (API 35) and runs on
  Android 7.0 (API 24) or later.
- A Firebase project (free plan is enough).
- Python 3.11 or later for the Python tests. Training the models needs more
  packages; see [03 - AI, data and results](03-ai-data-and-results.md).

## 1. Connect Firebase

1. Create or choose a Firebase project.
2. Add an Android app with the package name `com.example.salesonboarding`.
3. Download `google-services.json` and save it as `app/google-services.json`.
   This file is private to your project and is not in Git.
4. In Firebase Authentication, turn on **Email/Password** sign-in.
5. Create a Realtime Database. The app uses this URL, set in `MainActivity.kt`
   (`SalesMeshApplication`):

   ```text
   https://salesmesh-default-rtdb.asia-southeast1.firebasedatabase.app
   ```

   If your database has another URL, change it there.
6. Deploy the rules in [database.rules.json](../firebase/database.rules.json) to the
   database (see "Check access" below before relying on them).

Never put Firebase service-account keys in the app or in this repository.

## 2. Create the first admin

Signing up in the app does not make anyone an admin. A trusted Firebase project
owner gives the account two custom claims with the Firebase Admin SDK:

```javascript
await getAuth().setCustomUserClaims(userUid, {
  admin: true,
  companyId: "acme_finance",
});
```

1. The admin registers in the app (Administrator > Register) and verifies
   their email.
2. The project owner sets the claims above. Company IDs are 3 to 64 letters,
   digits, `_` or `-`, and should never change.
3. The admin signs in again, opens **Company**, and sets the company's
   onboarding industry (banking or telecommunications). Agents cannot sign in
   until it is set.

## 3. Invite an agent

1. The admin opens **Agents** and adds the agent's name and work email.
2. The admin gives the agent the company ID.
3. The agent creates an account with that email and company ID, then signs in.
   On first sign-in the app checks the invitation and makes them a member.

Agent email verification is turned off while the app is being tested
(`REQUIRE_AGENT_EMAIL_VERIFICATION` in `Auth.kt`). Turn it on, and require
`auth.token.email_verified` in the database rules for agent writes, before real
customer data is used. Until then, someone could register with an invited email
they do not own.

The current sign-in screen shows **Register** only when Administrator is
selected. The agent registration screen is still in the app, but there is no
button to it from the sign-in screen.

## 4. Build and install

Open the project folder in Android Studio, let Gradle sync, then run the app on
an emulator or phone. From PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

The build copies the model files from `ml/models/` into the app (Gradle task
`syncOnboardingModelAssets`). Edit the files in `ml/models/`, not the copies in
`app/src/main/assets/`.

## 5. Run the tests

Android unit tests (99 tests):

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Python tests (36 tests; plain Python, no extra packages):

```powershell
python -m unittest discover -s tests
```

The Python tests check the simulated-data generator, the consistency measure
and the risk-model features, and that the Python copy of the app's question
order still matches the Kotlin source.

Continuous integration (`.github/workflows/ci.yml`) compiles the Python code and
runs the Android unit tests on every push. It does not run the Python tests yet.

## 6. Check access before real data

The rules file existing does not mean the rules are deployed or correct. Test
them with the Firebase Emulator or a separate test project, with at least two
companies, each with an admin and an agent. Check that:

- Company A cannot read or change anything of Company B.
- An agent cannot read another agent's sessions.
- Only an invited email can join a company, and disabled invitations and
  memberships stop working.
- Agents can only create and update their own sessions, and only while a
  session is in progress.

`firebase/database.rules.test.js` (run from the `firebase/` folder with
`npm run test:database-rules`, which needs Node and the Firebase CLI) is an
early test file. It checks an older database
layout with paths at the root (`onboardingSessions/`, `salesAgents/`), not the
current `companies/{companyId}/` layout, so it does not test the current rules
and two of its three checks would fail. It needs to be rewritten for the current
layout and the checks above.

## Common problems

- **Admin access denied:** check the verified email, both claims, the company
  ID, the Firebase project, and that the admin signed in again after the claims
  changed.
- **Agent access denied:** check the invitation email, the company ID the agent
  typed, the membership, and the deployed rules.
- **"Your company administrator must choose an onboarding industry":** the admin
  has not set the industry on the **Company** page.
- **"Firebase field settings could not be loaded":** the form opens with the
  standard questions; company questions and rule changes are missing until the
  database can be reached.
- **Session save failed:** check the connection and the Firebase error shown.
  The app shows "Onboarding complete" only after Firebase confirms the save.
