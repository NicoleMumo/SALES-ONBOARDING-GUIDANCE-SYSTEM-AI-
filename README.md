# SalesMesh

Help sales agents sign up customers the same way, every time.

An Android app for companies that onboard customers in the field (bank accounts
or telecom services in Kenya). Every agent gets the company's standard form,
each answer is checked, and admins can see how consistently the team follows it.

> **Status: research prototype.** The AI models were trained and tested on
> simulated sessions, not real customers. Do not use it with real customer data
> yet (see [Known limits](#known-limits)).

---

## Features

- **Same form for everyone:** each company sets its industry, its required questions, and its own extra questions
- **Answers checked as you go:** each answer is checked when the agent leaves the box, with a clear message if it is wrong
- **Suggested next question:** an AI model on the phone suggests what to ask next
- **Early warning:** a banner appears when a customer may not finish
- **Consent recorded:** the time and the agent are saved with each session
- **Drafts:** an unfinished session is saved and can be continued later
- **Team overview for admins:** how many sessions finished, followed the standard, and where agents get stuck

---

## How to Use

### Agents

1. Open the app, choose **Sales agent**, and sign in with the company ID, your email and password. New agent? Tap **Create account** (an admin must have invited your email first).
2. Tap **Start onboarding** and choose the customer type.
3. Explain the consent points to the customer and tick that they agreed.
4. Fill in the form section by section. Required questions have a `*`.
5. Check the **Review** page, fix anything marked, then tap **Submit**.

**Need to stop?** Your answers are saved. Open **My sessions** and tap **Continue** later.

### Admins

1. Choose **Administrator** and sign in.
2. **Company:** set the industry. Agents cannot sign in until you do. Copy the company ID and give it to your agents.
3. **Agents:** tap **Invite an agent** and add their name and work email.
4. **Questions:** choose which questions are required, or add your own.
5. **Overview** and **Sessions:** see how the team is doing and read every session.

---

## Troubleshooting

| Problem | What to do |
| --- | --- |
| "Agents cannot sign in" | The admin has not set the company's industry yet (Company page) |
| Agent cannot create an account | The admin must invite the same email first (Agents page) |
| Submit is greyed out | Some required answer is missing or wrong. The Review page lists them |
| Overview bar shows a dash | There is nothing to measure yet. It never shows an invented number |
| Build fails: `google-services.json` missing | Add your Firebase file (see Getting Started) |

---

## Getting Started (developers)

You need Android Studio (JDK 17), Python 3, and your own Firebase project.

1. Clone the project:

   ```bash
   git clone https://github.com/NicoleMumo/SALES-ONBOARDING-GUIDANCE-SYSTEM-AI-.git
   ```

2. Open the **`android`** folder in Android Studio (not the repo top folder) and let Gradle sync.
3. Add your Firebase settings (see [Configuration](#configuration)).
4. Run the checks:

   ```powershell
   cd android
   .\gradlew.bat :app:assembleDebug
   .\gradlew.bat :app:testDebugUnitTest
   cd ..
   python -m unittest discover -s tests
   ```

Never add Firebase service-account keys or real customer data to this repository.

### Configuration

| What | Where |
| --- | --- |
| Your Firebase file | Save it as `android/app/google-services.json`. It is private and not in Git. |
| Database address | Set in `MainActivity.kt` (`SalesMeshApplication`). Change it if your database has another URL. |
| Sign-in method | Turn on **Email/Password** in Firebase Authentication. |
| Database rules | Deploy `firebase/database.rules.json`. |
| First admin | A project owner gives the account its admin and company marks. Steps: [Set up and run](docs/01-set-up-and-run.md). |

---

## Project Layout

| Folder | What is in it |
| --- | --- |
| `android/` | The app (Kotlin, Jetpack Compose) and its build files |
| `ml/` | Python code that makes simulated data and trains the two AI models; `ml/models/` holds the trained files |
| `firebase/` | Database access rules and their test |
| `tests/` | Python tests for `ml/` |
| `docs/` | The guides |
| `legacy/` | Old files, kept for reference only |
| `.github/` | Automatic checks on every pull request |

`ml/` trains the models, the build copies them into `android/`, and the app
reads and writes its data through Firebase.

---

## Built With

| Technology | Purpose |
| --- | --- |
| Kotlin, Jetpack Compose | The Android app and its screens |
| Firebase Authentication | Sign-in with email and password |
| Firebase Realtime Database | Sessions, answers, agents, and settings (one branch per company) |
| TensorFlow Lite | Runs the next-question model on the phone |
| Google ML Kit | Reads text from an ID photo to help typing (on the phone) |
| Python | Simulated data and model training |
| GitHub Actions | Builds and tests every pull request |

---

## Results (simulated data)

| What | Result |
| --- | --- |
| Next-question suggestion | Right **95.0%** of the time, against 86.5% for a fixed rule |
| Risk warning | ROC-AUC **0.71 to 0.74**, against 0.55 to 0.59 for the old hand-set rule (0.5 is guessing) |
| Consistency measure | Plain counting, not AI: at most 2 corrected answers and 2 questions out of order |

How these were tested: [AI, data and results](docs/03-ai-data-and-results.md).

---

## Known limits

- Models were never tested on real agents or customers.
- The Firebase rules still need a full test and deployment before real data.
- Agent email verification is off for testing.
- Identity checks are manual: the app reads text from a document photo to help
  typing, but does no KYC, selfie or face-match check.
- No data-retention plan yet.

---

## Guides

1. [Set up and run](docs/01-set-up-and-run.md): Firebase, first admin and agent, building, testing
2. [How the app works](docs/02-how-the-app-works.md): screens, what is saved, where things are in the code
3. [AI, data and results](docs/03-ai-data-and-results.md): the data, the models, retraining
4. [System architecture](docs/04-system-architecture.md): the parts, the layers, what is stored and security, on one page
5. [Privacy note](docs/05-privacy-note.md): what is collected, who sees it, and the decisions still open

---

## Contributing

`main` is protected, so changes go through a pull request.

1. Make a branch, for example `fix/57-short-name` or `docs/59-short-name`.
2. Run the checks from Getting Started.
3. Open a pull request with a short, plain description of what changed.

## Support

Found a problem or have a question? [Open an issue](https://github.com/NicoleMumo/SALES-ONBOARDING-GUIDANCE-SYSTEM-AI-/issues).

## License

No licence has been chosen yet, so all rights are reserved. The Jost font is used
under the SIL Open Font License 1.1.

---

## Credits

- Sign-in photo: "Minimalist wooden desk and laptop with empty chair", Kaboompics.
- Font: Jost, SIL Open Font License 1.1 (licence text in `android/app/src/main/assets/licenses/`).
