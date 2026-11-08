"""Edge cases for the shipped next-question model, called exactly as the phone calls it.

Each case states what a sensible suggestion is before the model is run, then checks it. Some
cases only check that the model answers safely (probabilities that sum to 1, no NaN, a pick
from the allowed questions); others check that the pick makes sense.

Needs TensorFlow. Usage:
    python ml/edge_case_check.py [--out ml/models/edge_case_check.json]
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from consistency import standard_order  # noqa: E402
from offscript_breakdown import required_fields  # noqa: E402
from try_next_question import ShippedModel  # noqa: E402

MODEL = ShippedModel()
RESULTS = []


def ask(industry, context, answered, last="passed", valid=None, company=None, form=None):
    """Rank the open questions. [valid] = answered questions that passed (default: all answered).
    [company] = {id: description} of company questions, added at the end of the form."""
    company = company or {}
    form = form or standard_order(industry, context) + list(company)
    valid = set(answered) if valid is None else set(valid)
    required = required_fields(industry, context) | set(company)
    candidates = [f for f in form if f not in valid]
    ranked = MODEL.rank(answered, industry, context, last, candidates, form, required, company)
    return candidates, ranked


def safe(candidates, ranked):
    p = np.asarray([x[1] for x in ranked])
    return (len(ranked) == len(candidates) and np.all(np.isfinite(p)) and abs(p.sum() - 1) < 1e-4
            and ranked[0][0] in candidates)


def case(name, expectation, passed, detail):
    RESULTS.append({"case": name, "expected": expectation, "pass": bool(passed), "got": detail})
    print(f"{'PASS' if passed else 'FAIL'}  {name}: {detail}")


def top(ranked, n=3):
    return ", ".join(f"{name} {p * 100:.0f}%" for name, p in ranked[:n])


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", default=str(Path(__file__).resolve().parent / "models" / "edge_case_check.json"))
    args = parser.parse_args()

    # 1. The very first step, every form: nothing answered yet.
    for industry, context in [("banking", "individual"), ("banking", "business"), ("telecom", "mobile"),
                              ("telecom", "broadband"), ("telecom", "tv"), ("telecom", "iot"), ("telecom", "landline")]:
        c, r = ask(industry, context, [], last="NONE")
        first = standard_order(industry, context)[0]
        case(f"start of the form ({industry}/{context})", f"suggest the form's first question, {first}",
             safe(c, r) and r[0][0] == first, top(r))

    # 2. The last answer failed its check: the agent should fix it before moving on.
    c, r = ask("banking", "individual", ["full_name", "email_address"], last="failed", valid=["full_name"])
    case("last answer failed (email)", "suggest email_address again", safe(c, r) and r[0][0] == "email_address", top(r))

    # 3. The same answer failed three times in a row: still fix it.
    c, r = ask("banking", "individual", ["full_name"] + ["mobile_phone_number"] * 3, last="failed", valid=["full_name"])
    case("same answer failed 3 times", "suggest mobile_phone_number again",
         safe(c, r) and r[0][0] == "mobile_phone_number", top(r))

    # 4. Only one question left.
    form = standard_order("banking", "individual")
    c, r = ask("banking", "individual", form[:-1])
    case("one question left", f"suggest {form[-1]} with ~100%", safe(c, r) and r[0][0] == form[-1] and r[0][1] > 0.99, top(r))

    # 5. A history far longer than the 29 the model reads (many revisits).
    long = (form[:5] * 8)[:45]
    c, r = ask("banking", "individual", long, valid=form[:5])
    case("45 answers, longer than the model reads", f"answer safely; suggest the next open question, {c[0]}",
         safe(c, r) and r[0][0] == c[0], top(r))

    # 6. Agent answers the last question of the form first.
    c, r = ask("banking", "individual", ["terms_and_conditions_acceptance"])
    case("last question answered first", "suggest the first open question, full_name",
         safe(c, r) and r[0][0] == "full_name", top(r))

    # 7. Agent starts with the ID document (a habit in the data), then the type.
    c, r = ask("banking", "individual", ["identification_document"])
    case("starts with the ID document", "suggest identification_document_type (follow the agent)",
         safe(c, r) and r[0][0] == "identification_document_type", top(r))

    # 8. Company questions while standard required questions are still open: do not jump ahead.
    company = {"company_loyalty_number": "Loyalty number. the client's loyalty card number"}
    c, r = ask("banking", "individual", form[:3], company=company)
    case("company question while the form is half done", "suggest a standard question, not the company one",
         safe(c, r) and r[0][0] in form, top(r))

    # 9. Only company questions left.
    two = {"company_loyalty_number": "Loyalty number. the client's loyalty card number",
           "company_branch": "Branch. the branch where the account is opened"}
    c, r = ask("banking", "individual", form, company=two)
    case("only company questions left", "suggest the first company question",
         safe(c, r) and r[0][0] == "company_loyalty_number", top(r))

    # 10. A company question answered in the middle (the app drops it from what the model reads).
    c, r = ask("banking", "individual", ["full_name", "company_loyalty_number", "email_address"],
               company={"company_loyalty_number": "Loyalty number. card"})
    case("company question answered mid-way", "still suggest mobile_phone_number next",
         safe(c, r) and r[0][0] == "mobile_phone_number", top(r))

    # 11. Odd company descriptions: empty, Swahili, very long, a copy of a standard description.
    odd = {"company_a": "", "company_b": "Nambari ya simu ya mteja wa ziada",
           "company_c": "x " * 300, "company_d": MODEL.meta["field_descriptions"]["email_address"]}
    c, r = ask("banking", "individual", form, company=odd)
    case("odd company descriptions", "answer safely; suggest a company question", safe(c, r) and r[0][0] in odd, top(r))

    # 12. A copy of a standard description must not pull a company question ahead of the form.
    c, r = ask("banking", "individual", ["full_name"], company={"company_d": MODEL.meta["field_descriptions"]["email_address"]})
    case("company copy of the email description", "still suggest email_address (the real one)",
         safe(c, r) and r[0][0] == "email_address", top(r))

    # 13. Many company questions (near the 128-question limit).
    many = {f"company_q{i}": f"Question {i}. extra detail number {i}" for i in range(100)}
    c, r = ask("banking", "business", [], last="NONE", company=many)
    case("114 open questions (100 company ones)", "answer safely; suggest full_name", safe(c, r) and r[0][0] == "full_name",
         f"{len(c)} candidates; " + top(r))

    # 14. Same input twice gives the same answer.
    a = ask("telecom", "mobile", ["full_name", "email_address"])[1]
    b = ask("telecom", "mobile", ["full_name", "email_address"])[1]
    case("same input twice", "identical rankings", [x[0] for x in a] == [x[0] for x in b] and
         np.allclose([x[1] for x in a], [x[1] for x in b]), top(a))

    # 15. Last check unknown (no checked answer yet but some typed).
    c, r = ask("telecom", "landline", ["full_name"], last="NONE")
    case("last check unknown", "answer safely", safe(c, r), top(r))

    passed = sum(r["pass"] for r in RESULTS)
    print(f"\n{passed} of {len(RESULTS)} cases passed")
    Path(args.out).write_text(json.dumps({"passed": passed, "cases": len(RESULTS), "results": RESULTS}, indent=2),
                              encoding="utf-8")


if __name__ == "__main__":
    main()
