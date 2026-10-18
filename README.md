# SALES-ONBOARDING-GUIDANCE-SYSTEM-AI-


## Mentor review progress

Roadmap work is split into dataset, model, Android app, and database/authentication chores. Dataset reproducibility is committed on `chore/dataset-reproducibility`.

### Committed and pushed branches

All four chore branches have been pushed to origin and are ready for review.

- Per-event completion duration scoring and regression coverage are committed on `fix/per-event-risk-duration-mentor`.
- Reproducible next-field baselines and evaluation documentation are committed on `chore/model-risk-evaluation-ready`.
- Context-aware workflow inputs, event handling, and integration tests are committed on `chore/android-app-integration-ready`.
- Firebase ownership rules, Emulator configuration/tests, and setup guidance are committed on `chore/database-authentication-ready`.


### Still required before calling the roadmap complete

- **Dataset:** confirm generated split assignments and agent-level separation against the final committed dataset; document the exact generation command and seed. Treat all generated data and results as synthetic.
- **Model:** run the notebook from a clean runtime for session and agent splits, complete held-out-field experiments, save metrics, compare all baselines, and measure median/p95 latency on a named physical Android device. Current synthetic evaluation tooling is not completed performance evidence.
- **Android app:** finish authenticated sign-in and trusted-claim routing in the live app flow, consent and recoverable model/Firebase error behavior, Firebase Emulator end-to-end agent-flow coverage, and CI. Existing visual screen edits remain uncommitted and need their own review. Verify the app builds and the full test suite passes from a clean checkout.
- **Database/authentication:** wire Firebase Authentication and UID ownership through all app data paths, provision admin claims through a secured Admin SDK procedure, run allow/deny tests against the Firebase Emulator, and review/deploy the rules. Do not use real customer data until these checks pass.
- **Evidence/governance:** approve a real-data privacy and retention plan before collecting real sessions. Synthetic metrics do not establish real-world prediction performance. Add the agreed project licence and complete the README demo/AI-use/results material after validated evidence is available.

Do not use real customer data until authentication, ownership rules, and emulator tests are complete.
