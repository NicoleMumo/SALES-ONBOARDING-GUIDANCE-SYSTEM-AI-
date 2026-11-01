# 03 - AI, data and results

SalesMesh uses data in three ways. Two are trained models that run on the phone;
the third is deliberately plain counting.

| Part | What it does for the agent or admin | How |
| --- | --- | --- |
| Next-question suggestion | Marks the question worth asking next | Transformer model, TensorFlow Lite, on the phone |
| Risk warning | Warns when a customer may give up before finishing | Logistic regression (14 weights), on the phone |
| Consistency measure | Shows admins how closely sessions follow the standard | Counting; an anomaly-detection model was tried and rejected |

**Every result below comes from simulated data.** No real customer or agent data
was used. The numbers show the methods work on a realistic simulation, not how
well they work with real agents and customers.

## The simulated data

`ml/dataset.py` simulates onboarding sessions: agents with different skill,
speed and habits (most follow the form in order; some start with the ID document;
some swap neighbouring questions), customers with different readiness and
patience, an identity-check service that sometimes fails for a while, and
customers who give up when things go wrong or take too long. Each row is one
answered question (an event). Every setting is an assumption, written in one
place at the top of the file, to be replaced by measured values when real data
exists.

The dataset used for all results: 8,001 sessions, 120,656 events, 32 questions,
154 agents. It is split **by agent** (about 70% train, 15% validation, 15% test),
so every test agent is one the models never saw. The simulated banking and
telecom forms are the app's forms; a test fails if they drift apart.

```powershell
python ml/dataset.py generate --sessions 8000 --seed 2026
python ml/dataset.py check ml/data/onboarding_dataset_v3.csv
```

The CSV is written to `ml/data/` and is not committed (about 34 MB). The checker
reports data quality and the simple lookup baselines. Columns that describe how a
session ended (status, scenario, next field) are never model inputs.

## Next-question suggestion

**What it sees:** the order of questions answered so far (up to 29), the
industry, the customer type, whether the last answer passed its check, and a
short description of each question it may choose from. It never sees the
customer's answers. Because it compares descriptions, it can also score a
company's own questions that it was not trained on.

**How the app uses it:** while a required question is still unanswered, the app
asks the model to choose among the questions not yet answered correctly (at most
128) and tags its pick "Suggested next". If the model file is missing or cannot
be loaded, no tag is shown and the section order is the guide.

**Results** (unseen agents, the 16,533 steps where the app asks for a
suggestion, choosing only among the questions the app allows):

| Method | Right next question |
| --- | --- |
| The app's rule without AI: first unfinished required question | 86.5% |
| Lookup on customer type and current question | 86.5% |
| Lookup on customer type, previous and current question, last check | 93.0% |
| **Transformer model** | **95.0%** |
| Best possible (the right question was one of the allowed choices) | 97.9% |

- On the same steps the model was right and the app's rule wrong 1,449 times,
  and the reverse 41 times. Against the best lookup: 355 and 30. Both gaps are
  far larger than chance (McNemar z = 36.5 and 16.6).
- Histories never seen in training: 94.2%. Sessions longer than 20 steps: 91.5%
  (130 steps). On all 32 questions without the app's limits: first choice 95.0%,
  in the top three 98.7%.
- The TensorFlow Lite file gives the same answers as the trained model (largest
  difference under 0.000001, no different first choice), and it loads and
  answers in the app on an Android emulator.
- Two full training runs gave 95.0% and 95.2%: read it as "about 95%".

**Pass mark.** It was written after the first results were seen, so it describes
what was reached rather than a prediction: in at least two full runs, beat the
app's rule by at least 5 points, beat the lookup on the same steps by more than
chance, and export a TensorFlow Lite file that matches. Both runs met it.

**Limits.** One simulator, written for this project, whose standard order is the
app's order, which is partly why the app's own rule already scores 86.5%. Only
the standard forms were tested: not company questions, rule changes, or edits
that make an earlier answer wrong.

**Retraining.** Open `ml/onboarding_transformer.ipynb` in Google Colab (or a
Python 3.11+ environment with TensorFlow), upload the whole CSV and wait for the
upload to finish (the notebook stops if the file is cut off), then run all cells.
It saves `onboarding_model.tflite`, `preprocessing_meta.json` and
`onboarding_model_flexible.keras` to `ml/models/`. The first two are a matching
pair: always replace both. Loading the `.keras` file in Python needs the layers
in `ml/next_field_model.py`.

## Risk warning

**The question:** part-way through a session, will this customer give up before
finishing?

**What it sees:** only what the app already knows at that moment: how many
answers there have been, how many failed (in total and in a row), revisits, time
spent (total, longest answer, answers over 15 minutes), progress, the rates of
these, and whether it is banking and a business customer. No answers, no question
names, and nothing about how the session ended. The last step of each simulated
session is left out of training, so the model learns to warn early, not to notice
a session that has already ended.

**How it was chosen:** logistic regression and gradient boosting were compared
on the validation agents only; logistic regression did as well or better each
time, so the simpler model was kept (14 weights in `ml/models/risk_model.json`).
The test agents were scored once, at the end.

**Results** on unseen agents, three simulated datasets (different random seeds).
ROC-AUC is the chance that a session that was really abandoned is ranked above
one that was not; 0.5 is guessing.

| Dataset | Trained model | Old hand-set rule |
| --- | --- | --- |
| Seed 2026 | 0.737 | 0.587 |
| Seed 7 | 0.724 | 0.565 |
| Seed 99 | 0.716 | 0.549 |

- The pass mark, fixed before any result was seen, was to beat the old rule by at
  least 0.03 on every dataset. The gains were 0.15 to 0.17.
- The old hand-set rule is close to guessing; on the first third of a session it
  scores 0.51.
- In practice: the warning thresholds are the riskiest 20% (amber) and 10% (red)
  of steps, measured on validation agents. Warning on the riskiest 20% catches
  44% of later abandonments, and 30% of those warnings are right; at 10%, it
  catches 27% and 38% are right (averages over the three datasets). On seed 2026
  it is better late in a session (0.83) than in its first third (0.71). So it is
  a useful prompt to offer help, not a prediction to act on alone.
- In the app, each answer the agent finishes is one input to the warning, measured
  the way the training data was: its time runs from the previous checked answer
  (or from opening the form), so it includes asking and listening, not only
  typing. In the simulated data that is the step's duration plus the agent's
  response after the previous step (`session_steps` in `ml/consistency.py`); the
  first model left the response time out, so in the app about a quarter of steps
  reached amber instead of a fifth, and it was retrained. Progress is answers
  correct out of all the form's questions. If
  `risk_model.json` is missing or refused (for example thresholds outside 0 to
  1), the app uses the old hand-set rule.
- The simulated times are assumptions; real step times may be distributed
  differently, so the warning thresholds should be checked on real sessions.

**Retraining** (needs numpy and scikit-learn):

```powershell
python ml/consistency.py ml/data/onboarding_dataset_v3.csv `
  --out ml/models/risk_model.json --report ml/models/risk_model_report.json
```

It writes the model only if the pass mark is met.

**Why the planned F1 of 80% was not reached, and cannot be on this data.** The
proposal planned an F1-score of 80% for the warning. At the amber threshold the
model reaches 0.31, 0.40 and 0.35 on the three datasets (about 0.35). To see
whether a better model could do more, `ml/risk_ceiling.py` scores four warnings
under the same protocol:

| Warning (F1 at amber, per step) | Seed 2026 | Seed 7 | Seed 99 |
| --- | --- | --- | --- |
| Old hand-set rule | 0.18 | 0.25 | 0.20 |
| Shipped model (14 inputs) | 0.31 | 0.40 | 0.35 |
| Same model with 10 more inputs the app could compute | 0.31 | 0.40 | 0.36 |
| Model also told the client's hidden traits (ceiling) | 0.31 | 0.43 | 0.34 |

The ceiling model knows what no app can: the client's patience, whether they
brought their documents, their literacy and phone, and the agent's skill (taken
from the generator by re-running it with the dataset's seed, which reproduces
every event). It does at most 0.04 better than the shipped model. Even choosing
each model's threshold by looking at the test answers, which is not a fair
result, no warning passes 0.44. The reason is in the simulator: a session ends by a
chance draw at each step, which rises after failures in a row and once the
client's patience runs out, so whether a session will be abandoned is partly
luck until the moment it happens. The shipped model is already at the limit of
this data; more inputs or a bigger model would not reach 80%. Only real sessions
can show whether real abandonment is more predictable.

```powershell
python ml/risk_ceiling.py --report ml/models/risk_ceiling_report.json
```

## Consistency measure, and the model that was rejected

The measure itself (at most 2 corrected answers and at most 2 questions out of
order) is described in [02 - How the app works](02-how-the-app-works.md). On the
simulated data, 81% of completed sessions meet it, and among the 134 agents with
at least 20 completed sessions the share ranges from 28% to 100% (median 96%), so
it does tell agents apart.

An **Isolation Forest** (an unsupervised anomaly detector) was tried first, to
flag unusual sessions without needing labelled examples. It was tested by
planting known deviations in held-out sessions, against a pass mark fixed in
advance (catch at least 80%, flag at most 10% of normal sessions). It failed:
it caught 67% to 74% of planted deviations, and flagged 10.5% of normal
sessions on one dataset. Its weakness was exactly what matters here: when the
only fault was the order of questions, it caught 8% to 14%, while the plain
out-of-order count caught 97% to 98%. So the app uses the plain count, which is
exact and easy to explain. The detector and its results are kept in `legacy/`.

## Model files

| File in `ml/models/` | Used by |
| --- | --- |
| `onboarding_model.tflite` and `preprocessing_meta.json` | The app (next-question suggestion); a matching pair |
| `risk_model.json` | The app (risk warning) |
| `onboarding_model_flexible.keras` | Python, to reload the trained model |
| `risk_model_report.json` | The evidence for the risk results above |
| `risk_ceiling_report.json` | The evidence for the F1 ceiling above |

The build copies the three app files into `android/app/src/main/assets/`.

## Speed on a phone

Run on a connected physical phone, from the `android` folder:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.example.salesonboarding.TfliteNextFieldPredictorBenchmark"
```

It runs 50 warm-ups and 500 timed suggestions and writes the device, Android
version, median and 95th-percentile time to Logcat under
`NextFieldInferenceBenchmark`. No physical-phone result has been recorded yet;
emulator timing does not count.

## Not yet shown

- Anything about real agents or customers. A small pilot with role-played
  sessions is planned; its numbers will be reported separately from the
  simulated ones.
- Speed on a physical phone.
- Deployed and tested Firebase rules, agent email verification, and a privacy
  and retention plan (see [01 - Set up and run](01-set-up-and-run.md) and
  [02 - How the app works](02-how-the-app-works.md)).
