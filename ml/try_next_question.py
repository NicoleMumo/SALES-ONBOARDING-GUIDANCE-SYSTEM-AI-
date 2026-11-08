"""Try the shipped next-question model: what does it suggest, and why is that useful?

Uses the same model file the phone uses (ml/models/onboarding_model.tflite), called as the app
calls it: the questions answered so far, the industry and client type, whether the last answer
passed its check, and the questions still open (the candidates, each given by its description).

Two ways to use it:

1. Ask it directly. Give the answered questions in order; it ranks every open question:
       python ml/try_next_question.py ask --industry banking --context individual \
           --answered full_name,email_address,mobile_phone_number --last passed

   Add a company's own question with --company-question "Name: description" (repeatable). It
   goes at the end of the form, as in the app; the model was never trained on it.

2. Watch it on a real test session (an unseen sales agent), step by step, next to the app's
   plain rule ("ask the first unanswered required question in the form's order"):
       python ml/try_next_question.py walk ml/data/onboarding_dataset_v3.csv [--off-script] [--session ID]
   --off-script picks a session where the agent left the form's order, where the model helps most.

Simulated data only.
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf

sys.path.insert(0, str(Path(__file__).resolve().parent))
from consistency import out_of_order_steps, standard_order  # noqa: E402
from next_question_v2 import candidate_features  # noqa: E402
from offscript_breakdown import required_fields  # noqa: E402

MODELS = Path(__file__).resolve().parent / "models"


class ShippedModel:
    def __init__(self):
        self.meta = json.loads((MODELS / "preprocessing_meta.json").read_text(encoding="utf-8"))
        self.interpreter = tf.lite.Interpreter(model_path=str(MODELS / "onboarding_model.tflite"))
        self.interpreter.allocate_tensors()
        self.inputs = {d["name"]: d for d in self.interpreter.get_input_details()}
        self.output = self.interpreter.get_output_details()[0]
        keys = sorted(["input_ids", "attention_mask", "industry_id", "context_id", "validation_id",
                       "candidate_description_ids", "candidate_mask", "candidate_features"], key=len, reverse=True)
        self.slot = {name: next(k for k in keys if k in name) for name in self.inputs}

    def encode(self, text):
        vocab, size = self.meta["description_vocab"], self.meta["max_desc_len"]
        ids = [vocab.get(t, vocab["<unk>"]) for t in text.lower().replace("-", " ").replace(",", " ").split()][:size]
        return ids + [vocab["<pad>"]] * (size - len(ids))

    def rank(self, answered, industry, context, last, candidates, form, required, extra_descriptions=None):
        """Candidates with their probabilities, most likely first. [form] is the whole form in order."""
        descriptions = {**self.meta["field_descriptions"], **(extra_descriptions or {})}
        meta, max_len, max_cand = self.meta, self.meta["max_len"], self.meta["max_candidates"]
        ids = [meta["field_to_id"][name] for name in answered if name in meta["field_to_id"]][-max_len:]
        ids = np.asarray([[0] * (max_len - len(ids)) + ids], np.int32)
        desc = np.zeros((1, max_cand, meta["max_desc_len"]), np.int32)
        mask = np.zeros((1, max_cand), np.int32)
        for i, name in enumerate(candidates):
            desc[0, i] = self.encode(descriptions[name])
            mask[0, i] = 1
        values = {
            "input_ids": ids, "attention_mask": (ids > 0).astype(np.int32),
            "industry_id": np.asarray([meta["industry_to_id"][industry]], np.int32),
            "context_id": np.asarray([meta["context_to_id"][context]], np.int32),
            "validation_id": np.asarray([meta["validation_to_id"].get(last, 0)], np.int32),
            "candidate_description_ids": desc, "candidate_mask": mask,
            "candidate_features": np.zeros((1, max_cand, 5), np.int32),
        }
        for i, row in enumerate(candidate_features(candidates, form, required, answered)):
            values["candidate_features"][0, i] = row
        for name, detail in self.inputs.items():
            self.interpreter.set_tensor(detail["index"], values[self.slot[name]])
        self.interpreter.invoke()
        # The model already gives probabilities over the open questions (they sum to 1).
        probabilities = self.interpreter.get_tensor(self.output["index"])[0, :len(candidates)].astype(float)
        return sorted(zip(candidates, probabilities), key=lambda pair: -pair[1])


def ask(args, model):
    answered = [f.strip() for f in args.answered.split(",") if f.strip()]
    # A company's own questions go at the end of the form, under "Company questions", as in the app.
    company = {}
    for text in args.company_question:
        name, _, description = text.partition(":")
        company["company_" + "_".join(name.lower().split())] = f"{name.strip()}. {description.strip()}"
    playbook = standard_order(args.industry, args.context) + list(company)
    unknown = [f for f in answered if f not in playbook]
    if unknown:
        raise SystemExit(f"Not in the {args.industry} {args.context} form: {unknown}. Its questions: {playbook}")
    candidates = [f for f in playbook if f not in answered]
    required = required_fields(args.industry, args.context) | set(company)
    rule = next((f for f in playbook if f in required and f not in answered), None)
    print(f"\n{args.industry} / {args.context}; answered: {', '.join(answered) or '(none)'}; last check: {args.last}")
    print(f"App's plain rule would ask: {rule}\n\nModel's ranking of the open questions:")
    ranked = model.rank(answered, args.industry, args.context, args.last, candidates, playbook, required, company)
    for position, (name, p) in enumerate(ranked, 1):
        print(f"  {position:2}. {name:34} {p * 100:5.1f}%{'   <- rule' if name == rule else ''}")


def walk(args, model):
    df = pd.read_csv(args.csv).sort_values(["session_id", "event_timestamp"])
    test = df[df["split"] == "test"]
    sessions = {sid: g.reset_index(drop=True) for sid, g in test.groupby("session_id", sort=True)}
    if args.session:
        session_id = args.session
    else:
        def order_breaks(group):
            industry, context = group["industry"].iloc[0], group["context_value"].iloc[0]
            return out_of_order_steps(group["onboarding_field"].tolist(), standard_order(industry, context))
        wanted = [sid for sid, g in sessions.items() if (order_breaks(g) >= 2) == args.off_script and 10 <= len(g) <= 22]
        session_id = wanted[len(wanted) // 2]
    group = sessions[session_id]
    industry, context = str(group["industry"].iloc[0]), str(group["context_value"].iloc[0])
    playbook, required = standard_order(industry, context), required_fields(industry, context)
    print(f"\nSession {session_id}: {industry} / {context}, {len(group)} answers, ended {group['session_status'].iloc[-1]}")
    print("Form order:", " > ".join(playbook))
    print(f"\n{'#':>2}  {'just answered (check)':38} {'agent asked next':30} {'rule':6} {'model top 3'}")
    latest, history, model_right, rule_right, steps = {}, [], 0, 0, 0
    for position in range(len(group) - 1):
        row = group.iloc[position]
        field = str(row["onboarding_field"])
        history.append(field)
        latest[field] = str(row["validation_status"])
        valid = {name for name, status in latest.items() if status == "passed"}
        if not (required - valid):
            continue
        candidates = [name for name in playbook if name not in valid]
        rule = next(n for n in playbook if n in required and n not in valid)
        ranked = model.rank(history, industry, context, str(row["validation_status"]), candidates, playbook, required)
        actual = str(row["next_onboarding_field"])
        steps += 1
        model_right += ranked[0][0] == actual
        rule_right += rule == actual
        top3 = ", ".join(f"{name} {p * 100:.0f}%" for name, p in ranked[:3])
        mark = lambda ok: "right" if ok else "wrong"
        print(f"{position + 1:>2}  {field + ' (' + latest[field] + ')':38} {actual:30} {mark(rule == actual):6} "
              f"{mark(ranked[0][0] == actual)}: {top3}")
    print(f"\nOn this session: model right {model_right}/{steps}, plain rule right {rule_right}/{steps}.")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)
    a = sub.add_parser("ask")
    a.add_argument("--industry", required=True, choices=["banking", "telecom"])
    a.add_argument("--context", required=True)
    a.add_argument("--answered", default="")
    a.add_argument("--last", default="passed", choices=["passed", "failed", "NONE"])
    a.add_argument("--company-question", action="append", default=[],
                   help='"Name: description" of a question the company added (repeatable)')
    w = sub.add_parser("walk")
    w.add_argument("csv")
    w.add_argument("--off-script", action="store_true")
    w.add_argument("--session")
    args = parser.parse_args()
    model = ShippedModel()
    ask(args, model) if args.command == "ask" else walk(args, model)


if __name__ == "__main__":
    main()
