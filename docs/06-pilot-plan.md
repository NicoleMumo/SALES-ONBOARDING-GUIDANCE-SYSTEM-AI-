# Pilot plan and feedback form

A small, safe trial of SalesMesh. **The users are the agents and the admin.** The
agent fills in the form on a customer's behalf, so the customer never touches the
app. For the pilot, each agent signs up **made-up test customers**, played by you
or a sheet of fake details.

It answers one question: can people who did not build the app use it, and does it
help them?

## The pilot in one page

| | |
| --- | --- |
| **Who** | 1 admin and 3 to 5 sales agents (people who do customer sign-ups, or could) |
| **Test customers** | Made-up only (the agent enters their details). Never real names, real ID numbers or real phone numbers |
| **Where** | Each person's own Android phone, or one you lend them |
| **How long** | About 30 minutes per agent, plus 10 minutes for the admin |
| **You do** | Set up, then watch and take notes. Do not help unless they are stuck for over a minute |

## Before the pilot (checklist)

- [ ] A Firebase project is set up and the rules from `firebase/database.rules.json` are published ([Set up and run](01-set-up-and-run.md))
- [ ] The admin account is created and the company's **industry is set** (Company page)
- [ ] Each agent is invited by email (Agents page) and has the company ID
- [ ] The app is installed on every phone and each agent can sign in once
- [ ] A sheet of made-up customers is ready (see below)
- [ ] You have printed or opened the feedback form, one per person
- [ ] Everyone knows it is a test and the data is made up

Email verification is not needed for a pilot with made-up customers. Turn it on
before any real data ([Privacy note](05-privacy-note.md)).

## Test customers

Prepare 5 made-up customers for each agent to sign up. Use obviously fake details, for example:

| Test customer # | Type | What to test |
| --- | --- | --- |
| 1 | Banking, individual | A normal, complete sign-up |
| 2 | Banking, business | The extra business questions |
| 3 | Telecom, mobile | The telecom form |
| 4 | Any | **Make mistakes on purpose:** a wrong phone number, an under-18 date of birth. Does the error message help? |
| 5 | Any | **Stop halfway,** close the app, reopen it, and continue from My sessions |

## What each agent does (30 minutes)

1. Sign in. If new, create an account. *(Note any trouble.)*
2. Sign up test customers 1 to 5 above, one after another, as if the customer were sitting next to them.
3. Notice the "Suggested next" question and the warning banner when they appear.
4. Fill in the feedback form.

## What the admin does (10 minutes)

1. Open **Overview** and say in your own words what the four bars mean.
2. Open **Sessions** and find one agent's session.
3. Open **Questions**, make one question optional, and add one question of your own.
4. Fill in the feedback form.

## What you record while watching (observer sheet)

One row per test customer, per agent.

| Agent | Test customer # | Finished? (Y/N) | Minutes | Mistakes made | Where they got stuck | Asked for help? |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |

Also write down any crash, any screen that looked wrong, and anything an agent
said out loud.

## What the app tells you afterwards

After the pilot, open the admin **Overview**. It shows, from the pilot sessions:
sessions started, finished, orderly (few corrections, close to the standard
order), filled, and unfinished, plus the most corrected questions. Take a
screenshot for the report.

## Feedback form

Give one to each person. Takes 3 minutes. Circle 1 to 5: **1 = strongly disagree,
5 = strongly agree.**

**Name (optional):** __________  **Role:** Agent / Admin  **Phone model:** __________

| | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- |
| 1. The app was easy to learn. | | | | | |
| 2. I always knew what to do next. | | | | | |
| 3. The error messages helped me fix my mistakes. | | | | | |
| 4. The "Suggested next" question was useful. | | | | | |
| 5. The warning banner (if I saw one) was useful. | | | | | |
| 6. It was quick enough, not slow. | | | | | |
| 7. I would use this app when signing up real customers. | | | | | |
| 8. The screens looked clear and easy to read. | | | | | |

**Open questions** (a sentence is enough):

9. What was the most confusing part?
10. What did you like most?
11. What one thing would you change first?
12. Did anything go wrong (crash, wrong message, lost answers)? What happened?

**For admins only:**

13. Did the Overview bars make sense without explanation? (Y / N) What was unclear?
14. Was it easy to invite an agent and change a question? (Y / N)

## Proposed targets

These are suggestions to decide before the pilot. Change them if you want.

| Measure | Target |
| --- | --- |
| Sign-ups of test customers 1 to 3 that the agent finished without help | at least 80% |
| Average rating on questions 1 to 8 | at least 4 out of 5 |
| Crashes or lost answers | none |
| "I would use this app when signing up real customers" (question 7) | most people answer 4 or 5 |

With only 3 to 5 people these are signs, not proof. The report should say so.

## After the pilot

1. Delete the pilot sessions in the Firebase console (they are made-up, but keep
   the database clean). See [Privacy note](05-privacy-note.md).
2. Put the observer sheet, the feedback scores and the Overview screenshot in one
   folder.
3. List every problem found, mark each **fixed** or **left, and why**.
4. Use the results in the final report, including anything that missed its target.
