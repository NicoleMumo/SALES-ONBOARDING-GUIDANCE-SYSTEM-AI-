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

**Two versions.** Version 1 (`ml/onboarding_transformer.ipynb`, now in
`ml/models/v1/`) is the one reported in most of this section. Version 2
(`ml/next_question_v2.py`), shipped since 8 Nov 2026, is the same Transformer
with one addition that lets it handle a company's own questions (see "New
questions" below). Overall they score the same.

**What it sees:** the order of questions answered so far (up to 29), the
industry, the customer type, whether the last answer passed its check, and for
each question it may choose from: a short description and, in version 2, where
that question sits in the form (its position, its distance from the last
question answered, whether it is the first open one, whether it is the plain
rule's choice, and whether it is required). It never sees the customer's answers.

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

**Where the gain comes from.** `ml/offscript_breakdown.py` rebuilds the same 16,533
steps, scores the shipped TensorFlow Lite file the way the app calls it (it reproduces
the 95.0%, 86.5% and 93.0% above), and splits the steps by session. A session "left the
standard order" if at least one question was out of place. That label uses the whole
session, so it is for analysis only; no method sees it. Results are saved in
`ml/models/offscript_report.json`.

| Sessions | Steps (sessions) | App's rule | Best lookup | Transformer |
| --- | --- | --- | --- | --- |
| Kept the standard order | 8,790 (643) | 92.7% | 97.6% | 97.7% |
| Left the standard order | 7,743 (539) | 79.5% | 87.8% | 92.0% |
| Banking | 8,612 (601) | 83.2% | 92.8% | 95.0% |
| Telecom | 7,921 (581) | 90.1% | 93.3% | 95.0% |

- When agents kept to the standard order, the model was no better than the lookup
  (5 steps against 1, McNemar z = 1.6).
- Almost all of its advantage over the lookup is in sessions that left the standard
  order: 350 of the 355 steps where it beat the lookup are there (z = 16.5). Against
  the app's rule the gain is wider spread: 1,004 of 1,449 wins (69%) are there.
- So the model works as a safety net for agents who go off-script, not as a
  replacement for the standard order. The departures were written into the
  simulator, so this still needs checking with real agents.

**Pass mark.** It was written after the first results were seen, so it describes
what was reached rather than a prediction: in at least two full runs, beat the
app's rule by at least 5 points, beat the lookup on the same steps by more than
chance, and export a TensorFlow Lite file that matches. Both runs met it.

**Limits.** One simulator, written for this project, whose standard order is the
app's order, which is partly why the app's own rule already scores 86.5%. Only
the standard forms were tested: not rule changes or edits that make an earlier
answer wrong.

**New questions (tested, 8 Nov 2026).** Version 1 compared sessions with
question descriptions only, which was meant to let it score a company's own
questions without retraining. `ml/custom_question_check.py` tested that: four
standard questions (email address, profession, US person, payment method; one
from each admin question group, chosen before any result) were held out of
training completely, as if a company had added them later: removed from the
candidates, the targets, every answered history (as the app does with a
question it does not know) and the description word list. The model was then
trained again from scratch. The pass mark, fixed before running: pick the
held-out question first at least 50% of the time when it is the right next one,
and have it in the top 3 at least 80% of the time.

Version 1 failed: it never once chose a question it was not trained on. A new
question's description is mostly unknown words to it, and the description was
all it had. But any question, including a company's own, has a place in the
form, which the app knows. Version 2 adds those five facts about each candidate
(above), so it can learn the general habit ("agents usually go on to the next
open question, except when ...") as well as each question's own pattern. The
same held-out test, run on version 2 (`ml/next_question_v2.py holdout`):

| Steps where a held-out question was next (2,927) | First choice | In top 3 |
|---|---|---|
| Version 1, never saw them | 0% | 42% |
| **Version 2, never saw them** | **98.8%** | **100%** |
| Random choice among the open questions | 16% | |
| Version 1 trained with them | 98.5% | |

Each held-out question: email 98.3%, profession 100%, US person 100%, payment
method 97.5%. On the other steps the version 2 trained without them scored
93.5% (version 1 with everything: 94.8%), and it picked a held-out question
wrongly on 2.5% of them.

**Version 2 replaced version 1** because it met three conditions fixed before
it was trained: it passed this test; on the app's 16,533 suggestion steps it was
no more than 0.5 points below version 1 (it scored 95.2% against 95.0%; right
where version 1 was wrong 44 times, the reverse 20, z = 3.0); and its TensorFlow
Lite file matches the trained model (largest difference 0.0000001, no different
first choice in 50 checks). Off-script sessions 92.2% (version 1 92.0%, lookup
87.8%); standard-order sessions 97.7% (the same as version 1). One training run
each; results in `ml/models/next_question_v2_report.json` and
`ml/models/custom_question_check_v2.json`.

What this does not show: the held-out questions came from the simulator, which
places them in the form's usual order. A company question is placed at the end
of the form, and agents may ask it anywhere; the model will suggest it where
the form puts it until real sessions show otherwise.

```powershell
python ml/next_question_v2.py holdout ml/data/onboarding_dataset_v3.csv   # the held-out test
python ml/next_question_v2.py train   ml/data/onboarding_dataset_v3.csv   # train, compare, export to ml/models/
```

**Try it yourself.** `ml/try_next_question.py` calls the phone's model file:

```powershell
# Rank the open questions after the ones answered so far
python ml/try_next_question.py ask --industry banking --context individual --answered full_name,email_address
# Add questions a company made up (the model never saw them)
python ml/try_next_question.py ask --industry banking --context individual --answered full_name --company-question "Loyalty number: the client's loyalty card number"
# Follow a real test session step by step, next to the app's plain rule
python ml/try_next_question.py walk ml/data/onboarding_dataset_v3.csv --session SESSION-4CD01894
```

In that session the agent starts with the ID document. Version 1 followed the
agent's flow and was right on 14 of 15 steps, version 2 on 13; the plain rule
keeps asking for the next question in the form's order and is right on 7. In
the 219 test sessions of 10 to 20 answers that left the form's order, version 1
was right 2,802 times and the rule 2,244 times. Where the agent keeps to the
order, the two agree almost everywhere. With two made-up company questions
(loyalty number, branch) at the end of a banking form, version 2 suggests the
terms while they are open (99.6%), then the loyalty number (90%), then the branch.

**Retraining.** Version 2: `python ml/next_question_v2.py train <csv>` (a few
minutes on a laptop CPU) writes `onboarding_model.tflite`,
`preprocessing_meta.json` and `next_question_v2_report.json` to `ml/models/`.
The first two are a matching pair: always replace both. Version 1: open
`ml/onboarding_transformer.ipynb` in Google Colab, upload the whole CSV, run all
cells; its files are kept in `ml/models/v1/` for comparison. Loading version 1's
`.keras` file in Python needs the layers in `ml/next_field_model.py`.

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

**Check with more realistic abandonment (v4).** Real users show strain before
they give up: error messages, failed connections and long waits are the main
causes of frustration, and frustration grows with time lost (Ceaparu et al.,
2004). The generator's `--version v4` adds this: frustration builds with failed
answers, verification failures and long steps, and eases after answers that
pass; frustrated or less committed clients slow down and make more mistakes;
and the chance of giving up rises with that strain. Its effect sizes were set
before any model was trained, and only one scale was calibrated, so that the
share of abandoned sessions stays at v3's level (about 18%). `ml/risk_v4_check.py`
trains the risk model on v4 with the same protocol:

| Seed | Abandoned sessions | ROC-AUC (old rule) | F1 at amber | Failed answers in the last 3 steps, abandoned vs completed |
| --- | --- | --- | --- | --- |
| 2026 | 18.1% | 0.738 (0.585) | 0.32 | 12.2% vs 3.0% |
| 7 | 17.4% | 0.754 (0.573) | 0.36 | 11.4% vs 3.1% |
| 99 | 18.0% | 0.755 (0.579) | 0.36 | 10.5% vs 2.8% |

Clear warning signs make the ranking a little better (0.74 to 0.75, against
0.72 to 0.74 on v3), but F1 stays about 0.35: the target scores every step of
a session that is later abandoned, including its first answers, when a client
who will give up still looks like any other. The shipped model stays trained
on v3.

```powershell
python ml/risk_v4_check.py --report ml/models/risk_v4_check.json
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
| `onboarding_model.tflite` and `preprocessing_meta.json` | The app (next-question suggestion, version 2); a matching pair |
| `risk_model.json` | The app (risk warning) |
| `v1/` | Version 1 of the next-question model and its metadata, for the comparisons above |
| `risk_model_report.json` | The evidence for the risk results above |
| `risk_ceiling_report.json` | The evidence for the F1 ceiling above |
| `risk_v4_check.json` | The evidence for the v4 check above |

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

## The warning as the app shows it

The numbers above score every step against the amber and red thresholds. The
app does not show those raw levels: it paces them (`RiskWarningPacer` in
`Insights.kt`): nothing for the first 4 checked answers, "Check in" before
"Needs help", "Needs help" only after 3 high scores in a row.
`ml/risk_policy_replay.py` replays every test session (unseen agents) answer by
answer through the shipped model and the same pacer:

| Shown to the agent | Abandoned sessions warned | Median warning, answers before the end | Completed sessions warned (false alarms) | Warned sessions that were abandoned |
|---|---|---|---|---|
| "Needs help" | 19-27% | 7-11.5 | 8-9% | 34-46% |
| "Check in" or more | 38-42% | 6-8 | 16-17% | 29-43% |
| Raw amber, no pacing | 66-75% | 8-10 | 48-53% | 19-29% |

Ranges over the three datasets (seeds 2026, 7, 99), whose test sessions were
14.5%, 22.3% and 20.2% abandoned. A "Needs help" warning is followed by
abandonment about 2.1 to 2.3 times as often as chance, and usually comes
several answers early. Pacing trades reach for calm: without it, half of the
sessions that finished fine would have shown a warning.

```powershell
python ml/risk_policy_replay.py ml/data/onboarding_dataset_v3.csv
```

## Not yet shown

- Anything about real agents or customers. A small pilot with role-played
  sessions is planned; its numbers will be reported separately from the
  simulated ones.
- Speed on a physical phone.
- Deployed and tested Firebase rules, agent email verification, and a privacy
  and retention plan (see [01 - Set up and run](01-set-up-and-run.md) and
  [02 - How the app works](02-how-the-app-works.md)).
