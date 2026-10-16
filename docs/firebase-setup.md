# Firebase setup

The Android app uses Firebase Realtime Database for onboarding sessions and
admin-managed data. `FirebaseFieldEntryStore` persists onboarding data, while
`FirebaseAdminDataStore` loads and saves agent records, field rules, and custom
field definitions.

Each session stores its agent ID, industry, context, status, timestamps, field
entries, validation results, and onboarding events under
`onboardingSessions/{sessionId}`. The admin analytics screen reads this path:
it counts all sessions, calculates the completion rate from sessions whose
status is `completed`, and averages durations for completed sessions that have
valid start and completion timestamps.

Admin data uses these Realtime Database paths:

- `salesAgents/{agentId}` for agent records.
- `fieldRuleOverrides/{fieldId}/required` for required-rule overrides on
  catalogue fields.
- `fieldDefinitions/{fieldId}` for custom field names, descriptions, types,
  and validation rule selections.

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
5. Enable Realtime Database and configure its security rules.
6. Build and run the app. `MainActivity` loads the field configuration before
   starting onboarding, and the admin dashboard uses the same Firebase
   Realtime Database reference for its management and analytics screens.

`google-services.json` is intentionally not committed. It is project-specific
configuration and must be supplied by the Firebase project owner.
