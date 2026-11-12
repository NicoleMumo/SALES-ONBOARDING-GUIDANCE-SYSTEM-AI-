"""Checks the next-question model against overfitting and against a real onboarding log.

Part 1 scores the shipped model on the training, validation and test sales agents in
the same way (the app's suggestion steps). A large gap between training and the
others would mean the model memorised its training data.

Part 2 uses a real, public event log of digital onboarding at a Brazilian fintech
(Mcamilo/Onboarding-Fintech-Event-Log on GitHub, from "Using Process Mining to
Reduce Fraud in Digital Onboarding", FinTech 2023, doi:10.3390/fintech2010009).
Ten of its steps are questions of the app's banking form for individuals. For each
real case, the order in which those ten were first answered is replayed, and the
shipped model, the app's plain rule and a lookup learned from half of the real cases
predict each next question. The other half is scored. The model never saw real data.

Part 3 compares simple patterns of the simulator with the real log on the same ten
questions: how often the order differs from the form, how often an answer is entered
again, and how often a case reaches the terms question.

The log is not in the repository. Download it, then:
    python ml/real_log_check.py --event-log path/to/do_eventlog.csv
"""
import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent))
from consistency import out_of_order_steps, standard_order  # noqa: E402
from offscript_breakdown import required_fields  # noqa: E402
from try_next_question import ShippedModel  # noqa: E402

# Steps of the real log that are the same question as one in the app's banking form.
# Steps that combine several questions (PersonalInfoInsertion, DocumentDataInsertion) are left out.
REAL_TO_APP = {
    "EmailInsertion": "email_address",
    "MobilePhoneNumberInsertion": "mobile_phone_number",
    "BirthDateInsertion": "date_of_birth",
    "HomeAddressInsertion": "home_address",
    "ProfessionInsertion": "profession",
    "IncomeInsertion": "income",
    "AssetsInsertion": "assets",
    "USPersonInsertion": "us_person_status",
    "PoliticalExpositionInsertion": "politically_exposed_person_status",
    "TermsAcceptanceInsertion": "terms_and_conditions_acceptance",
}
SHARED = set(REAL_TO_APP.values())


def pct(x):
    return round(100.0 * float(np.mean(x)), 1) if len(x) else None


# Part 1 -------------------------------------------------------------------------------

def app_steps(df, split):
    """The steps where the app asks for a suggestion, exactly as in the main evaluation."""
    out = []
    for _, g in df[df["split"] == split].groupby("session_id", sort=True):
        g = g.reset_index(drop=True)
        industry, context = g["industry"][0], g["context_value"][0]
        playbook, required = standard_order(industry, context), required_fields(industry, context)
        fields, status = g["onboarding_field"].tolist(), g["validation_status"].tolist()
        latest, history = {}, []
        for i in range(len(g) - 1):
            history.append(fields[i])
            latest[fields[i]] = status[i]
            valid = {k for k, v in latest.items() if v == "passed"}
            if not (required - valid):
                continue
            out.append((industry, context, list(history), status[i],
                        [n for n in playbook if n not in valid], playbook, required, fields[i + 1]))
    return out


def fit_gap(model, df):
    result = {}
    for split in ("train", "validation", "test"):
        steps = app_steps(df, split)
        right = [model.rank(h, ind, ctx, last, cand, form, req)[0][0] == target
                 for ind, ctx, h, last, cand, form, req, target in steps]
        result[split] = {"steps": len(steps), "model": pct(right)}
        print(f"  {split:<10} {len(steps):>6} steps  {result[split]['model']}%")
    return result


# Part 2 and 3 -------------------------------------------------------------------------

def real_cases(path):
    log = pd.read_csv(path, usecols=["concept:name", "time:timestamp", "case:concept:name", "concept:fraud"])
    log["row"] = np.arange(len(log))
    log["t"] = pd.to_datetime(log["time:timestamp"], utc=True, format="mixed")
    log = log.sort_values(["case:concept:name", "t", "row"])
    cases = []
    for case, g in log.groupby("case:concept:name", sort=True):
        steps = g[g["concept:name"].isin(REAL_TO_APP)]
        fields = [REAL_TO_APP[n] for n in steps["concept:name"]]
        first, times = [], []
        for f, t in zip(fields, steps["t"]):
            if f not in first:
                first.append(f)
                times.append(t)
        gaps = [(b - a).total_seconds() for a, b in zip(times, times[1:])]
        cases.append({"case": int(case), "order": first, "entries": len(fields), "gaps": gaps,
                      "fraud": bool(g["concept:fraud"].any())})
    return cases


def simulated_cases(df):
    sim = df[(df["industry"] == "banking") & (df["context_value"] == "individual")]
    sim = sim.sort_values(["session_id", "event_timestamp"])
    cases = []
    for _, g in sim.groupby("session_id", sort=True):
        g = g[g["onboarding_field"].isin(SHARED)]
        fields = g["onboarding_field"].tolist()
        times = pd.to_datetime(g["event_timestamp"]).tolist()
        first, first_t = [], []
        for f, t in zip(fields, times):
            if f not in first:
                first.append(f)
                first_t.append(t)
        gaps = [(b - a).total_seconds() for a, b in zip(first_t, first_t[1:])]
        cases.append({"order": first, "entries": len(fields), "gaps": gaps})
    return cases


def patterns(cases, form):
    started = [c for c in cases if c["order"]]
    complete = [c for c in started if set(c["order"]) == SHARED]
    orders = Counter(tuple(c["order"]) for c in complete)
    common, common_n = orders.most_common(1)[0] if orders else ((), 0)
    gaps = [g for c in started for g in c["gaps"]]
    return {
        "cases_started": len(started),
        "reached_terms_question": pct(["terms_and_conditions_acceptance" in c["order"] for c in started]),
        "answered_all_ten": pct([set(c["order"]) == SHARED for c in started]),
        "order_differs_from_app_form": pct([out_of_order_steps(c["order"], form) > 0 for c in complete]),
        "follows_app_form_exactly": pct([list(c["order"]) == [f for f in form if f in SHARED] for c in complete]),
        "share_using_most_common_order": round(100.0 * common_n / len(complete), 1) if complete else None,
        "most_common_order": list(common),
        "distinct_orders": len(orders),
        "cases_with_an_answer_entered_again": pct([c["entries"] > len(c["order"]) for c in started]),
        "median_seconds_between_questions": round(float(np.median(gaps)), 1) if gaps else None,
    }


def real_next_question(model, cases):
    form = standard_order("banking", "individual")
    required = required_fields("banking", "individual")
    asked = [f for f in form if f in SHARED]
    learn = [c for c in cases if c["case"] % 2 == 0]
    score = [c for c in cases if c["case"] % 2 == 1]
    # Lookup learned from real cases: the most common next question after (previous, current).
    table = defaultdict(Counter)
    for c in learn:
        o = c["order"]
        for i in range(1, len(o)):
            table[(o[i - 2] if i > 1 else "", o[i - 1])][o[i]] += 1
    right = defaultdict(list)
    top3, chance = [], []
    for c in score:
        o = c["order"]
        for i in range(1, len(o)):
            history, target = o[:i], o[i]
            candidates = [f for f in asked if f not in history]
            ranked = [n for n, _ in model.rank(history, "banking", "individual", "passed", candidates, form, required)]
            right["model"].append(ranked[0] == target)
            top3.append(target in ranked[:3])
            right["app_rule"].append(next(f for f in candidates if f in required or f == candidates[-1]) == target)
            seen = table.get((history[-2] if i > 1 else "", history[-1]))
            guess = next((f for f, _ in seen.most_common() if f in candidates), None) if seen else None
            right["real_lookup"].append((guess or candidates[0]) == target)
            chance.append(1.0 / len(candidates))
    out = {name: pct(v) for name, v in right.items()}
    out.update({"model_top3": pct(top3), "chance": pct(chance), "steps": len(chance), "cases_scored": len(score)})
    a, b = np.array(right["model"]), np.array(right["app_rule"])
    only_model, only_rule = int((a & ~b).sum()), int((~a & b).sum())
    out["model_vs_app_rule"] = {"only_model_right": only_model, "only_rule_right": only_rule,
                                "z": round((only_model - only_rule) / max(1.0, (only_model + only_rule) ** 0.5), 1)}
    return out


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--event-log", required=True)
    p.add_argument("--data", default=str(Path(__file__).resolve().parent / "data" / "onboarding_dataset_v3.csv"))
    p.add_argument("--report", default=str(Path(__file__).resolve().parent / "models" / "real_log_check.json"))
    args = p.parse_args()

    model = ShippedModel()
    df = pd.read_csv(args.data)
    print("Part 1: accuracy on the app's suggestion steps, by split")
    gap = fit_gap(model, df)

    print("Part 2 and 3: real event log")
    cases = real_cases(args.event_log)
    form = standard_order("banking", "individual")
    report = {
        "fit_gap": gap,
        "real_log": {"source": "github.com/Mcamilo/Onboarding-Fintech-Event-Log (doi:10.3390/fintech2010009)",
                     "cases": len(cases), "fraud_cases": sum(c["fraud"] for c in cases),
                     "steps_matched_to_app_questions": REAL_TO_APP},
        "patterns": {"real": patterns(cases, form), "simulated_banking_individual": patterns(simulated_cases(df), form)},
        "next_question_on_real_orders": real_next_question(model, cases),
    }
    Path(args.report).write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("patterns", "next_question_on_real_orders")}, indent=2))


if __name__ == "__main__":
    main()
