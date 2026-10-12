# Firebase setup

The Android app now includes the Firebase Realtime Database dependency and a
`FirebaseFieldEntryStore` implementation for persisting valid onboarding
entries.

To connect the app to a Firebase project:

1. Create or select a Firebase project.
2. Register the Android application ID:
   `com.example.salesonboarding`.
3. Download `google-services.json`.
4. Place it in `app/google-services.json`.
5. Enable Realtime Database and configure its security rules.
6. Replace the default `InMemoryFieldEntryStore` in `MainActivity` with:

```kotlin
FirebaseFieldEntryStore(FirebaseDatabase.getInstance().reference)
```

`google-services.json` is intentionally not committed. It is project-specific
configuration and must be supplied by the Firebase project owner.
