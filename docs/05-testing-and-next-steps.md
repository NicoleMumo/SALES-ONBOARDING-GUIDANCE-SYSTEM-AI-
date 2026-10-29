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

1. Test the model with suitable real-world data. The current results come from
   simulated data only (see
   [04 - Training data and AI](04-training-data-and-ai.md)).
2. Run Android unit tests and build the app (last run: 68 tests, no failures):

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

Done: the model evaluation (95.0% on simulated test steps; 86.5% for the app's
own rule), the TensorFlow Lite check, and a load test on an Android emulator.
Not confirmed: Firebase rule deployment, a full run of the app by an agent, and
timing on a physical device.

## Before real customer data

- Deploy and test the Firebase rules.
- Check that one company cannot access another company's data.
- Restore agent email verification.
- Decide who can access customer data and how long to keep it.
- Decide how to handle deletion requests and backups.
- Never commit customer data or Firebase service-account keys.

See the other numbered guides for setup and testing steps.
