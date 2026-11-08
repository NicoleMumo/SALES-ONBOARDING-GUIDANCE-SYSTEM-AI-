# 02 - How the app works

SalesMesh is for companies whose sales agents sign up customers in the field,
for bank accounts or telecom services in Kenya. The problem it addresses is
inconsistent onboarding: different agents ask different questions, in different
orders, and make different mistakes. The app gives every agent the company's
standard form, checks each answer, and shows the company how consistently its
team follows the process.

## An agent's session

1. **Sign in.** The agent picks "Sales agent" and enters the company ID, email
   and password. **Forgot password?** sends a Firebase reset link to the email
   typed; the app gives the same reply whether or not that email has an account.
   **Continue with Google** signs in without a password, but only if that
   Google account's email was invited by the company's
   admin (the same database rules as for passwords decide). The first time, the
   agent enters the company ID; after that it can be left empty. An uninvited
   account is deleted again at once. The button appears only when it is set up
   (see [01-set-up-and-run.md](01-set-up-and-run.md)). An admin can also use
   Google, once the Firebase owner has given that account admin
   access.
2. **Choose the customer.** The company's industry is fixed by the admin. The
   agent picks the customer type: banking individual or business; telecom
   mobile, broadband, TV, IoT or landline. The type chosen last time is already
   selected. That choice, plus the company's own settings, decides the form (the
   "playbook").
3. **Consent.** The agent explains to the customer why the information is
   collected, how document photos are used, the customer's rights, and how
   answers are kept, then ticks that the customer agreed. If the company allows
   it (Company page), a second, optional tick asks whether anonymised steps may
   be used to improve the system; it starts unticked and saying no changes
   nothing else.
4. **Fill in the form, section by section.** Banking sections: Customer,
   Identity, Financial, Declarations, Account. Telecom sections: Customer,
   Identity, Service, Activation. Questions a company added come last, under
   "Company questions". All questions of a section are shown together; required
   ones are marked `*`, the rest say "Optional". The step bar at the top shows
   which sections are done, started or need a fix, and the agent can move to
   any section at any time.
5. **Answers are checked when the agent leaves the box**, picks an option, ticks
   a box, or changes section, never while typing. A wrong answer shows its
   message under the box; a correct one shows a tick. Typing aids: a date is
   never typed. Tapping its box (or pressing Enter on it) opens a calendar, and
   tapping a day picks it, checks it and moves on. A date of birth opens 30
   years back and only offers ages the check accepts. A phone number typed as
   0712 345 678, +254 712 345 678 or 254712345678 is tidied to 0712345678 before
   it is checked. An address question has **Use my location** and **Find
   address**: they ask OpenStreetMap's free address service (Nominatim) for the
   address at the phone's position, or for up to five Kenyan addresses matching
   what was typed. The agent checks a suggestion with the customer and taps
   **Use** to fill the question. Nothing is sent while typing, only on a tap,
   and at most one request a second, as Nominatim's rules ask.
6. **Suggestions and warnings.** A guidance panel floats at the bottom of the
   form and stays in sight while the agent scrolls. It shows, in order of
   priority: an answer to fix, the AI's "Suggested next" question, or the next
   required question. Tapping it opens that question, in any section, and puts
   the cursor in it. When more than one answer needs fixing, the panel lists
   them all in form order; tapping one opens it. If the session looks likely to
   be abandoned, the panel also shows a gentle line ("Check in with the
   customer", then "This customer may need help now"); tapping it shows the
   advice. The warning builds up slowly so one slip does not alarm the agent:
   nothing for the first four checked answers, always amber before red, and red
   only after three high scores in a row. It drops as soon as things improve.
   The panel can be dragged anywhere on the page or folded to one line, and it
   never disappears: before any advice it points at the first required
   question. A small guide character
   in the panel shows how things are going: calm, pleased after an accepted
   answer, concerned when something needs fixing or the risk is amber, worried
   when it is red, and celebrating when every required answer is in. Tapping it
   folds or opens the panel. The panel's ⋮ menu has two settings, both
   remembered on the phone: **Auto-follow** (the keyboard's Next key checks the
   answer and goes straight to where the panel points) and **Moving guide**
   (turn it off for a still face that uses less battery; it is also still when
   the phone's "Remove animations" is on). The first time the form opens on a
   phone, the guide gives a five-step tour of the panel (replay it from ⋮).
   Each time after that it greets the agent with a fun fact about onboarding
   for a few seconds (tap to skip), then opens the first question it points at;
   the panel stays open after the greeting.
   **While the keyboard is open**, the panel becomes a slim bar just above the
   keyboard (guide, risk dot, where to go next; tap to jump), the header shrinks
   and the Next button steps aside, so the box being typed in stays visible.
   Tapping the guide in the slim bar opens the full panel over the form. On
   short phones the header is always compact and the panel starts folded.
   **The Enter key** never leaves the form. Pressed in a box, it checks the
   answer and moves to the next question in the section (or, with Auto-follow,
   or at the last question, to where the panel points). Pressed when no box is
   selected, it goes to where the panel points. All of it is advice; the agent
   decides.
7. **Review and submit.** The review page lists every answer by section, with an
   Edit button per section and a list of anything still missing. Tapping a
   missing or wrong answer opens its question. Submit stays
   off until every required answer is valid. The app shows "Onboarding
   complete" only after Firebase confirms the save.

Answers are saved as a draft while the agent works (about 0.7 seconds after the
last change). An unfinished session can be continued from **My sessions** on the
agent's home screen, or, for the latest one, with one tap on **Continue last
session** there. It opens on the section where the agent stopped. The card has a
**Cancel session** link: after a confirm, the session is marked abandoned, can
no longer be changed, and the card goes. A session with no saved change for 48
hours is marked abandoned the next time the agent opens the home screen, so old
sessions stop coming back. **My sessions** has an Abandoned filter. The phone's
Back button goes back one step wherever the screen has a back arrow.

## An admin's pages

Admins see five pages in a bottom navigation bar:

- **Overview**: the number of sessions started, and four tall bars: Finished,
  Orderly (followed the standard, see below), Filled (required answers filled
  in) and Unfinished. Tap a bar to read what it means. Under **More details**
  are the weekly trend (with a "Show numbers" table), the most corrected
  questions, the questions most often answered out of order, and agents
  compared. A bar with nothing to measure shows a dash, never a made-up number.
- **Agents**: invite agents by email, see whether each has registered, and turn
  an agent off or on.
- **Sessions**: search and filter all of the company's sessions. Tap one to open
  its own page with every saved answer (read-only). Two buttons save the
  sessions as CSV files, in a place the admin chooses:
  - **Sessions CSV**: one row per session (agent, customer type, status, times,
    answers checked, failed checks, steps out of order, followed the standard).
  - **Training CSV**: one row per checked answer, in the columns of the training
    data (`ml/dataset.py`), so real sessions can retrain the models. Agents are
    replaced by codes (`AGENT-` plus 12 characters). Only sessions with a known
    outcome are included: completed, abandoned (cancelled by the agent or
    marked so after 48 hours), or unfinished with no check for 48 hours
    (labelled abandoned). Run `python ml/dataset.py split <file>` to fill the
    `split` column by agent before training.

  Neither file holds answers or customer names; sessions do not save which
  answer was given to a check, only the question, pass or fail, and the time.
- **Questions**: the standard questions of the company's industry, grouped
  (Identity, Contact, Business, Money, Service), with a switch for which are
  required. Add, edit or delete the company's own questions (name,
  instructions, answer type, checks, choices). Seven questions
  always stay required: full name, date of birth, ID type, ID number, politically
  exposed person status, US person status, and acceptance of the terms.
- **Company**: copy the company ID to give to agents, and set the onboarding
  industry (agents cannot sign in until it is set). **Allow sessions to improve
  the system** (off by default) makes the consent screen ask each customer the
  optional question above. For each customer who agrees, the app saves an
  anonymised copy of the session to `trainingData/` as the agent works: the
  industry, customer type, number of questions, the agent as a code, and each
  check (question, passed or not, time). No answers, names or session ID. The
  database rules let agents only add these copies; no one can read them in the
  app. The project owner exports `trainingData` from the Firebase console and
  runs `python ml/dataset.py from-firebase <export.json>` to get a training CSV,
  already split by agent.

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
| Session details | company and agent IDs, agent email, industry, customer type, status (`in_progress` or `completed`), start, consent and finish times, `answerChecks: "on_leave"`, `trainingConsent` |
| `playbookSnapshot` | a copy of the questions and rules used, so later setting changes do not alter old sessions |
| `entries/{fieldId}` | the answers (`value`, `isValid`, `timestamp`): drafts while in progress, then only valid, non-empty answers when completed |
| `validation/{fieldId}` | the latest check result per question (`isValid`, `updatedAt`, and `errorMessage` while it fails) |

IDs are not repeated inside a record: the session ID and question ID are the keys
in its path. Sessions saved before 8 November 2026 still hold those repeats and
`lastSavedAt`/`consentBy`; the app reads both forms.
| `events` | one `field_validation_passed` or `field_validation_failed` record per attempt: event type, question ID and time, never the answer |
| `resumeFieldId`, `updatedAt` | where to continue an unfinished session, and when it was last saved |

Time spent per question and revisits feed the in-app risk warning. They are
worked out in memory and are not saved.

The ID photo reader (on the "Identification document" question) opens the camera
from inside the app. The photo goes into the app's private cache, never the
phone's gallery, and Google ML Kit reads its printed text on the phone. The photo
is deleted as soon as it has been read. The text is shown for the agent to
compare. Beside the full name, ID number and date of birth it found, a **Use**
button fills and checks that question; nothing is filled unless the agent taps
it. Neither the photo nor the rest of the text is uploaded or saved. It is a
typing aid, not identity
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

All app code is in one package, `android/app/src/main/java/com/example/salesonboarding/`:

| File | What it does |
| --- | --- |
| `MainActivity.kt` | App start and which screen is shown. |
| `Auth.kt` | Sign-in and registration screens, and the Firebase account checks. |
| `SocialSignIn.kt` | Continue with Google (shown only when set up). |
| `AddressLookup.kt` | Use my location and Find address, through OpenStreetMap's Nominatim. |
| `AgentScreens.kt` | Agent home, customer choice, and consent. |
| `OnboardingForm.kt` | The agent's form: sections and step bar, review and finish pages. |
| `FormQuestions.kt` | Each kind of question input, format hints, and the ID photo reader. |
| `OnboardingController.kt` | Form state, checking answers when the agent leaves them, suggestions, progress, drafts, and saving. |
| `Fields.kt` | The question catalogue, admin rule changes, playbooks, and form sections. |
| `Validation.kt` | Answer checks and their messages. |
| `NextFieldPredictor.kt` | Runs the next-question model (TensorFlow Lite). |
| `Insights.kt` | The risk warning (trained model and hand-set fallback) and the consistency measure. |
| `FirebaseFieldEntryStore.kt` | Saves sessions and answers. |
| `SessionHistoryScreen.kt` | Session lists and the session page, for agents and admins. |
| `AdminDataStore.kt` | Admin data: agents, question rules, company industry, overview numbers. |
| `AdminScreens.kt` | The admin pages behind the bottom navigation bar. |
| `AdminOverview.kt` | The admin overview and its charts. |
| `Ui.kt` | Colours (app, charts, sign-in), fonts, theme, and the shared building blocks. |

Android Studio previews show the sign-in screen (`Auth.kt`), the customer choice
(`AgentScreens.kt`), the agent form (`OnboardingForm.kt`), the admin overview
(`AdminOverview.kt`) and the shared building blocks (`Ui.kt`)
without running the app.
