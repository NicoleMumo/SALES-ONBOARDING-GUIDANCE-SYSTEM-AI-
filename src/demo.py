"""
Live demo: predict the next onboarding field, step by step.

Uses the final flexible model (onboarding_model_flexible.keras) and the
same defensive input handling built in onboarding_transformer_final.ipynb
section 10 -- unknown fields raise a clear error instead of crashing,
oversized sequences warn instead of silently truncating.

Two things it does:

1. DEMO_SESSIONS: replays a few complete fresh sessions from the generator,
   printing the model's prediction (and confidence) before revealing the
   true next field at each step.

2. An interactive prompt: type a sequence of field names yourself and get
   the model's top-5 predictions.

Usage:
    python demo.py                 # scripted walkthrough
    python demo.py --interactive   # also drops into a type-your-own-sequence prompt

Requires, in the same folder: model_def.py, field_similarity_head.py,
onboarding_model_flexible.keras, preprocessing_meta.json,
onboarding_generator_v2.py (all provided alongside this script).
"""

import argparse
import json
import random
import warnings

import numpy as np
import tensorflow as tf

from model_def import GatherLastToken, PositionalEmbedding, ExpandAttentionMask, TransformerBlock
from field_similarity_head import FieldSimilarityHead

MODEL_PATH = "onboarding_model_flexible.keras"
META_PATH = "preprocessing_meta.json"


class UnknownFieldError(ValueError):
    pass


class UnknownCategoryError(ValueError):
    pass


class SequenceTooLongWarning(UserWarning):
    pass


def load_everything():
    meta = json.load(open(META_PATH))
    model = tf.keras.models.load_model(MODEL_PATH, custom_objects={
        "GatherLastToken": GatherLastToken,
        "PositionalEmbedding": PositionalEmbedding,
        "ExpandAttentionMask": ExpandAttentionMask,
        "TransformerBlock": TransformerBlock,
        "FieldSimilarityHead": FieldSimilarityHead,
    })
    return model, meta


def predict_next(field_names, industry, context, last_validation, model, meta, top_k=5):
    """Same defensive handling as the notebook's safe_predict_next: clear
    errors for unrecognized input instead of a raw crash, and an explicit
    warning instead of silent truncation for oversized sequences."""
    field_to_id = meta["field_to_id"]
    max_len = meta["max_len"]
    industry_to_id = meta["industry_to_id"]
    context_to_id = meta["context_to_id"]
    validation_to_id = meta["validation_to_id"]

    unknown_fields = [f for f in field_names if f not in field_to_id]
    if unknown_fields:
        raise UnknownFieldError(
            f"Unrecognized field name(s): {unknown_fields}. "
            f"Known fields: {sorted(field_to_id.keys())}"
        )
    if industry not in industry_to_id:
        raise UnknownCategoryError(f"Unknown industry '{industry}'. Known: {list(industry_to_id.keys())}")
    if context not in context_to_id:
        raise UnknownCategoryError(f"Unknown context '{context}'. Known: {list(context_to_id.keys())}")

    if len(field_names) > max_len:
        warnings.warn(
            f"Sequence has {len(field_names)} fields but the model only considers the most recent "
            f"{max_len}. The earliest {len(field_names) - max_len} field(s) will be IGNORED.",
            SequenceTooLongWarning,
        )

    ids = [field_to_id[f] for f in field_names][-max_len:]
    padded = [0] * (max_len - len(ids)) + ids
    mask = [0] * (max_len - len(ids)) + [1] * len(ids)

    x = np.array([padded], dtype=np.int32)
    m = np.array([mask], dtype=bool)
    ind = np.array([industry_to_id[industry]], dtype=np.int32)
    ctx = np.array([context_to_id[context]], dtype=np.int32)
    val = np.array([validation_to_id.get(last_validation, 0)], dtype=np.int32)

    probabilities = model.predict([x, m, ind, ctx, val], verbose=0)[0]
    id_to_field = {int(v): k for k, v in field_to_id.items()}
    top_indices = np.argsort(probabilities)[-top_k:][::-1]
    return [(id_to_field[i + 1], float(probabilities[i])) for i in top_indices]


def print_prediction(field_names, prediction, actual=None):
    history = " -> ".join(field_names) if field_names else "(start of session)"
    print(f"\nSequence so far: {history}")
    print("Model's top predictions for the NEXT field:")
    for rank, (field, prob) in enumerate(prediction, start=1):
        marker = "  <-- correct!" if actual is not None and field == actual else ""
        print(f"  {rank}. {field:35s} {prob*100:5.1f}%{marker}")
    if actual is not None:
        top1_correct = prediction[0][0] == actual
        print(f"Actual next field was: {actual}   "
              f"({'MATCHED top prediction' if top1_correct else 'in top-5' if actual in [f for f, _ in prediction] else 'missed'})")


def run_scripted_walkthrough(model, meta, num_sessions=3, seed=None):
    import sys
    sys.path.insert(0, ".")
    from onboarding_generator_v2 import generate_session

    if seed is not None:
        random.seed(seed)

    correct_top1, correct_top5, total = 0, 0, 0

    for session_number in range(1, num_sessions + 1):
        industry = random.choice(["banking", "telecom"])
        events = generate_session(industry)
        print("\n" + "=" * 70)
        print(f"DEMO SESSION {session_number}  (industry: {industry})")
        print("=" * 70)

        context_value = events[0]["context_value"]
        field_history = []
        for event in events:
            actual_next = event["next_onboarding_field"] or None
            if actual_next is not None:
                prediction = predict_next(field_history + [event["onboarding_field"]],
                                           industry, context_value,
                                           event["validation_status"], model, meta)
                print_prediction(field_history + [event["onboarding_field"]], prediction, actual_next)
                total += 1
                if prediction[0][0] == actual_next:
                    correct_top1 += 1
                if actual_next in [f for f, _ in prediction]:
                    correct_top5 += 1
            field_history.append(event["onboarding_field"])

        print(f"\nSession status: {events[-1]['session_status']}")

    print("\n" + "=" * 70)
    print(f"LIVE DEMO SUMMARY over {total} predictions across {num_sessions} fresh sessions:")
    print(f"  Top-1 accuracy: {correct_top1/total*100:.1f}%")
    print(f"  Top-5 accuracy: {correct_top5/total*100:.1f}%")
    print("=" * 70)


def run_robustness_checks(model, meta):
    """Shows the defensive handling actually working -- good to run live if
    asked 'what happens when it breaks'."""
    print("\n" + "=" * 70)
    print("ROBUSTNESS CHECKS")
    print("=" * 70)

    print("\n1. Unknown field name:")
    try:
        predict_next(["not_a_real_field"], "banking", "individual", "passed", model, meta)
    except UnknownFieldError as e:
        print(f"   Caught cleanly: {e}"[:150] + "...")

    print("\n2. Unknown industry:")
    try:
        predict_next(["full_name"], "insurance", "individual", "passed", model, meta)
    except UnknownCategoryError as e:
        print(f"   Caught cleanly: {e}")

    print("\n3. Oversized sequence (50 fields, model only sees the last 30):")
    field_vocab = meta["field_vocab"]
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        predict_next([field_vocab[0]] * 50, "banking", "individual", "passed", model, meta)
        for w in caught:
            print(f"   Warned: {w.message}")


def run_interactive(model, meta):
    print("\n--- Interactive mode ---")
    print("Available fields:", ", ".join(sorted(meta["field_to_id"].keys())))
    print("Industries:", ", ".join(meta["industry_to_id"].keys()))
    print("Contexts:", ", ".join(meta["context_to_id"].keys()))
    print("Type field names separated by commas, or 'quit' to exit.\n")

    industry = input("Industry (banking/telecom): ").strip()
    context = input(f"Context ({'/'.join(meta['context_to_id'].keys())}): ").strip()

    while True:
        raw = input("\nField sequence so far (comma-separated, or 'quit'): ").strip()
        if raw.lower() == "quit":
            break
        field_names = [f.strip() for f in raw.split(",") if f.strip()]
        try:
            prediction = predict_next(field_names, industry, context, "passed", model, meta)
        except (UnknownFieldError, UnknownCategoryError) as e:
            print(f"  {e}")
            continue
        print_prediction(field_names, prediction)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--interactive", action="store_true")
    parser.add_argument("--sessions", type=int, default=3)
    parser.add_argument("--seed", type=int, default=None)
    parser.add_argument("--robustness", action="store_true", help="also run the error-handling demo")
    args = parser.parse_args()

    model, meta = load_everything()
    run_scripted_walkthrough(model, meta, num_sessions=args.sessions, seed=args.seed)

    if args.robustness:
        run_robustness_checks(model, meta)

    if args.interactive:
        run_interactive(model, meta)
