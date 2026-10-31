# 04 - System architecture

How the parts of SalesMesh fit together. The diagrams are written in Mermaid, so
GitHub draws them on this page.

## 1. The big picture

There is one phone app and one cloud database. The AI runs **on the phone**, so a
customer's answers never leave the device to be scored. The Python side is only
used offline, to make data and train the models.

```mermaid
flowchart LR
    subgraph Phone["Android phone: the SalesMesh app"]
        UI["Screens<br/>(Jetpack Compose)"]
        LOGIC["Form logic<br/>checks, progress, drafts"]
        AI["On-device AI<br/>next question + risk warning"]
        OCR["ID photo reader<br/>(Google ML Kit, on the phone)"]
        UI --> LOGIC
        LOGIC --> AI
        UI --> OCR
    end

    subgraph Cloud["Firebase (Google cloud)"]
        AUTH["Authentication<br/>email + password,<br/>admin and company claims"]
        DB[("Realtime Database<br/>one branch per company")]
        RULES{{"Access rules"}}
        RULES --- DB
    end

    subgraph Offline["Your computer (not shipped)"]
        SIM["Simulated data<br/>ml/dataset.py"]
        TRAIN["Training<br/>ml/next_field_model.py"]
        MODELS[/"Model files<br/>ml/models/"/]
        SIM --> TRAIN --> MODELS
    end

    LOGIC <-->|sign in| AUTH
    LOGIC <-->|sessions, answers, settings| RULES
    MODELS -. "copied into the app by the build" .-> AI
```

## 1b. The standard components, and where SalesMesh has them

Most system architecture guides list the same building blocks. This is how
SalesMesh fits them. It is a **client app plus a managed cloud backend**, not a
set of servers we run ourselves, so some blocks do not exist here on purpose.

| Standard component | In SalesMesh | Notes |
| --- | --- | --- |
| Clients and user interface | The Android app (Jetpack Compose screens) | Agents and admins use the same app; the screens differ by role |
| API gateway / load balancer | Not needed | The app talks to Firebase directly over its own secure connection. Google handles routing, TLS and scaling |
| Application / business logic | Inside the app: form logic, answer checks, risk warning, consistency measure | No separate server. See the layers in section 3 |
| Data storage | Firebase Realtime Database (NoSQL, one branch per company), plus the phone's offline copy | No cache engine such as Redis |
| Messaging / event broker | Not needed | Nothing runs in the background between services. The app writes records straight to the database |
| AI / ML | Two small models run on the phone; training happens offline on a computer | Section 7 |
| Infrastructure | Google's cloud for Firebase; the agent's own Android phone | We run no servers, containers or virtual machines |
| Security and authentication | Firebase Authentication, admin and company claims, database access rules | Section 6 |

**Monolith or microservices?** Neither word fits well. The app is one
**monolith** (one module, one package), and the backend is a managed service, so
there are no microservices to look after. That is the right size for one team and a
pilot. It is also why there is no gateway or message broker to explain.

**Purpose:** give every agent the same checked form, so onboarding is consistent,
and show admins how consistently the team follows it.

## 2. Who uses it

| Person | What they do | What they can see |
| --- | --- | --- |
| **Sales agent** | Onboards customers with the form | Only their own sessions |
| **Admin** | Manages agents, questions and industry; reads results | Everything in their own company, nothing from other companies |
| **Project owner** | Creates the first admin (sets the admin and company claims in Firebase) | The Firebase console |

## 3. The layers

The app has three layers. A layer may only call the one below it, so a screen
never talks to Firebase directly and the checks never draw anything.

```mermaid
flowchart TB
    subgraph P["1. Presentation layer: what the user sees"]
        direction LR
        AUTHUI["Auth<br/>sign in, register"]
        AGENT["AgentScreens<br/>home, customer type, consent"]
        FORM["OnboardingForm + FormQuestions<br/>the form"]
        HIST["SessionHistoryScreen<br/>lists, session page"]
        ADMIN["AdminScreens + AdminOverview<br/>5 admin pages"]
        UIKIT["Ui<br/>colours, Jost, shared parts"]
    end

    subgraph L["2. Logic layer: the rules, no screens, no Firebase"]
        direction LR
        CTRL["OnboardingController<br/>state, progress, drafts"]
        FIELDS["Fields<br/>question catalogue, playbooks"]
        VALID["Validation<br/>answer checks"]
        INSIGHT["Insights<br/>risk warning, consistency"]
        PRED["NextFieldPredictor<br/>runs the TFLite model"]
    end

    subgraph D["3. Data layer: where information is kept"]
        direction LR
        ENTRY["FirebaseFieldEntryStore<br/>sessions and answers"]
        ADMINSTORE["AdminDataStore<br/>agents, rules, analytics, membership"]
        ASSETS[("App assets<br/>onboarding_model.tflite<br/>risk_model.json")]
    end

    subgraph X["External services"]
        direction LR
        FBAUTH["Firebase Authentication"]
        FBDB[("Firebase Realtime Database")]
        MLKIT["Google ML Kit<br/>ID photo reader"]
    end

    P --> L
    L --> D
    ENTRY --> FBDB
    ADMINSTORE --> FBDB
    ADMINSTORE --> FBAUTH
    PRED --> ASSETS
    INSIGHT --> ASSETS
    FORM -.-> MLKIT
```

| Layer | Files | Job | Must not |
| --- | --- | --- | --- |
| Presentation | `MainActivity`, `Auth`, `AgentScreens`, `OnboardingForm`, `FormQuestions`, `SessionHistoryScreen`, `AdminScreens`, `AdminOverview`, `Ui` | Show screens, take taps and typing, pass them down | Decide whether an answer is valid |
| Logic | `OnboardingController`, `Fields`, `Validation`, `Insights`, `NextFieldPredictor` | Check answers, track progress, suggest the next question, work out the risk and consistency | Draw anything or call Firebase |
| Data | `FirebaseFieldEntryStore`, `AdminDataStore`, the model files in `assets/` | Save and load sessions, agents, settings; hold the trained models | Contain rules about the form |

Three honest exceptions: `MainActivity.kt` creates the Firebase stores and hands
them to the screens, `Auth.kt` mixes the sign-in screens with the Firebase
account checks, and `Fields.kt` holds both the question catalogue and the admin's
rule changes. Splitting them is possible but would not change how the app behaves.

Each store is an interface (`AdminDataStore`, `OnboardingSessionStore`,
`SessionHistoryStore`) with a Firebase version behind it. That is what lets the
Android tests run the logic without a database.

## 4. What happens during one onboarding

```mermaid
sequenceDiagram
    actor A as Agent
    participant App as App
    participant AI as On-device AI
    participant DB as Firebase

    A->>App: Choose customer type
    A->>App: Customer gives consent
    App->>DB: Start session (company, agent, industry, consent time)
    loop Each question
        A->>App: Type an answer, then leave the box
        App->>App: Check the answer
        App->>DB: Save draft + pass/fail record (never the answer text in the record)
        App->>AI: Which question next? Is the customer likely to give up?
        AI-->>App: Suggested next question, risk level
        App-->>A: Error message, or suggestion and warning
    end
    A->>App: Review and submit
    App->>DB: Mark completed, keep only valid answers
```

## 5. What is stored in the database

Everything for a company lives under its own branch. Another company's branch is
not readable, which the access rules enforce.

```
companyMemberships/{agentUid}            who belongs to which company, active or not
companies/{companyId}/
├── settings/industry                    banking or telecom
├── agentInvitations/{email}             invited agents
├── fieldRuleOverrides/{fieldId}         which standard questions are required
├── fieldDefinitions/{fieldId}           the company's own questions
└── onboardingSessions/{sessionId}/
    ├── (details)                        agent, industry, status, times, consent time and agent
    ├── playbookSnapshot                 the questions used, frozen at the start
    ├── entries/{fieldId}                the answers
    ├── validation/{fieldId}             latest pass/fail per question
    └── events                           one record per check: type, question, time
```

Not saved anywhere: the ID photo, the text read from it, and the time spent per
question (used only in memory by the risk warning).

## 6. Security in layers

| Layer | What it does | Where |
| --- | --- | --- |
| Sign-in | Email and password; admins carry `admin` and `companyId` claims | Firebase Authentication |
| Invitation | An agent can only join with an email an admin invited, for that company | `AdminDataStore`, rules |
| Active check | A switched-off agent loses access | `companyMemberships`, rules |
| Company wall | Every read and write is limited to the person's own company | `firebase/database.rules.json` |
| Agent wall | Agents read only sessions where they are the agent | rules |
| Locked questions | Seven compliance questions can never be made optional | `FieldRuleConfiguration` |
| No stored secrets | No service-account keys or customer data in the repository | convention + `.gitignore` |

Still to do before real data: a full test of the rules, email verification, a
retention plan. See the README's known limits.

## 7. The AI side

```mermaid
flowchart LR
    G["dataset.py<br/>simulated sessions<br/>(3 datasets, different seeds)"] --> N["next_field_model.py<br/>Transformer"]
    G --> R["risk model<br/>logistic regression"]
    N --> T["onboarding_model.tflite<br/>+ preprocessing_meta.json"]
    R --> J["risk_model.json<br/>14 weights, 2 thresholds"]
    T --> APP["app assets"]
    J --> APP
    C["consistency.py<br/>plain counting"] -. "same rule written again in Kotlin (Insights.kt)" .-> APP
```

| Piece | Job | Where it runs |
| --- | --- | --- |
| Next-question Transformer | Suggests which question comes next | Phone (TensorFlow Lite) |
| Risk model | Warns when a customer may give up | Phone (weights read from JSON) |
| Consistency measure | Counts corrected answers and out-of-order steps | Phone and admin screens (not AI) |
| ID photo reader | Shows printed text from a photo to help typing | Phone (Google ML Kit) |

Both models were trained on **simulated** data only. A build step copies the
files in `ml/models/` into the app, and a check keeps the two copies identical.

## 8. Building and checking

```mermaid
flowchart LR
    DEV["Developer<br/>branch + pull request"] --> CI["GitHub Actions<br/>build + Android tests"]
    DEV --> LOCAL["Local checks<br/>Gradle tests + Python tests"]
    CI --> MAIN2["main branch"]
```

CI currently runs the Android build and tests. Running the Python tests there
and testing the Firebase rules are planned (Phase 5).

## 9. Why it is built this way

- **AI on the phone:** answers are not sent anywhere to be scored, it works
  without a connection, and there is no server to run.
- **One Firebase branch per company:** a simple wall between companies that the
  rules can check in one line.
- **Plain counting for consistency:** an admin can check any number by hand. An
  AI model for this was tried and failed its pass mark.
- **Frozen question list per session:** changing a setting later never rewrites
  old sessions.
- **One Android module:** small enough that splitting it would add work and no benefit.
