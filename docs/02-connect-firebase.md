# 02 - Firebase setup

SalesMesh uses Firebase Authentication to sign users in and Realtime Database
to save company data. Finish the tests in this guide before using real customer
information.

## Connect the app

1. Create or choose a Firebase project.
2. Add an Android app with this package name:
   `com.example.salesonboarding`.
3. Download `google-services.json` and save it as
   `app/google-services.json`. This private, project-specific file is not in
   Git.
4. Turn on **Email/Password** sign-in in Firebase Authentication.
5. Create a Realtime Database.

The app currently uses this database URL:

```text
https://salesmesh-default-rtdb.asia-southeast1.firebasedatabase.app
```

If your Firebase project uses another URL, update the app and check that it
points to the right project. Never put service-account keys in the app or
repository.

## Set up an admin

A trusted Firebase project owner must create or approve the company, then
assign the admin claims with the Firebase Admin SDK. For example:

```javascript
await getAuth().setCustomUserClaims(userUid, {
  admin: true,
  companyId: "acme_finance",
});
```

Use a stable company ID. The admin must verify their email and sign in again
after the claims change. Signing up in the app does not give a user admin
rights.

After signing in, the admin sets the company's industry and invites agents.

## Invite an agent

1. The admin creates an invitation with the agent's name and email.
2. The agent registers using the invited email and company ID.
3. The app checks the invitation, company, and membership.
4. The agent chooses a customer type for the company's industry.

Agent email verification is currently off for testing. Turn it on and test it
before production. Knowing a company ID does not give someone access.

## Database paths and rules

Company data is stored below:

```text
companies/{companyId}/
```

This includes industry settings, invitations, field settings, and onboarding
sessions. Memberships are stored at:

```text
companyMemberships/{uid}
```

The rules file is [database.rules.json](../database.rules.json). Having this
file does not mean the rules are deployed or correct. Use the Firebase
Emulator or a test project to check the rules before deployment.

Test at least two companies, each with an admin and an agent. Check that:

- Company A cannot read or change Company B's data.
- An agent cannot read another agent's sessions.
- Only an invited email can join a company.
- Disabled invitations and memberships cannot be used.
- Agents can only create or update allowed session data.

The repository does not yet have an automated rules test suite, and deployment
has not been confirmed.

## Old data and privacy

Older database data may be outside the current `companies/{companyId}/` path.
Back up and review old data before changing or deleting anything. Deleting
Firebase nodes permanently removes their data.

The app does not set a data-retention period or automatically delete sessions.
Before production, decide who may see the data, why it is collected, how long
it is kept, and how it will be deleted. Do not use real customer details until
the rules and privacy plan have been reviewed and tested.

## Free document reading and manual identity review

The app uses on-device ML Kit text recognition to read text from a document
photo selected by the agent. It does not call a billable OCR endpoint. The
photo and extracted text are not automatically uploaded or saved.

OCR is only a data-entry aid. The agent must compare the text with the original
document and customer, then follow the organization's approved manual review
process. The app does not store an identity-verification decision and does not
perform official KYC/AML checks, selfie capture, liveness detection, face
matching, or sanctions screening. It does not approve or reject customers.

There is no free KYC API included. A third-party identity-verification service
may charge for checks or require a paid plan. Do not add one unless the
organization approves its cost, coverage, data handling, and compliance terms.
If a service is considered later, put credentials and provider calls on a
trusted backend, never in the Android app or source control.

## Common problems

- **Admin access denied:** Check the verified email, admin claim, company ID,
  Firebase project, and refreshed sign-in token.
- **Agent access denied:** Check the invitation email, company ID, membership,
  and deployed rules.
- **Company settings missing:** Check that the admin saved the industry and
  that the app uses the right database URL.
- **Session save failed:** Check network access and Firebase errors. Do not
  treat an unsuccessful save as complete.
