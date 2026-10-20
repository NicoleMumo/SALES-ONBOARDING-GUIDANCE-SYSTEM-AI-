# 05 - Testing and next steps

This page separates what is in the code from what has actually been tested or
deployed.

## In the project

- Android screens for agents and admins.
- Company settings, invitations, forms, answer checks, and saved sessions.
- A TensorFlow Lite model that suggests a next field.
- Tools to create simulated training data.
- A notebook to train and export the model.
- Firebase rules for company data.

## Still needs testing

1. Run the notebook from top to bottom. Record candidate coverage, app-style
   Top-1/Top-3/Top-5, Macro F1, and TensorFlow Lite versus Keras results.
   Keep these separate from old full-vocabulary results.
2. Run Android unit tests and build the app:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   .\gradlew.bat :app:assembleDebug
   ```

3. Test Firebase rules with at least two companies, admins, and agents.
4. Turn agent email verification back on and test it.
5. Measure model speed on a physical Android device.
6. Decide how customer data will be protected, kept, and deleted before
   production.
7. Test with suitable real-world data before making claims about real agent
   or customer performance.

The earlier `:app:compileDebugKotlin` check passed after a syntax fix. It was
not a full app test. Firebase rule deployment, the new model evaluation, and
physical-device timing are not confirmed.

## Before real customer data

- Deploy and test the Firebase rules.
- Check that one company cannot access another company's data.
- Restore agent email verification.
- Decide who can access customer data and how long to keep it.
- Decide how to handle deletion requests and backups.
- Never commit customer data or Firebase service-account keys.

See the other numbered guides for setup and testing steps.
