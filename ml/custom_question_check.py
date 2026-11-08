"""Can version 1 of the next-question model score a question it never saw, from its description alone?

(It could not: see the result below. Version 2, ml/next_question_v2.py, can.)

The model (ml/onboarding_transformer.ipynb) does not pick from a fixed list: it compares the
session with a short text description of each candidate question. The report claims this lets
it score a company's own questions without retraining. This script tests that claim.

How: four standard questions are held out completely, as if a company had added them later.
They are removed from training (from the candidate list, from the targets, and from every
answered history, which is also what the app does with a company question it does not know),
and their descriptions are kept out of the model's word list, so unknown words stay unknown.
The same model is then trained from scratch, and on the test sales agents it scores every
candidate, the held-out ones from their descriptions only, exactly as the app calls it.

Held-out questions, chosen by a fixed rule before any result was seen (one from each of four
groups on the admin Questions page): email_address (Contact, both industries), profession
(Money, banking), us_person_status (Declarations, banking), payment_method (Service, telecom).

Pass mark, fixed before running: on test steps where the right next question is a held-out
one, the model picks it first at least 50% of the time and has it in its top 3 at least 80%
of the time, while its accuracy on all other steps stays within 3 points of the shipped model.

Simulated data only. Needs TensorFlow, numpy, pandas. Usage:
    python ml/custom_question_check.py ml/data/onboarding_dataset_v3.csv \
        [--out ml/models/custom_question_check.json]
"""
import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf
from tensorflow.keras import layers

sys.path.insert(0, str(Path(__file__).resolve().parent))
from consistency import standard_order  # noqa: E402
from next_field_model import (  # noqa: E402
    FieldSimilarityHead, GatherLastToken, build_backbone, build_description_vocab, encode_description,
)
from offscript_breakdown import required_fields, tflite_predictions  # noqa: E402

MODELS = Path(__file__).resolve().parent / "models"
HELD_OUT = ["email_address", "profession", "us_person_status", "payment_method"]
PASS_TOP1, PASS_TOP3, PASS_OTHER_DROP = 50.0, 80.0, 3.0
EMBEDDING_DIM, NUM_HEADS, FEED_FORWARD_DIM, NUM_BLOCKS, DROPOUT = 64, 4, 128, 2, 0.15


def sessions_of(df, split):
    for session_id, group in df[df["split"] == split].groupby("session_id", sort=True):
        yield session_id, group.reset_index(drop=True)


def training_arrays(df, split, field_to_id, max_len, industry_to_id, context_to_id, validation_to_id):
    """Prefix -> next pairs, in a world where the held-out questions do not exist."""
    xs, ys, ind, ctx, val = [], [], [], [], []
    for _, group in sessions_of(df, split):
        kept = group[~group["onboarding_field"].isin(HELD_OUT)].reset_index(drop=True)
        ids = [field_to_id[f] for f in kept["onboarding_field"]]
        for i in range(len(ids) - 1):
            prefix = ids[: i + 1][-max_len:]
            xs.append([0] * (max_len - len(prefix)) + prefix)
            ys.append(ids[i + 1] - 1)
            ind.append(industry_to_id[kept["industry"].iloc[0]])
            ctx.append(context_to_id[kept["context_value"].iloc[0]])
            val.append(validation_to_id.get(str(kept["validation_status"].iloc[i]), 0))
    x = np.asarray(xs, np.int32)
    return [x, x > 0, np.asarray(ind, np.int32), np.asarray(ctx, np.int32), np.asarray(val, np.int32)], np.asarray(ys, np.int32)


def build_model(field_vocab, descriptions, vocab, max_len, n_industries, n_contexts):
    input_ids = layers.Input(shape=(max_len,), dtype=tf.int32, name="input_ids")
    attention_mask = layers.Input(shape=(max_len,), dtype=tf.bool, name="attention_mask")
    industry_id = layers.Input(shape=(), dtype=tf.int32, name="industry_id")
    context_id = layers.Input(shape=(), dtype=tf.int32, name="context_id")
    validation_id = layers.Input(shape=(), dtype=tf.int32, name="validation_id")
    sequence, _ = build_backbone(input_ids, attention_mask, len(field_vocab) + 1, EMBEDDING_DIM, NUM_HEADS,
                                 FEED_FORWARD_DIM, NUM_BLOCKS, DROPOUT, max_len)
    pooled = GatherLastToken(name="last_valid_token")(sequence)
    combined = layers.Concatenate()([
        pooled,
        layers.Embedding(n_industries, 8, name="industry_embedding")(industry_id),
        layers.Embedding(n_contexts, 8, name="context_embedding")(context_id),
        layers.Embedding(3, 4, name="validation_embedding")(validation_id),
    ])
    session_vector = layers.Dense(128, activation="relu", name="session_vector")(combined)
    session_vector = layers.Dropout(DROPOUT, name="session_dropout")(session_vector)
    head = FieldSimilarityHead(field_names=field_vocab, field_descriptions=descriptions, vocab=vocab,
                               max_desc_len=14, text_embed_dim=32, output_dim=64, name="field_similarity_head")
    outputs = layers.Activation("softmax", name="next_field_probabilities")(head(session_vector))
    model = tf.keras.Model([input_ids, attention_mask, industry_id, context_id, validation_id], outputs)
    model.compile(optimizer=tf.keras.optimizers.AdamW(learning_rate=1e-3, weight_decay=2e-4),
                  loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    return model, head


def test_steps(df, meta, field_to_id, max_len, industry_to_id, context_to_id, validation_to_id):
    """The app's suggestion steps (as in offscript_breakdown.build_records), encoded twice:
    for the shipped model (full history) and for the held-out model (held-out questions dropped)."""
    records = []
    for session_id, group in sessions_of(df, "test"):
        industry, context = str(group["industry"].iloc[0]), str(group["context_value"].iloc[0])
        playbook, required = standard_order(industry, context), required_fields(industry, context)
        latest, history = {}, []
        for position in range(len(group) - 1):
            row = group.iloc[position]
            field = str(row["onboarding_field"])
            history.append(field)
            latest[field] = str(row["validation_status"])
            valid = {name for name, status in latest.items() if status == "passed"}
            if not (required - valid):
                continue
            full = [meta["field_to_id"][name] for name in history][-meta["max_len"]:]
            kept = [field_to_id[name] for name in history if name not in HELD_OUT][-max_len:]
            records.append({
                "session_id": session_id,
                "input_ids": [0] * (meta["max_len"] - len(full)) + full,
                "kept_ids": [0] * (max_len - len(kept)) + kept,
                "industry_id": meta["industry_to_id"][industry],
                "context_id": meta["context_to_id"][context],
                "validation_id": meta["validation_to_id"].get(str(row["validation_status"]), 0),
                "own_industry_id": industry_to_id[industry],
                "own_context_id": context_to_id[context],
                "own_validation_id": validation_to_id.get(str(row["validation_status"]), 0),
                "target": str(row["next_onboarding_field"]),
                "candidates": [name for name in playbook if name not in valid],
                "rule": next(n for n in playbook if n in required and n not in valid),
            })
    return records


def held_out_rankings(model, head, records, descriptions_by_field, vocab):
    """Each candidate scored from its description, as the app does; held-out ones included."""
    session_model = tf.keras.Model(model.inputs, model.get_layer("session_dropout").output)
    x = np.asarray([r["kept_ids"] for r in records], np.int32)
    vectors = session_model.predict(
        [x, x > 0,
         np.asarray([r["own_industry_id"] for r in records], np.int32),
         np.asarray([r["own_context_id"] for r in records], np.int32),
         np.asarray([r["own_validation_id"] for r in records], np.int32)],
        batch_size=1024, verbose=0)
    projected = head.session_projection(vectors).numpy()
    all_fields = sorted(descriptions_by_field)
    encoded = np.asarray([encode_description(descriptions_by_field[f], vocab, 14) for f in all_fields], np.int32)
    field_vectors = dict(zip(all_fields, head.encode_descriptions(encoded).numpy()))
    rankings = []
    for vector, record in zip(projected, records):
        scores = {name: float(vector @ field_vectors[name]) for name in record["candidates"]}
        rankings.append(sorted(record["candidates"], key=lambda name: -scores[name]))
    return rankings


def pct(values):
    return round(100 * float(np.mean(values)), 1) if len(values) else None


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("csv")
    parser.add_argument("--out", default=str(MODELS / "custom_question_check.json"))
    args = parser.parse_args()
    np.random.seed(42)
    tf.random.set_seed(42)

    df = pd.read_csv(args.csv).sort_values(["session_id", "event_timestamp"])
    meta = json.loads((MODELS / "v1" / "preprocessing_meta.json").read_text(encoding="utf-8"))
    descriptions_by_field = meta["field_descriptions"]
    known = sorted(set(df["onboarding_field"]) - set(HELD_OUT))
    field_to_id = {f: i + 1 for i, f in enumerate(known)}
    known_descriptions = [descriptions_by_field[f] for f in known]
    # The word list never sees the held-out descriptions: their unknown words stay unknown.
    vocab = build_description_vocab(known_descriptions + [f.replace("_", " ") for f in known])
    industry_to_id = {v: i for i, v in enumerate(sorted(df["industry"].unique()))}
    context_to_id = {v: i for i, v in enumerate(sorted(df["context_value"].unique()))}
    validation_to_id = {"NONE": 0, "passed": 1, "failed": 2}
    max_len = int(df[~df["onboarding_field"].isin(HELD_OUT)].groupby("session_id").size().max())

    arrays = lambda split: training_arrays(df, split, field_to_id, max_len, industry_to_id, context_to_id, validation_to_id)
    train_x, train_y = arrays("train")
    val_x, val_y = arrays("validation")
    model, head = build_model(known, known_descriptions, vocab, max_len, len(industry_to_id), len(context_to_id))
    history = model.fit(train_x, train_y, validation_data=(val_x, val_y), epochs=25, batch_size=512, verbose=2,
                        callbacks=[tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=3, restore_best_weights=True)])

    records = test_steps(df, meta, field_to_id, max_len, industry_to_id, context_to_id, validation_to_id)
    rankings = held_out_rankings(model, head, records, descriptions_by_field, vocab)
    shipped = tflite_predictions(records, meta)

    target = [r["target"] for r in records]
    on_held = [i for i, r in enumerate(records) if r["target"] in HELD_OUT and r["target"] in r["candidates"]]
    other = [i for i, r in enumerate(records) if r["target"] not in HELD_OUT]
    top = lambda i, k: target[i] in rankings[i][:k]

    def block(rows):
        return {
            "steps": len(rows),
            "held_out_model_top1": pct([top(i, 1) for i in rows]),
            "held_out_model_top3": pct([top(i, 3) for i in rows]),
            "shipped_model_top1": pct([shipped[i] == target[i] for i in rows]),
            "app_rule_top1": pct([records[i]["rule"] == target[i] for i in rows]),
            "chance_top1": pct([1 / len(records[i]["candidates"]) for i in rows]),
        }

    per_question = {name: block([i for i in on_held if target[i] == name]) for name in HELD_OUT}
    on_held_result, other_result = block(on_held), block(other)
    wrong_held_picks = pct([rankings[i][0] in HELD_OUT for i in other])
    passed = (
        on_held_result["held_out_model_top1"] >= PASS_TOP1
        and on_held_result["held_out_model_top3"] >= PASS_TOP3
        and other_result["shipped_model_top1"] - other_result["held_out_model_top1"] <= PASS_OTHER_DROP
    )
    report = {
        "dataset": Path(args.csv).name,
        "note": "Simulated data, test split (unseen sales agents), the app's suggestion steps only.",
        "held_out_questions": HELD_OUT,
        "pass_mark": {"top1_on_held_out_steps": PASS_TOP1, "top3_on_held_out_steps": PASS_TOP3,
                      "max_drop_on_other_steps": PASS_OTHER_DROP},
        "epochs": len(history.history["loss"]),
        "steps_where_a_held_out_question_is_next": on_held_result,
        "per_held_out_question": per_question,
        "all_other_steps": other_result,
        "other_steps_where_a_held_out_question_was_wrongly_picked_pct": wrong_held_picks,
        "claim_supported": bool(passed),
    }
    Path(args.out).write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
