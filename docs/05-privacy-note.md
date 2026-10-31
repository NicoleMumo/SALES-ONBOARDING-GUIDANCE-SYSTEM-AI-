# Privacy note

What SalesMesh does with customer information, who can see it, and what is still
undecided. This is a project note, not legal advice. Before real customers are
used, have it checked against the law that applies to you (in Kenya, the Data
Protection Act, 2019).

## What is collected

When an agent onboards a customer, the app saves the answers to the form. Depending
on the industry and the company's own questions, that can include:

- name, date of birth, gender, email, phone number, home or business address
- identification document type and number
- profession, income and assets
- whether the person is a politically exposed person or a US person
- that the customer agreed to the terms, and **when** and **by which agent**
  (consent time and agent)

It also saves, per session: the agent, the company, the industry, start and finish
times, and a record of each answer check (which question, passed or failed, and
when, never the answer itself).

It does **not** collect location, contacts, or the customer's photo or ID image.

## Why

To complete the customer's sign-up and to show the company how consistently its
agents follow the process. Nothing is used for advertising.

## Where it is kept

- **Firebase Realtime Database** (Google), in the Singapore region. One branch per
  company.
- **The agent's phone** keeps an offline copy of recent data so the app works
  without a connection.
- **Phone backups:** Android backup is currently **on** for the app, so a phone's
  backup may contain saved data. See "Decisions" below.

## Who can see it

| Who | What they can see |
| --- | --- |
| The agent | Only their own sessions |
| The company's admin | Every session in their own company, read-only |
| Another company | Nothing. The database rules block it (tested automatically) |
| The project owner | Everything, through the Firebase console |
| Google | Stores it for us as our cloud provider |

## The AI and the ID photo

- The AI models run **on the phone**. Answers are not sent anywhere to be scored.
- The ID photo reader reads text from a photo the agent picks, **on the phone**.
  The photo and the text it reads are not uploaded or saved.
- The models were trained on simulated data, not real customers.

## How long it is kept, and deleting

**There is no retention period yet, and the app has no delete button for sessions.**
Sessions stay until someone with Firebase access deletes them. This is the biggest
open item.

Today, to delete a customer's data you must remove their session in the Firebase
console (`companies/{companyId}/onboardingSessions/{sessionId}`). Switching an agent
off stops their access, but keeps the sessions they created.

## Decisions to make before real data

| Decision | Why it matters |
| --- | --- |
| How long to keep sessions, and who deletes them | Keeping data forever is the riskiest default |
| How a customer asks to see, correct or delete their data | Today it is a manual job in the console, and someone must own it |
| Who the data controller is (the company or you) | It decides who is responsible and who may need to register |
| Turn Android backup off? | The app can set `allowBackup` to false so phone backups do not copy customer data |
| Who gets Firebase console access | They can read everything |
| Turn on email verification for agents | It is off for testing |
| Deploy the tested rules to your Firebase project | The rules file only protects data once published |

## Customer consent

The agent reads the consent points to the customer and ticks that they agreed. The
app records the time and the agent. The full consent text is available from the
consent screen ("Read the full terms").

## More

- [How the app works](02-how-the-app-works.md): what is saved, screen by screen
- [System architecture](04-system-architecture.md): where the data lives
