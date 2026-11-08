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
3. Download `google-services.json` and save it as `android/app/google-services.json`.
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

### Optional: Google and Facebook sign-in (both free)

Each button appears on the sign-in screen only once it is set up. Both are free
on Firebase's no-cost Spark plan. To stay free, do **not** upgrade to "Firebase
Authentication with Identity Platform", and do **not** turn on phone (SMS)
sign-in, which is billed per message. Sign in with Apple is not included: it
needs the paid Apple Developer Program (US$99 a year).

**Google**

1. Firebase console > Authentication > Sign-in method > **Google** > Enable.
2. Project settings > your Android app > **Add fingerprint**: add the SHA-1 of
   the debug key (`.\gradlew.bat :app:signingReport` from `android/`) and of the
   release key (`keytool -list -v -keystore %USERPROFILE%\.salesmesh-signing\salesmesh-release.jks -alias salesmesh`).
3. Download `google-services.json` again (it now holds Google's client ID),
   replace `android/app/google-services.json`, and rebuild.

**Facebook**

1. At developers.facebook.com, create an app with **Facebook Login** (free).
   Under Facebook Login > Android, enter the package
   `com.example.salesonboarding`, the class
   `com.example.salesonboarding.MainActivity`, and the key hashes of the debug
   and release keys.
2. Firebase console > Authentication > Sign-in method > **Facebook** > Enable,
   with the Facebook app ID and app secret. Copy the redirect URI Firebase shows
   into Facebook Login > Settings > Valid OAuth Redirect URIs.
3. Put the app ID and client token (Facebook: Settings > Advanced) in
   `android/local.properties`, which is not in Git, then rebuild:

   ```properties
   facebook.appId=[your Facebook app ID]
   facebook.clientToken=[your Facebook client token]
   ```

While the Facebook app is in Development mode, only people given a role on it
(admins, developers, testers) can log in. Making it Live needs a privacy policy
web address. Facebook's automatic usage reports, advertising ID and install
tracking are switched off in the app's manifest.

**Who can get in.** A Google or Facebook account works exactly like a password
account: an agent's email must match an active invitation (checked by the
database rules), and an admin needs the custom claims below. A new account
whose email was not invited is deleted again straight away.

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
   Instead of a password, the agent can enter the company ID and tap
   **Continue with Google** or **Continue with Facebook**, if that account
   uses the invited email.

Agents must verify their email (`REQUIRE_AGENT_EMAIL_VERIFICATION` in
`Auth.kt`, and `auth.token.email_verified` in the database rules for joining a
company, saving sessions and sharing training steps). A new password account
gets a verification link; an unverified agent who signs in is sent a new link
and signed out until they open it. Google accounts are already verified.
Facebook accounts are not, so an invited agent using Facebook is sent a link
the first time. This stops anyone registering with an invited email they do not
own. Agents who registered before this was on are asked to verify at their next
sign-in.

The current sign-in screen shows **Register** only when Administrator is
selected. The agent registration screen is still in the app, but there is no
button to it from the sign-in screen.

## 4. Build and install

Open the **android** folder (not the top folder) in Android Studio, let Gradle
sync, then run the app on an emulator or phone. From PowerShell:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

The build copies the model files from `ml/models/` into the app (Gradle task
`syncOnboardingModelAssets`). Edit the files in `ml/models/`, not the copies in
`android/app/src/main/assets/`.

### Signed release APK (to share the app)

The app is shared as a signed release APK, not through Google Play. The signing
key stays outside the repository, in your home folder, so it can never be
committed:

```powershell
mkdir $HOME\.salesmesh-signing
keytool -genkeypair -keystore $HOME\.salesmesh-signing\salesmesh-release.jks `
  -storetype PKCS12 -alias salesmesh -keyalg RSA -keysize 2048 -validity 10000 `
  -dname "CN=SalesMesh"
```

Then create `$HOME\.salesmesh-signing\keystore.properties`:

```properties
storeFile=C:/Users/<you>/.salesmesh-signing/salesmesh-release.jks
storePassword=<the password you chose>
keyAlias=salesmesh
keyPassword=<the password you chose>
```

Build it (the release check needs Java 17 or 21; it fails on Java 25):

```powershell
cd android
.\gradlew.bat :app:assembleRelease "-Dorg.gradle.java.home=C:\Program Files\Java\jdk-21"
```

The APK is `android/app/build/outputs/apk/release/app-release.apk`. Without
`keystore.properties` the release build is simply left unsigned. Keep a backup
of the `.salesmesh-signing` folder: an update must be signed with the same key.
A phone that has the debug build installed must uninstall it first, because the
two builds are signed with different keys.

If Gradle cannot download on Windows because of certificate errors (PKIX),
add `"-Djavax.net.ssl.trustStoreType=Windows-ROOT"` to the command.

## 5. Run the tests

Android unit tests (100 tests), from the `android` folder:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Python tests (43 tests; plain Python, no extra packages):

```powershell
python -m unittest discover -s tests
```

The Python tests check the simulated-data generator, the consistency measure
and the risk-model features (including the extra inputs used for the F1 ceiling
check), and that the Python copy of the app's question order still matches the
Kotlin source.

Continuous integration (`.github/workflows/ci.yml`) runs on every push and pull
request: it compiles the Python code, runs the Python tests, runs the Android unit
tests, and runs the Firebase rules tests (see section 6).

## 6. Check access before real data

The rules file existing does not mean the rules are deployed. The rules are
tested automatically against the Firebase Emulator, with two companies, each with
an admin and agents. The 43 tests check that:

- Company A cannot read or change anything of Company B.
- An agent cannot read another agent's sessions.
- Only an invited email can join a company, and disabled invitations and
  memberships stop working.
- Agents can only create and update their own sessions, and only while a
  session is in progress.

- Signed-out visitors can do nothing.
- An admin can change only their own company's settings, questions and
  invitations, and the industry can only be banking or telecom.
- A deactivated agent loses access to settings and sessions.
- A completed session cannot be changed, and a session cannot change owner or
  company.
- Only a company's admin can turn on sharing anonymised steps. Then only its
  active agents can add copies to `trainingData/`, only in the expected shape (no
  names or extra fields), and nobody can read or delete them through the app.

To run them yourself, from the `firebase/` folder (needs Node 20 and Java 21):

```powershell
npm install
npm run test:database-rules
```

The tests are in `firebase/database.rules.test.js`. They passed here, and they
fail when a rule is deliberately weakened (checked by hand). Passing tests do not
deploy anything: you still need to deploy `database.rules.json` to your own
Firebase project.

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
