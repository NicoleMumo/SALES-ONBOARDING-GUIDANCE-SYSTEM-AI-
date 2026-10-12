# Firebase setup

The Android app includes the Firebase Realtime Database dependency and uses
`FirebaseFieldEntryStore` to persist valid onboarding entries.

Each session stores its agent ID, industry, context, status, timestamps, field
entries, validation results, and onboarding events under
`onboardingSessions/{sessionId}`.

To connect the app to a Firebase project:

1. Create or select a Firebase project.
2. Register the Android application ID:
   `com.example.salesonboarding`.
3. Download `google-services.json`.
4. Place it in `app/google-services.json` (this file is present locally and is
   excluded from Git).
5. Enable Realtime Database and configure its security rules.
6. Build and run the app. `MainActivity` already creates a
   `FirebaseFieldEntryStore` using the Firebase Realtime Database reference.

`google-services.json` is intentionally not committed. It is project-specific
configuration and must be supplied by the Firebase project owner.
