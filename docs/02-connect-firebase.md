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

## Future identity-verification API

The Android app currently has no server-side identity-verification endpoint.
Smile ID is only a provider under evaluation; the app does not capture a
selfie, perform liveness or face matching, or make KYC/AML decisions.

If the organization approves a provider, use a trusted backend such as Firebase
Cloud Functions as the only component that holds provider credentials. A
proposed app-to-backend contract is:

```text
POST /v1/identity-verifications
Authorization: Bearer <Firebase ID token>
Content-Type: application/json

{
  "sessionId": "existing-session-id",
  "countryCode": "KE",
  "documentType": "national_id",
  "documentObjectRef": "private, short-lived document reference",
  "selfieObjectRef": "private, short-lived selfie reference"
}
```

The backend must verify the caller's Firebase identity, active company
membership, ownership of the in-progress session, and consent before contacting
the provider. It must map the provider response to a minimal app response, for
example:

```json
{
  "verificationId": "server-generated-id",
  "overallStatus": "NEEDS_HUMAN_REVIEW",
  "liveness": "PENDING",
  "faceMatch": "PENDING"
}
```

These statuses are not account approval. Any pass, failure, mismatch, or
uncertain result must follow an organization-approved human-review policy.
Never put provider secrets in the Android app, Realtime Database, or source
control. Do not store identity images, raw OCR text, or biometric templates in
Realtime Database or logs. Before implementation, confirm Smile ID's coverage,
supported document types, fees, data-processing terms, retention/deletion
controls, and applicable Kenyan privacy and financial-sector requirements.
Create and test the backend endpoint, private image upload and deletion flow,
Firebase rules, consent copy, and review process before enabling the feature.
The Android-side `IdentityVerificationGateway` types define an integration
boundary only; there is no network client or provider-backed implementation
yet.

## Common problems

- **Admin access denied:** Check the verified email, admin claim, company ID,
  Firebase project, and refreshed sign-in token.
- **Agent access denied:** Check the invitation email, company ID, membership,
  and deployed rules.
- **Company settings missing:** Check that the admin saved the industry and
  that the app uses the right database URL.
- **Session save failed:** Check network access and Firebase errors. Do not
  treat an unsuccessful save as complete.
