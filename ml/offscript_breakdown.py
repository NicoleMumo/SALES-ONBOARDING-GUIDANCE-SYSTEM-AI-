"""Where does the next-question model beat the app's rule? A breakdown by session type.

Rebuilds the notebook's app-style test steps (ml/onboarding_transformer.ipynb, the
"Android candidate-restricted" evaluation), scores the shipped TensorFlow Lite model the
way the app calls it, and compares it with the app's rule and the best lookup on:

- sessions whose order stayed standard (no step out of place) vs sessions that left it,
- each industry.

The session labels use the whole session, so they are for analysis only: none of the three
methods sees them. It first checks that the overall numbers match the notebook's.

Needs TensorFlow (for the TFLite interpreter), numpy and pandas:
    python ml/offscript_breakdown.py ml/data/onboarding_dataset_v3.csv \
        --out ml/models/offscript_report.json
"""
import argparse
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf

from consistency import out_of_order_steps, standard_order

MODELS = Path(__file__).resolve().parent / "models"

# The app's required questions per form (as in the notebook and the Kotlin playbooks).
BANKING_INDIVIDUAL_OPTIONAL = {"assets"}
BANKING_BUSINESS_EXTRA_REQUIRED = {
    "business_name", "commercial_address", "beneficial_owner_information",
    "business_relationship_purpose", "business_relationship_nature",
}


def required_fields(industry, context):
    playbook = standard_order(industry, context)
    if industry == "banking":
        required = set(standard_order("banking", "individual")) - BANKING_INDIVIDUAL_OPTIONAL
        if context == "business":
            required |= BANKING_BUSINESS_EXTRA_REQUIRED
        return required
    required = set(standard_order("telecom", "iot"))  # base fields + subscriber_number
    if context in {"mobile", "landline"}:
        required.add("phone_service_selection")
    if context in {"broadband", "tv"}:
        required.add("internet_service_selection")
    assert required <= set(playbook)
    return required


def build_records(df, split, meta):
    """One record per step where the app asks for a suggestion (as in the notebook)."""
    field_to_id = meta["field_to_id"]
    max_len = meta["max_len"]
    records = []
    for session_id, group in df[df["split"] == split].groupby("session_id", sort=True):
        group = group.reset_index(drop=True)
        industry = str(group["industry"].iloc[0])
        context = str(group["context_value"].iloc[0])
        playbook = standard_order(industry, context)
        required = required_fields(industry, context)
        fields = group["onboarding_field"].astype(str).tolist()
        session_out_of_order = out_of_order_steps(fields, playbook)
        latest_validation, history = {}, []
        for position in range(len(group) - 1):
            row = group.iloc[position]
            field = str(row["onboarding_field"])
            history.append(field)
            latest_validation[field] = str(row["validation_status"])
            valid = {name for name, status in latest_validation.items() if status == "passed"}
            if not (required - valid):
                continue  # all required questions valid: the app asks for no suggestion
            candidates = [name for name in playbook if name not in valid]
            ids = [field_to_id[name] for name in history][-max_len:]
            previous = row["previous_onboarding_field"]
            records.append({
                "session_id": session_id,
                "industry": industry,
                "input_ids": [0] * (max_len - len(ids)) + ids,
                "industry_id": meta["industry_to_id"][industry],
                "context_id": meta["context_to_id"][context],
                "validation_id": meta["validation_to_id"].get(str(row["validation_status"]), 0),
                "target": str(row["next_onboarding_field"]),
                "current_field": field,
                "previous_field": "" if pd.isna(previous) else str(previous),
                "candidates": candidates,
                "plain_rule": next(n for n in playbook if n in required and n not in valid),
                "session_out_of_order": session_out_of_order,
            })
    return records


def lookup_predictions(train, test):
    """The notebook's best lookup (B2) with its fallbacks (B1, then the overall counts)."""
    def counts(key):
        table = defaultdict(Counter)
        for record in train:
            table[key(record)][record["target"]] += 1
        return table

    b1_key = lambda r: (r["context_id"], r["current_field"])
    b2_key = lambda r: (r["context_id"], r["previous_field"], r["current_field"], r["validation_id"])
    b1, b2 = counts(b1_key), counts(b2_key)
    overall = Counter(r["target"] for r in train)

    def choose(candidates, *score_maps):
        for scores in score_maps:
            if max(scores.get(name, 0) for name in candidates) > 0:
                return max(candidates, key=lambda name: scores.get(name, 0))
        return candidates[0]

    return [choose(r["candidates"], b2.get(b2_key(r), Counter()), b1.get(b1_key(r), Counter()), overall)
            for r in test]


def tflite_predictions(records, meta):
    """The shipped model, called as the app calls it: candidates and their descriptions."""
    interpreter = tf.lite.Interpreter(model_path=str(MODELS / "onboarding_model.tflite"))
    interpreter.allocate_tensors()
    inputs = {d["name"]: d for d in interpreter.get_input_details()}
    output = interpreter.get_output_details()[0]
    vocab, max_desc, max_cand = meta["description_vocab"], meta["max_desc_len"], meta["max_candidates"]

    def encode(text):
        ids = [vocab.get(t, vocab["<unk>"]) for t in text.lower().replace("-", " ").replace(",", " ").split()]
        ids = ids[:max_desc]
        return ids + [vocab["<pad>"]] * (max_desc - len(ids))

    encoded = {name: encode(desc) for name, desc in meta["field_descriptions"].items()}
    keys = sorted(["input_ids", "attention_mask", "industry_id", "context_id", "validation_id",
                   "candidate_description_ids", "candidate_mask"], key=len, reverse=True)
    slot = {name: next(k for k in keys if k in name) for name in inputs}

    predictions = []
    for r in records:
        ids = np.asarray([r["input_ids"]], dtype=np.int32)
        desc = np.zeros((1, max_cand, max_desc), dtype=np.int32)
        mask = np.zeros((1, max_cand), dtype=np.int32)
        for i, name in enumerate(r["candidates"]):
            desc[0, i] = encoded[name]
            mask[0, i] = 1
        values = {
            "input_ids": ids, "attention_mask": (ids > 0).astype(np.int32),
            "industry_id": np.asarray([r["industry_id"]], dtype=np.int32),
            "context_id": np.asarray([r["context_id"]], dtype=np.int32),
            "validation_id": np.asarray([r["validation_id"]], dtype=np.int32),
            "candidate_description_ids": desc, "candidate_mask": mask,
        }
        for name, detail in inputs.items():
            interpreter.set_tensor(detail["index"], values[slot[name]])
        interpreter.invoke()
        scores = interpreter.get_tensor(output["index"])[0, :len(r["candidates"])]
        predictions.append(r["candidates"][int(np.argmax(scores))])
    return predictions


def summarise(records, model, rule, lookup, keep):
    rows = [i for i, r in enumerate(records) if keep(r)]
    if not rows:
        return {"steps": 0}
    target = np.asarray([records[i]["target"] for i in rows])
    right = {name: np.asarray([preds[i] for i in rows]) == target
             for name, preds in (("model", model), ("rule", rule), ("lookup", lookup))}

    def mcnemar(other):
        only_model = int((right["model"] & ~right[other]).sum())
        only_other = int((~right["model"] & right[other]).sum())
        z = (only_model - only_other) / max(math.sqrt(only_model + only_other), 1e-9)
        return {"only_model_right": only_model, f"only_{other}_right": only_other, "z": round(z, 1)}

    return {
        "steps": len(rows),
        "sessions": len({records[i]["session_id"] for i in rows}),
        "model": round(float(right["model"].mean()) * 100, 1),
        "rule": round(float(right["rule"].mean()) * 100, 1),
        "lookup": round(float(right["lookup"].mean()) * 100, 1),
        "model_vs_rule": mcnemar("rule"),
        "model_vs_lookup": mcnemar("lookup"),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("csv")
    parser.add_argument("--out", default=str(MODELS / "offscript_report.json"))
    args = parser.parse_args()

    meta = json.loads((MODELS / "preprocessing_meta.json").read_text(encoding="utf-8"))
    df = pd.read_csv(args.csv)
    train = build_records(df, "train", meta)
    test = build_records(df, "test", meta)
    rule = [r["plain_rule"] for r in test]
    lookup = lookup_predictions(train, test)
    model = tflite_predictions(test, meta)

    report = {
        "dataset": Path(args.csv).name,
        "note": "Simulated data. Session labels use the whole session and are for analysis only; no method sees them.",
        "all_steps": summarise(test, model, rule, lookup, lambda r: True),
        "standard_order_sessions": summarise(test, model, rule, lookup, lambda r: r["session_out_of_order"] == 0),
        "off_script_sessions": summarise(test, model, rule, lookup, lambda r: r["session_out_of_order"] >= 1),
        "banking": summarise(test, model, rule, lookup, lambda r: r["industry"] == "banking"),
        "telecom": summarise(test, model, rule, lookup, lambda r: r["industry"] == "telecom"),
    }
    Path(args.out).write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
