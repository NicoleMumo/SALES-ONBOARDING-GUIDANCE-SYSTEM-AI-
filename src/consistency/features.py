"""Session features for the risk and anomaly models, and the app's current rule.

Every feature here must be computable by the Android app at the moment it would
warn the agent. The app holds, for each step so far, exactly four things (see
`CompletionRiskEvent` in the app): the step's duration in minutes, whether its
check passed, whether it was a revisit, and the progress percent afterwards.
Nothing else is used -- in particular no customer answer, no field name, and
nothing about how the session ends.

Plain Python on purpose: the same arithmetic has to be reproduced in Kotlin.
"""

from dataclasses import dataclass

from playbooks import standard_order

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


# --- Whole-session features, for the anomaly detector and the consistency score ---

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
    anomaly detector does (see `anomaly.py`).
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
