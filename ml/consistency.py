"""Measure how consistently an onboarding session is going, and train the risk model.

Separate from the next-field model: this looks at *how* a session is running
(retries, time, revisits, progress, step order), not at which field comes next.
It holds the app's standard field order, the session features, the app's
current hand-set risk rule, and the training of the risk model that replaces it.

Every feature here must be computable by the Android app at the moment it would
warn the agent. The app holds, for each step so far, exactly four things (see
`CompletionRiskEvent` in the app): the step's duration in minutes, whether its
check passed, whether it was a revisit, and the progress percent afterwards.
Nothing else is used -- in particular no customer answer, no field name, and
nothing about how the session ends.

The feature code is plain Python on purpose: the same arithmetic has to be
reproduced in Kotlin, and its tests run without numpy. Only training imports
numpy and scikit-learn.

Risk model protocol, fixed before any result was looked at:
  * Rows are split by agent (the dataset's own split column), so the test set
    only has agents no model saw in training.
  * Candidate models are compared on the validation agents only. The test agents
    are scored once, at the end, with the already-chosen model.
  * The score is ROC-AUC: the chance that a session that really was abandoned is
    ranked above one that was not. 0.5 is guessing.
  * Pass mark: the trained model must beat the app's current rule by at least
    0.03 AUC on the test agents, on every seed. Logistic regression is kept
    unless gradient boosting beats it by at least 0.02 AUC on validation,
    because the simpler model is far easier to put in the app.

Usage:
    .\\.venv-ml\\Scripts\\python.exe ml/consistency.py \\
        ml/data/onboarding_dataset_v3.csv [more datasets...] \\
        --out ml/models/risk_model.json --report ml/models/risk_model_report.json
"""

import argparse
import csv
import json
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

# ---------------------------------------------------------------------------
# The app's standard field order, copied from the Kotlin playbooks.
#
# `tests/test_consistency.py` reads `OnboardingPlaybook.kt` and fails if this
# copy drifts from it, and `tests/test_dataset.py` checks the generator against
# this copy. Change `OnboardingPlaybook.kt` and these together.
# ---------------------------------------------------------------------------
BANKING_INDIVIDUAL = [
    "full_name", "email_address", "mobile_phone_number", "date_of_birth",
    "home_address", "identification_document", "identification_document_type",
    "identification_document_number", "profession", "income", "assets",
    "us_person_status", "politically_exposed_person_status",
    "terms_and_conditions_acceptance",
]
BANKING_BUSINESS = [
    "full_name", "business_name", *BANKING_INDIVIDUAL[1:],
    "commercial_address", "beneficial_owner_information",
    "business_relationship_purpose", "business_relationship_nature",
    "ownership_and_control_information",
]
TELECOM_BASE = [
    "full_name", "email_address", "contact_phone_number", "date_of_birth",
    "gender", "physical_address", "identification_document", "subscriber_type",
    "service_type", "contract_type", "payment_method",
    "paperless_billing_preference",
]


def standard_order(industry, context):
    """The order the app guides an agent through, for this form."""
    if industry == "banking":
        if context == "individual":
            return BANKING_INDIVIDUAL
        if context == "business":
            return BANKING_BUSINESS
        raise ValueError(f"No banking playbook for context {context!r}")
    if industry == "telecom":
        fields = list(TELECOM_BASE)
        if context in {"mobile", "landline"}:
            fields.append("phone_service_selection")
        elif context in {"broadband", "tv"}:
            fields.extend(["internet_service_selection", "streaming_addons_selection"])
        elif context != "iot":
            raise ValueError(f"No telecom playbook for context {context!r}")
        return fields + ["subscriber_number"]
    raise ValueError(f"No playbook for industry {industry!r}")


# ---------------------------------------------------------------------------
# Step features for the risk model, and the app's current rule
# ---------------------------------------------------------------------------

# Same threshold as the app's CompletionRiskCalculator.
LONG_EVENT_THRESHOLD_MINUTES = 15.0

# The order of FEATURE_NAMES is the order of the vectors this module returns and
# the order of the weights exported for the app. Keep them in step.
FEATURE_NAMES = (
    "events",
    "failures",
    "consecutive_failures",
    "revisits",
    "elapsed_minutes",
    "longest_event_minutes",
    "long_events",
    "progress_percent",
    "failure_rate",
    "revisit_rate",
    "minutes_per_event",
    "progress_per_minute",
    "is_banking",
    "is_business",
)


@dataclass(frozen=True)
class Step:
    """One completed step of a session, as the app already records it."""

    duration_minutes: float
    validation_passed: bool
    is_revisit: bool
    progress_percent: float


def features(steps, is_banking, is_business):
    """Feature vector after [steps], in FEATURE_NAMES order.

    [steps] must not be empty: the app only scores a session once a step is done.
    """
    if not steps:
        raise ValueError("features() needs at least one completed step.")

    events = len(steps)
    failures = sum(1 for step in steps if not step.validation_passed)
    revisits = sum(1 for step in steps if step.is_revisit)
    elapsed = sum(step.duration_minutes for step in steps)
    longest = max(step.duration_minutes for step in steps)
    long_events = sum(
        1 for step in steps if step.duration_minutes > LONG_EVENT_THRESHOLD_MINUTES
    )
    progress = steps[-1].progress_percent

    consecutive_failures = 0
    for step in reversed(steps):
        if step.validation_passed:
            break
        consecutive_failures += 1

    return [
        float(events),
        float(failures),
        float(consecutive_failures),
        float(revisits),
        elapsed,
        longest,
        float(long_events),
        progress,
        failures / events,
        revisits / events,
        elapsed / events,
        # Progress per minute: slow sessions score low. Guarded against 0 minutes.
        progress / elapsed if elapsed > 0 else 0.0,
        1.0 if is_banking else 0.0,
        1.0 if is_business else 0.0,
    ]


def app_rule_score(steps):
    """The app's current hand-set risk score, reproduced exactly.

    Mirrors CompletionRiskScorer.score + CompletionRiskCalculator.calculate in the
    Android app, including its use of the *longest* step rather than total time.
    Used as the baseline the trained model has to beat.
    """
    if not steps:
        raise ValueError("app_rule_score() needs at least one completed step.")

    validation_errors = sum(1 for step in steps if not step.validation_passed)
    field_revisits = sum(1 for step in steps if step.is_revisit)
    long_events = sum(
        1 for step in steps if step.duration_minutes > LONG_EVENT_THRESHOLD_MINUTES
    )
    progress = steps[-1].progress_percent

    score = 0.05
    if validation_errors > 0:
        score += 0.25
    if long_events > 0:
        score += 0.15
    if field_revisits > 0:
        score += 0.10 * field_revisits
    if progress < 50.0:
        score += 0.10
    return min(max(score, 0.0), 1.0)


# ---------------------------------------------------------------------------
# Whole-session features, for the consistency score (and the retired anomaly detector)
# ---------------------------------------------------------------------------

SESSION_FEATURE_NAMES = (
    "steps",
    "out_of_order_steps",
    "retries",
    "revisits",
    "extra_steps",
    "minutes_per_step",
)


def out_of_order_steps(fields, expected):
    """The fewest steps that would have to move to put this session in order.

    Counting every step that follows an early jump would blame the innocent: in
    `a, d, b, c` only `d` is misplaced, yet `b` and `c` both come after it. So
    this keeps the longest run of steps that are already in the standard order
    (not necessarily next to each other) and counts the rest, which is the number
    an administrator would recognise as "steps out of place".

    Repeats of a field are ignored here; `revisits` covers those separately, and
    fields outside the standard order (a company's custom fields) are skipped.

    This plain count is the project's order-consistency measure. It is exact and
    explainable, and it detects a single misplaced step far better than the
    anomaly detector did (see `legacy/src/consistency/anomaly.py`).
    """
    rank = {name: position for position, name in enumerate(expected)}
    ranks, seen = [], set()
    for field in fields:
        if field in seen or field not in rank:
            continue
        seen.add(field)
        ranks.append(rank[field])

    # Longest run of steps already in standard order (longest increasing
    # subsequence). Sessions are at most a few dozen steps, so the simple
    # quadratic method is fast enough and easy to re-read in Kotlin.
    longest_in_order = 0
    best_ending_here = [1] * len(ranks)
    for position, value in enumerate(ranks):
        for earlier in range(position):
            if ranks[earlier] < value:
                best_ending_here[position] = max(
                    best_ending_here[position], best_ending_here[earlier] + 1
                )
        longest_in_order = max(longest_in_order, best_ending_here[position])
    return len(ranks) - longest_in_order


def is_true(value):
    return str(value).strip().lower() in {"true", "1", "yes"}


def session_features(events, industry, context):
    """One feature vector per finished session, in SESSION_FEATURE_NAMES order."""
    if not events:
        raise ValueError("session_features() needs at least one event.")
    expected = standard_order(industry, context)
    steps = len(events)
    minutes = sum(float(event["event_duration_minutes"]) for event in events)
    return [
        float(steps),
        float(out_of_order_steps([event["onboarding_field"] for event in events], expected)),
        float(sum(1 for event in events if event["validation_status"] != "passed")),
        float(sum(1 for event in events if is_true(event["is_revisit"]))),
        float(max(steps - len(expected), 0)),
        minutes / steps,
    ]


def session_steps(events):
    """Turn one session's CSV rows (in time order) into [Step]s."""
    steps = []
    for event in events:
        steps.append(
            Step(
                duration_minutes=float(event["event_duration_minutes"]),
                validation_passed=event["validation_status"] == "passed",
                is_revisit=is_true(event["is_revisit"]),
                progress_percent=float(event["onboarding_progress"]),
            )
        )
    return steps


def rows_from_sessions(sessions):
    """One training row per step, for every session.

    [sessions] is an iterable of (session_id, agent_id, industry, context, events).

    The final step of each session is skipped: at that point the session is
    already over, so predicting its outcome is not a warning, it is hindsight.
    Every row therefore asks the honest question: given what has happened so far,
    will this session be abandoned later?
    """
    rows = []
    for session_id, agent_id, industry, context, events in sessions:
        steps = session_steps(events)
        if len(steps) < 2:
            continue
        abandoned = events[-1]["session_status"] == "abandoned"
        is_banking = industry == "banking"
        is_business = context == "business"
        for position in range(1, len(steps)):
            prefix = steps[:position]
            rows.append(
                {
                    "session_id": session_id,
                    "agent_id": agent_id,
                    "features": features(prefix, is_banking, is_business),
                    "app_rule": app_rule_score(prefix),
                    "abandoned": abandoned,
                    "position": position,
                    "session_length": len(steps),
                }
            )
    return rows


# ---------------------------------------------------------------------------
# Train and honestly test the risk model against the app's current rule
# ---------------------------------------------------------------------------

MIN_AUC_GAIN_OVER_APP_RULE = 0.03
MIN_AUC_GAIN_TO_PREFER_BOOSTING = 0.02

# Columns that must never become features: they describe how the session ended,
# or they restate the label.
FORBIDDEN_AS_FEATURES = (
    "session_status",
    "onboarding_scenario",
    "onboarding_risk_score",
    "onboarding_risk_level",
    "next_onboarding_field",
    "real_time_guidance",
)


def load_split_rows(path):
    """Read one dataset and build training rows, grouped by split."""
    sessions = defaultdict(list)
    meta = {}
    with open(path, newline="", encoding="utf-8") as handle:
        for event in csv.DictReader(handle):
            sessions[event["session_id"]].append(event)
            meta[event["session_id"]] = (
                event["sales_agent_id"],
                event["industry"],
                event["context_value"],
                event["split"],
            )

    by_split = defaultdict(list)
    for session_id, events in sessions.items():
        events.sort(key=lambda event: event["event_timestamp"])
        agent_id, industry, context, split = meta[session_id]
        by_split[split].append((session_id, agent_id, industry, context, events))

    return {split: rows_from_sessions(group) for split, group in by_split.items()}


def as_arrays(rows):
    import numpy as np

    x = np.asarray([row["features"] for row in rows], dtype=np.float64)
    y = np.asarray([row["abandoned"] for row in rows], dtype=np.int32)
    app_rule = np.asarray([row["app_rule"] for row in rows], dtype=np.float64)
    return x, y, app_rule


def check_no_agent_crosses_splits(splits):
    agents = {split: {row["agent_id"] for row in rows} for split, rows in splits.items()}
    for first in agents:
        for second in agents:
            if first < second and agents[first] & agents[second]:
                raise SystemExit(
                    f"Agents appear in both '{first}' and '{second}'; the test would be unfair."
                )


def evaluate(path, verbose=True):
    import numpy as np
    from sklearn.ensemble import HistGradientBoostingClassifier
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import average_precision_score, roc_auc_score
    from sklearn.preprocessing import StandardScaler

    with open(path, newline="", encoding="utf-8") as handle:
        header = next(csv.reader(handle))
    leaked = [name for name in FORBIDDEN_AS_FEATURES if name in FEATURE_NAMES]
    if leaked:
        raise SystemExit(f"Outcome columns used as features: {leaked}")

    splits = load_split_rows(path)
    missing = {"train", "validation", "test"} - set(splits)
    if missing:
        raise SystemExit(f"{path} has no {sorted(missing)} rows.")
    check_no_agent_crosses_splits(splits)

    train_x, train_y, _ = as_arrays(splits["train"])
    val_x, val_y, val_rule = as_arrays(splits["validation"])
    test_x, test_y, test_rule = as_arrays(splits["test"])

    scaler = StandardScaler().fit(train_x)
    logistic = LogisticRegression(max_iter=2000, class_weight="balanced")
    logistic.fit(scaler.transform(train_x), train_y)
    boosting = HistGradientBoostingClassifier(
        max_iter=200, learning_rate=0.1, random_state=0
    )
    boosting.fit(train_x, train_y)

    val_logistic = roc_auc_score(val_y, logistic.predict_proba(scaler.transform(val_x))[:, 1])
    val_boosting = roc_auc_score(val_y, boosting.predict_proba(val_x)[:, 1])
    chosen_name = (
        "gradient_boosting"
        if val_boosting - val_logistic >= MIN_AUC_GAIN_TO_PREFER_BOOSTING
        else "logistic_regression"
    )

    def score(model_name, x):
        if model_name == "logistic_regression":
            return logistic.predict_proba(scaler.transform(x))[:, 1]
        return boosting.predict_proba(x)[:, 1]

    test_scores = score(chosen_name, test_x)
    result = {
        "dataset": str(path),
        "rows": {name: len(rows) for name, rows in splits.items()},
        "abandoned_share": {
            name: round(float(as_arrays(rows)[1].mean()), 4) for name, rows in splits.items()
        },
        "validation_auc": {
            "logistic_regression": round(val_logistic, 4),
            "gradient_boosting": round(val_boosting, 4),
            "app_rule": round(roc_auc_score(val_y, val_rule), 4),
        },
        "chosen_model": chosen_name,
        "test_auc": {
            "model": round(float(roc_auc_score(test_y, test_scores)), 4),
            "app_rule": round(float(roc_auc_score(test_y, test_rule)), 4),
        },
        "test_average_precision": {
            "model": round(float(average_precision_score(test_y, test_scores)), 4),
            "app_rule": round(float(average_precision_score(test_y, test_rule)), 4),
        },
    }
    result["test_auc"]["gain_over_app_rule"] = round(
        result["test_auc"]["model"] - result["test_auc"]["app_rule"], 4
    )
    result["passes_mark"] = (
        result["test_auc"]["gain_over_app_rule"] >= MIN_AUC_GAIN_OVER_APP_RULE
    )

    if verbose:
        print(f"\n=== {path}")
        print(f"rows {result['rows']} | abandoned share {result['abandoned_share']}")
        print(f"validation AUC {result['validation_auc']} -> chose {chosen_name}")
        print(
            f"test AUC: model {result['test_auc']['model']:.4f} vs "
            f"app rule {result['test_auc']['app_rule']:.4f} "
            f"(gain {result['test_auc']['gain_over_app_rule']:+.4f}) "
            f"-> {'PASS' if result['passes_mark'] else 'FAIL'}"
        )

    export = None
    if chosen_name == "logistic_regression":
        # Fold the scaler into the weights so the app needs only a dot product.
        weights = logistic.coef_[0] / scaler.scale_
        intercept = float(logistic.intercept_[0] - (logistic.coef_[0] * scaler.mean_ / scaler.scale_).sum())
        # Warning thresholds come from the validation agents, never the test ones:
        # "medium" is the riskiest 20% of steps, "high" the riskiest 10%. Scores
        # from a class-balanced model are rankings, not true probabilities, so
        # these have to be measured rather than guessed.
        val_scores = logistic.predict_proba(scaler.transform(val_x))[:, 1]
        medium, high = (float(np.quantile(val_scores, share)) for share in (0.80, 0.90))
        export = {
            "model": "logistic_regression",
            "feature_names": list(FEATURE_NAMES),
            "weights": [round(float(weight), 6) for weight in weights],
            "intercept": round(intercept, 6),
            "medium_risk_threshold": round(medium, 4),
            "high_risk_threshold": round(high, 4),
            "long_event_threshold_minutes": LONG_EVENT_THRESHOLD_MINUTES,
            "note": (
                "score = 1 / (1 + exp(-(intercept + sum(weights * features)))). "
                "Thresholds are the 80th and 90th percentile of validation scores, "
                "so about 20% of steps reach medium and 10% high. "
                "Trained on simulated sessions only; not a real-world probability."
            ),
        }
        check = 1.0 / (1.0 + np.exp(-(intercept + test_x @ weights)))
        largest_difference = float(np.max(np.abs(check - test_scores)))
        result["export_matches_sklearn_to"] = largest_difference
        if verbose:
            print(f"exported weights reproduce sklearn to {largest_difference:.2e}")

    return result, export


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("datasets", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, help="where to write the model for the app")
    parser.add_argument("--report", type=Path, help="where to write the full results as JSON")
    arguments = parser.parse_args()

    results, exports = [], []
    for path in arguments.datasets:
        result, export = evaluate(path)
        results.append(result)
        exports.append(export)

    gains = [result["test_auc"]["gain_over_app_rule"] for result in results]
    all_pass = all(result["passes_mark"] for result in results)
    print("\n=== Pass mark: beat the app's rule by >= "
          f"{MIN_AUC_GAIN_OVER_APP_RULE} AUC on every dataset")
    print(f"gains: {gains} -> {'PASS' if all_pass else 'FAIL'}")

    if arguments.report:
        arguments.report.parent.mkdir(parents=True, exist_ok=True)
        arguments.report.write_text(
            json.dumps({"datasets": results, "all_pass": all_pass}, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"wrote {arguments.report}")

    if arguments.out:
        if not all_pass:
            print("Not exporting a model: the pass mark was not met.")
        elif exports[0] is None:
            print("Not exporting: the chosen model is not the exportable one.")
        else:
            arguments.out.parent.mkdir(parents=True, exist_ok=True)
            arguments.out.write_text(json.dumps(exports[0], indent=2) + "\n", encoding="utf-8")
            print(f"wrote {arguments.out}")


if __name__ == "__main__":
    main()
