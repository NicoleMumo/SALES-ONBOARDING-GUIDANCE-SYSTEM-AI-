# 03 - Forms and saved data

This guide explains how form fields are chosen, how answers are checked, and
what the app saves.

## Forms and fields

The app chooses a form using the company's industry and the customer's type:

- Banking: individual or business.
- Telecom: mobile, broadband, TV, IoT, or landline.

Admins can change some field rules and add custom fields. The app saves a copy
of the form settings with each session. Later setting changes do not change
that saved copy.

Each field has a name, type, description, required/optional setting, and rules
for checking the answer. Rules may check that an answer is present, has a
valid email or Kenyan phone number, is a valid date or choice, or matches
another field.

## Answer checks and next-field suggestions

The app checks an answer after the agent stops typing for 300 milliseconds.
When an answer changes, the old check is cleared. Fields that depend on that
answer are checked again. Untouched fields do not show errors.

The next-field model:

- Does not receive the customer's answers.
- Does not check or approve answers.
- Suggests only fields that are not already valid.
- Does not change which fields are required.

When required fields are still incomplete, the app can offer any field that is
not currently valid. This can include optional fields. The app handles answer
corrections before showing a predicted next field. The form's own checks decide
whether the session can be submitted.

See [04 - Training data and AI](04-training-data-and-ai.md) for the model test
status and its limits.

## Document OCR and identity checks

On the identification-document step, an agent can choose a document photo.
Bundled Google ML Kit reads printed text on the device. The app displays the
text for the agent to inspect; it does not automatically fill form fields.
The image and OCR result are not uploaded or saved by this feature. The agent
must check the document and enter only the required answers.

OCR is text extraction, not proof that a document is genuine or belongs to the
customer. The app does not currently capture a selfie, check liveness, match
faces, screen sanctions, or make KYC/AML approval decisions. Banking playbooks
do not ask agents to manually mark liveness or face-match checks as passed.

Smile ID has been selected for evaluation, but no provider API or backend
endpoint is implemented. See [02 - Connect Firebase](02-connect-firebase.md)
for the proposed server-side verification API and the work still required.

## What the app saves

Sessions are stored in Firebase at:

```text
companies/{companyId}/onboardingSessions/{sessionId}
```

A session includes the company and agent IDs, industry, customer type, status,
timestamps, and a copy of the form fields and rules used.

Other saved data includes:

- Non-empty draft answers under `entries/{fieldId}` while a session is in
  progress. Drafts include a resume field and last-saved time.
- Valid, non-empty answers under `entries/{fieldId}` when the session is
  completed. Invalid draft values are removed when the agent completes the
  session.
- The latest answer-check result under `validation/{fieldId}`.
- Events under `events`, with the event type, field ID, and time.

The event records do not contain the customer's answer. The app saves the
draft as the agent types, with a short delay to reduce repeated writes. A save
failure is shown in the form. Agents can resume their own in-progress sessions
from **My onboarded clients**. The app shows its answer-review step before it
submits a completed session, and shows completion only after Firebase confirms
the save.

Time spent on fields and revisits are used for in-app completion-risk
guidance. They are calculated in memory and are not saved as event details.

## Access and deletion

The intended access is: company admins can access their company's sessions,
and agents can access their own sessions. Firebase rules must be tested to
confirm this; a database path alone does not protect data.

The app does not automatically delete sessions. Decide access, backups,
retention, and deletion rules before using real customer information. See
[02 - Connect Firebase](02-connect-firebase.md).
