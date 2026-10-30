# Design overrides

Where the app does something different from the design pictures, and why.

| Screen | Design | App | Why |
|---|---|---|---|
| Consent | Not shown as saved | Time and agent are saved with each session | The app must keep proof of consent |
| Consent | Short points only | "Read the full terms" opens the full text | The legal wording must stay |
| Form | Locked: Identification document, Document type, Nationality | Locked: full name, date of birth, ID type, ID number, politically exposed person, US person, terms | These are the app's real compliance questions; there is no Nationality question |
| Agents | Invite dates | Not shown | Invitations store no date |
| Sign-in | Register for admins only | Agents also get "New agent? Create account" | Agents had no other way in |
| Launch | Splash library | Theme-only launch screen (Android 12+ shows the dark one) | The splash library is not available offline |
| Whole app | Light and dark | Light only | Agreed with the team |
| Customer type | First card chosen | Nothing chosen until tapped | Avoids onboarding the wrong type by accident |
| Questions | Groups (Identity, Contact, Business, Money) | One list, searchable | Questions have no group saved |
| Questions | Edit / delete own questions | Add only | The database layer has no edit or delete yet |
| Questions | Only the company's industry | All standard questions | Not wired yet |
