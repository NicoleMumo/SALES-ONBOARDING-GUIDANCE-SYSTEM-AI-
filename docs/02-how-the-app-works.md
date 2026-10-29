# 02 - How the app works

SalesMesh is for companies whose sales agents sign up customers in the field,
for bank accounts or telecom services in Kenya. The problem it addresses is
inconsistent onboarding: different agents ask different questions, in different
orders, and make different mistakes. The app gives every agent the company's
standard form, checks each answer, and shows the company how consistently its
team follows the process.

## An agent's session

1. **Sign in.** The agent picks "Sales agent" and enters the company ID, email
   and password.
2. **Choose the customer.** The company's industry is fixed by the admin. The
   agent picks the customer type: banking individual or business; telecom
   mobile, broadband, TV, IoT or landline. That choice, plus the company's own
   settings, decides the form (the "playbook").
3. **Consent.** The agent explains to the customer why the information is
   collected, how document photos are used, the customer's rights, and how
   answers are kept, then ticks that the customer agreed.
4. **Fill in the form, section by section.** Banking sections: Customer,
   Identity, Financial, Declarations, Account. Telecom sections: Customer,
   Identity, Service, Activation. Questions a company added come last, under
   "Company questions". All questions of a section are shown together; required
   ones are marked `*`, the rest say "Optional". The step bar at the top shows
   which sections are done, started or need a fix, and the agent can move to
   any section at any time.
5. **Answers are checked when the agent leaves the box**, picks an option, ticks
   a box, or changes section, never while typing. A wrong answer shows its
   message under the box; a correct one shows a tick.
6. **Suggestions and warnings.** The AI's suggestion for the next question is
   marked "Suggested next" (with a link if it is in another section). If the
   session looks likely to be abandoned, an amber or red banner says so and
   suggests offering help. Both are advice; the agent decides.
7. **Review and submit.** The review page lists every answer by section, with an
   Edit button per section and a list of anything still missing. Submit stays
   off until every required answer is valid. The app shows "Onboarding
   complete" only after Firebase confirms the save.

Answers are saved as a draft while the agent works (about 0.7 seconds after the
last change). An unfinished session can be continued from **My sessions** on the
agent's home screen, and opens on the section where the agent stopped. The phone's
Back button goes back one step wherever the screen has a back arrow.

## An admin's pages

Admins see five pages in a bottom navigation bar:

- **Overview**: the share of completed sessions that followed the standard (see
  below), sessions started and finished, average time and progress, where
  unfinished sessions stop, a weekly trend, the most corrected questions, the
  questions most often answered out of order, and agents compared. Every chart
  has a one-line caption, and the weekly chart has a "Show numbers" table.
- **Agents**: invite agents by email, see whether each has registered, and turn
  an agent off or on.
- **Sessions**: search and filter all of the company's sessions and read their
  answers (read-only).
- **Questions**: add company questions (name, instructions, answer type, checks,
  choices) and choose which standard questions are required. Seven questions
  always stay required: full name, date of birth, ID type, ID number, politically
  exposed person status, US person status, and acceptance of the terms.
- **Company**: set the onboarding industry and see the company ID to give to
  agents.

## The consistency measure

A completed session **followed the standard** when at most 2 answers had to be
corrected and at most 2 questions were answered out of the standard order.

- **Corrected answers** count the checks that failed. Each saved check is one
  real attempt (the agent left the box), so a slow typist is not counted as
  making mistakes.
- **Questions out of order** is the fewest questions that would have to move to
  put the session in the playbook's order. In `a, d, b, c` only `d` is out of
  place, so the count is 1, not 3.
- Answering in a different order is not automatically wrong, and the agent may
  move freely between sections, so a few questions out of order is normal.
- The limits (2 and 2) come from the simulated data, where about 81% of completed
  sessions meet them. They should be checked again once real sessions exist.
- Sessions saved before answers were checked on leaving the box (marked by the
  absence of `answerChecks: "on_leave"`) are left out of this measure, because
  their saved checks include half-typed answers. The Overview says how many were
  left out. They still count in sessions started, finished and average time.

It is plain counting, not a model, so any number on the Overview can be checked
by hand. See [03 - AI, data and results](03-ai-data-and-results.md) for why a
model was tried and rejected for this.

## Accounts and companies

- A Firebase sign-in alone gives no access. Admins need the `admin` and
  `companyId` claims set by the project owner; agents need an invitation and an
  active membership of the company they type.
- Every failed step of a sign-in signs the person out again, so a half-finished
  sign-in never leaves a session open.
- Signing out clears everything from the session in memory (company, agent,
  answers of a resumed session, loaded settings), so the next person starts clean.
- Company data is stored under `companies/{companyId}/`; agent memberships under
  `companyMemberships/{uid}`.

## What is saved

Each session is stored at `companies/{companyId}/onboardingSessions/{sessionId}`:

| Data | What it holds |
| --- | --- |
| Session details | company and agent IDs, agent email, industry, customer type, status (`in_progress` or `completed`), start and finish times, `answerChecks: "on_leave"` |
| `playbookSnapshot` | a copy of the questions and rules used, so later setting changes do not alter old sessions |
| `entries/{fieldId}` | the answers: drafts while in progress, then only valid, non-empty answers when completed |
| `validation/{fieldId}` | the latest check result per question |
| `events` | one `field_validation_passed` or `field_validation_failed` record per attempt: event type, question ID and time, never the answer |
| `resumeFieldId`, `lastSavedAt` | where to continue an unfinished session |

Time spent per question and revisits feed the in-app risk warning. They are
worked out in memory and are not saved.

The ID photo reader (on the "Identification document" question) reads printed
text from a photo the agent chooses, on the phone, using Google ML Kit. It shows
the text for the agent to compare; it does not fill in answers, and neither the
photo nor the text is uploaded or saved. It is a typing aid, not identity
verification. The app has no KYC/AML service, selfie, liveness or face-match
check, and records no identity decision; the agent follows the company's own
manual checks.

## Access, keeping and deleting data

The intended access is: admins see their own company's data, and agents see
their own sessions. That depends on the deployed Firebase rules, which still need
testing (see [01 - Set up and run](01-set-up-and-run.md)).

The app does not delete sessions and sets no retention period. The Firebase SDK
keeps an offline copy of data on the phone, and the app allows Android backups
(`allowBackup`), so a phone backup may contain saved data. Before real customer
data is used, decide who may see it, how long it is kept, how it is deleted and
how backups are handled.

## Where things are in the code

All app code is in one package, `app/src/main/java/com/example/salesonboarding/`:

| File | What it does |
| --- | --- |
| `MainActivity.kt` | App start and which screen is shown. |
| `Auth.kt` | Sign-in and registration screens, and the Firebase account checks. |
| `AgentScreens.kt` | Agent home, customer choice, and consent. |
| `OnboardingForm.kt` | The agent's form: sections and step bar, review and finish pages. |
| `FormQuestions.kt` | Each kind of question input, format hints, and the ID photo reader. |
| `OnboardingController.kt` | Form state, checking answers when the agent leaves them, suggestions, progress, drafts, and saving. |
| `Fields.kt` | The question catalogue, admin rule changes, playbooks, and form sections. |
| `Validation.kt` | Answer checks and their messages. |
| `NextFieldPredictor.kt` | Runs the next-question model (TensorFlow Lite). |
| `Insights.kt` | The risk warning (trained model and hand-set fallback) and the consistency measure. |
| `FirebaseFieldEntryStore.kt` | Saves sessions and answers. |
| `SessionHistoryScreen.kt` | Session lists for agents and admins. |
| `AdminDataStore.kt` | Admin data: agents, question rules, company industry, overview numbers. |
| `AdminScreens.kt` | The admin pages behind the bottom navigation bar. |
| `AdminOverview.kt` | The admin overview and its charts. |
| `Ui.kt` | Colours (app, charts, sign-in), fonts, theme, and the shared building blocks. |

Android Studio previews show the sign-in screen (`Auth.kt`), the customer choice
(`AgentScreens.kt`), the agent form (`OnboardingForm.kt`), the admin overview
(`AdminOverview.kt`) and the shared building blocks in light and dark (`Ui.kt`)
without running the app.
