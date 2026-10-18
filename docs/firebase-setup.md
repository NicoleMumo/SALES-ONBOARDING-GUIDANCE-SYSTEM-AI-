# Firebase setup

The Android app uses Firebase Realtime Database for onboarding sessions and
admin-managed data. `FirebaseFieldEntryStore` persists onboarding data, while
`FirebaseAdminDataStore` loads and saves agent records, field rules, and custom
field definitions.

Each session stores its agent ID, industry, context, status, timestamps, field
entries, validation results, and onboarding events under
`onboardingSessions/{sessionId}`. Each new session includes a snapshot of its
ordered playbook fields and revision. Field-update events record field IDs and
whether a value is present; they do not duplicate the entered customer value.
The admin analytics screen reads this path:
it counts all sessions, calculates the completion rate from sessions whose
status is `completed`, and averages durations for completed sessions that have
valid start and completion timestamps. It also shows team completion rates,
average required-field progress and the most common unfinished step when
sessions include a playbook snapshot.

Admin data uses these Realtime Database paths:

- `salesAgents/{agentId}` for agent records.
- `fieldRuleOverrides/{fieldId}/required` for required-rule overrides on
  catalogue fields.
- `fieldDefinitions/{fieldId}` for custom field names, descriptions, types,
  validation rule selections, and approved choices for choice fields.

Stored sessions can be retrieved with:

```kotlin
store.getSession(sessionId) { result ->
    result.onSuccess { session ->
        // Use the stored session when it is not null.
    }.onFailure { error ->
        // Show a retrieval error to the user.
    }
}
```

To connect the app to a Firebase project:

1. Create or select a Firebase project.
2. Register the Android application ID:
   `com.example.salesonboarding`.
3. Download `google-services.json`.
4. Place it in `app/google-services.json` (this file is present locally and is
   excluded from Git).
5. Enable Firebase Authentication email/password and Realtime Database.
6. Provision agent Auth accounts through a trusted process. Assign the
   `admin: true` claim to administrators using a secured Admin SDK environment.
7. Deploy `database.rules.json` with the Firebase CLI after selecting the
   intended project.
8. Run the emulator rules tests with `npm install` and
   `npm run test:database-rules`.
9. Build and run the app. Agent field configuration loads after sign-in, and
   the admin dashboard uses the same database reference for management and
   analytics.

Realtime Database persistence is enabled at application startup. The field
definitions and rule overrides are kept in the local cache, and Firebase queues
offline writes on-device to sync when connectivity returns. The onboarding
screen shows the current network status. Offline submissions can be queued;
the app does not yet provide a cross-restart draft-resume screen.

`google-services.json` is intentionally not committed. It is project-specific
configuration and must be supplied by the Firebase project owner.

## Authentication and access control

The app signs in through Firebase Authentication email/password. The role
choice on the login screen is checked against the signed-in token: only a
trusted `admin: true` custom claim grants the administrator role. Claims must
be provisioned from a secured Admin SDK environment, never from the app.

For example, a trusted server-side provisioning tool can assign the claim
after verifying the administrator through your organization:

```javascript
await getAuth().setCustomUserClaims(firebaseUid, { admin: true });
```

Use Application Default Credentials or another approved server-side
credential source. Never commit service-account keys or put them in the Android
application. The administrator must sign out and back in (or refresh the ID
token) before the claim is reflected in the app.

There is no public account-registration flow. Create or approve Firebase Auth
accounts through a trusted administrative process. Authenticated accounts
without the admin claim receive the agent experience. The in-app agent
directory stores contact records; it does not provision Auth accounts.

Deploy `database.rules.json` with the Firebase CLI after selecting the intended
project. The default rules deny access. Agents can read and update sessions
whose `agentUid` matches their Firebase UID; admins can access all sessions and
management data. Agent/profile management and field-rule changes require the
admin claim. Exercise both allowed and denied cases locally with:

```powershell
npm install
npm run test:database-rules
```

Session documents include `agentUid` and the timestamp when the agent confirmed
customer consent. The consent screen explains the onboarding purpose, Firebase
storage, who can access a session, and the absence of automatic deletion. The
organization remains responsible for its retention schedule and deleting old
records.

Validation events contain only event type, field ID, and timestamp. Customer
values are saved in the session entry record on submission; they are not copied
into analytics events or written on every keystroke. CI uses
`app/google-services.example.json` only as a nonfunctional build placeholder;
it cannot connect to a Firebase project.
