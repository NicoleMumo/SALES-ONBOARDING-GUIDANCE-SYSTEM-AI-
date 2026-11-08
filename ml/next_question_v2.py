"""Next-question model, version 2: it also knows where each candidate question sits in the form.

Version 1 (ml/onboarding_transformer.ipynb) scores each candidate question only by its text
description. ml/custom_question_check.py showed that this does not carry over to questions it
was not trained on: it never chose one. A company's own question has a description the model
cannot read, but it does have a place in the form. Version 2 gives the model, for every
candidate, four facts the app knows for any question, including a company's own:

- its position in the form;
- how far it is from the last question answered (the "gap");
- whether it is the first open question in the form's order;
- whether it is the question the app's plain rule would ask (the first open required one),
  and whether it is required.

So the model can learn the general habit ("agents usually go on to the next open question,
except when ...") instead of only memorising each question's name. Everything else is as in
version 1: the answered history, industry, client type and last check go through two
Transformer blocks (attention written with plain operations, so the phone version matches),
and each candidate is scored against the session.

Decision rule, fixed before any result: version 2 replaces version 1 in the app only if
(1) it passes the held-out test of ml/custom_question_check.py (held-out question first at
least 50% of the time, top 3 at least 80%), (2) on the app's suggestion steps of the test
agents it is no more than 0.5 points below version 1, and (3) its TensorFlow Lite file
matches the trained model (largest probability difference under 0.0001).

Usage:
    python ml/next_question_v2.py holdout ml/data/onboarding_dataset_v3.csv   # the held-out test
    python ml/next_question_v2.py train   ml/data/onboarding_dataset_v3.csv   # train, evaluate, export
Simulated data only.
"""
import argparse
import json
import math
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf
from tensorflow.keras import layers

sys.path.insert(0, str(Path(__file__).resolve().parent))
from consistency import out_of_order_steps, standard_order  # noqa: E402
from next_field_model import build_description_vocab, encode_description  # noqa: E402
from offscript_breakdown import lookup_predictions, required_fields, tflite_predictions  # noqa: E402

MODELS = Path(__file__).resolve().parent / "models"
HELD_OUT = ["email_address", "profession", "us_person_status", "payment_method"]
MAX_DESC_LEN, MAX_CANDIDATES = 14, 128
POSITIONS, GAP = 40, 12  # positions 0-39; gaps -12..12
EMBEDDING_DIM, NUM_HEADS, FEED_FORWARD_DIM, NUM_BLOCKS, DROPOUT = 64, 4, 128, 2, 0.15
WORD_DROPOUT, DESCRIPTION_DROPOUT = 0.15, 0.2


# ---------------------------------------------------------------------------------------------
# Steps
# ---------------------------------------------------------------------------------------------

def candidate_features(candidates, playbook, required, history):
    """The facts the app knows about every candidate, including a company's own question."""
    last = next((playbook.index(f) for f in reversed(history) if f in playbook), -1)
    rule = next((c for c in candidates if c in required), None)
    feats = []
    for position, name in enumerate(candidates):
        index = playbook.index(name)
        feats.append((
            min(index, POSITIONS - 1),
            max(-GAP, min(GAP, index - last)) + GAP,
            1 if position == 0 else 0,
            1 if name == rule else 0,
            1 if name in required else 0,
        ))
    return feats


def steps_of(df, split, field_to_id, max_len, hidden=(), v1_meta=None):
    """The app's suggestion steps. With [hidden], training sees a world without those questions
    (removed from the sessions and the form); the test keeps them, as when a company adds one."""
    records = []
    for session_id, group in df[df["split"] == split].groupby("session_id", sort=True):
        group = group.reset_index(drop=True)
        industry, context = str(group["industry"].iloc[0]), str(group["context_value"].iloc[0])
        full_playbook = standard_order(industry, context)
        training_world = split != "test"
        if training_world and hidden:
            group = group[~group["onboarding_field"].isin(hidden)].reset_index(drop=True)
        playbook = [f for f in full_playbook if not (training_world and f in hidden)]
        required = required_fields(industry, context) - (set(hidden) if training_world else set())
        fields = group["onboarding_field"].astype(str).tolist()
        session_out_of_order = out_of_order_steps(fields, full_playbook)
        latest, history = {}, []
        for position in range(len(group) - 1):
            row = group.iloc[position]
            field = str(row["onboarding_field"])
            history.append(field)
            latest[field] = str(row["validation_status"])
            valid = {name for name, status in latest.items() if status == "passed"}
            if not (required - valid):
                continue
            candidates = [name for name in playbook if name not in valid]
            target = fields[position + 1]
            if training_world and target not in candidates:
                continue  # the app never offers it here, so it cannot be learned from this step
            ids = [field_to_id[f] for f in history if f in field_to_id][-max_len:]
            previous = history[-2] if len(history) > 1 else ""
            record = {
                "session_id": session_id,
                "industry": industry,
                "ids": [0] * (max_len - len(ids)) + ids,
                "history": list(history),
                "industry_name": industry, "context_name": context,
                "validation": str(row["validation_status"]),
                "candidates": candidates,
                "features": candidate_features(candidates, playbook, required, history),
                "target": target,
                "rule": next(n for n in playbook if n in required and n not in valid),
                "session_out_of_order": session_out_of_order,
                # For the lookup and for version 1, as in offscript_breakdown.py.
                "current_field": field, "previous_field": previous,
            }
            if v1_meta is not None:
                v1_ids = [v1_meta["field_to_id"][f] for f in history if f in v1_meta["field_to_id"]][-v1_meta["max_len"]:]
                record.update({
                    "input_ids": [0] * (v1_meta["max_len"] - len(v1_ids)) + v1_ids,
                    "industry_id": v1_meta["industry_to_id"][industry],
                    "context_id": v1_meta["context_to_id"][context],
                    "validation_id": v1_meta["validation_to_id"].get(str(row["validation_status"]), 0),
                })
            records.append(record)
    return records


def tensors(records, encoder, industry_to_id, context_to_id, n_candidates):
    n = len(records)
    out = {
        "input_ids": np.asarray([r["ids"] for r in records], np.int32),
        "industry_id": np.asarray([industry_to_id[r["industry_name"]] for r in records], np.int32),
        "context_id": np.asarray([context_to_id[r["context_name"]] for r in records], np.int32),
        "validation_id": np.asarray([{"passed": 1, "failed": 2}.get(r["validation"], 0) for r in records], np.int32),
        "candidate_description_ids": np.zeros((n, n_candidates, MAX_DESC_LEN), np.int32),
        "candidate_mask": np.zeros((n, n_candidates), np.int32),
        "candidate_features": np.zeros((n, n_candidates, 5), np.int32),
    }
    out["attention_mask"] = (out["input_ids"] > 0).astype(np.int32)
    targets = np.zeros(n, np.int32)
    for i, r in enumerate(records):
        for slot, name in enumerate(r["candidates"][:n_candidates]):
            out["candidate_description_ids"][i, slot] = encoder(name)
            out["candidate_mask"][i, slot] = 1
            out["candidate_features"][i, slot] = r["features"][slot]
        targets[i] = r["candidates"].index(r["target"]) if r["target"] in r["candidates"] else 0
    return out, targets


# ---------------------------------------------------------------------------------------------
# Model (plain operations only, so the TensorFlow Lite file matches it exactly)
# ---------------------------------------------------------------------------------------------

class PlainTransformerBlock(layers.Layer):
    def __init__(self, dim, heads, ff_dim, dropout, **kwargs):
        super().__init__(**kwargs)
        self.dim, self.heads, self.head_dim = dim, heads, dim // heads
        self.q, self.k, self.v, self.o = (layers.Dense(dim) for _ in range(4))
        self.ff1, self.ff2 = layers.Dense(ff_dim, activation="relu"), layers.Dense(dim)
        self.norm1, self.norm2 = layers.LayerNormalization(epsilon=1e-6), layers.LayerNormalization(epsilon=1e-6)
        self.drop1, self.drop2 = layers.Dropout(dropout), layers.Dropout(dropout)

    def split(self, x):
        b, s = tf.shape(x)[0], tf.shape(x)[1]
        return tf.transpose(tf.reshape(x, [b, s, self.heads, self.head_dim]), [0, 2, 1, 3])

    def call(self, x, mask, training=None):
        q, k, v = self.split(self.q(x)), self.split(self.k(x)), self.split(self.v(x))
        scores = tf.matmul(q, k, transpose_b=True) / math.sqrt(self.head_dim)
        scores += (1.0 - mask[:, tf.newaxis, tf.newaxis, :]) * -1e9
        attended = tf.matmul(tf.nn.softmax(scores, axis=-1), v)
        b, s = tf.shape(x)[0], tf.shape(x)[1]
        attended = tf.reshape(tf.transpose(attended, [0, 2, 1, 3]), [b, s, self.dim])
        x = self.norm1(x + self.drop1(self.o(attended), training=training))
        return self.norm2(x + self.drop2(self.ff2(self.ff1(x)), training=training))


class NextQuestionV2(tf.keras.Model):
    def __init__(self, vocab_size, word_vocab_size, max_len, n_industries, n_contexts, **kwargs):
        super().__init__(**kwargs)
        self.max_len = max_len
        self.token = layers.Embedding(vocab_size, EMBEDDING_DIM)
        self.position = self.add_weight(name="position", shape=(max_len, EMBEDDING_DIM), initializer="uniform")
        self.blocks = [PlainTransformerBlock(EMBEDDING_DIM, NUM_HEADS, FEED_FORWARD_DIM, DROPOUT) for _ in range(NUM_BLOCKS)]
        self.industry = layers.Embedding(n_industries, 8)
        self.context = layers.Embedding(n_contexts, 8)
        self.validation = layers.Embedding(3, 4)
        self.session = layers.Dense(128, activation="relu")
        self.session_drop = layers.Dropout(DROPOUT)
        self.session_projection = layers.Dense(64)
        self.words = layers.Embedding(word_vocab_size, 32)
        self.text_projection = layers.Dense(64)
        self.feature_tables = [layers.Embedding(POSITIONS, 16), layers.Embedding(2 * GAP + 1, 16),
                               layers.Embedding(2, 4), layers.Embedding(2, 4), layers.Embedding(2, 4)]
        self.feature_projection = layers.Dense(64)
        self.feature_bias = layers.Dense(1)

    def session_vector(self, inputs, training=None):
        mask = tf.cast(inputs["attention_mask"], tf.float32)
        x = self.token(inputs["input_ids"]) + self.position[tf.newaxis, :, :]
        for block in self.blocks:
            x = block(x, mask, training=training)
        combined = tf.concat([x[:, -1, :], self.industry(inputs["industry_id"]),
                              self.context(inputs["context_id"]), self.validation(inputs["validation_id"])], axis=-1)
        return self.session_projection(self.session_drop(self.session(combined), training=training))

    def call(self, inputs, training=None):
        session = self.session_vector(inputs, training)                        # (b, 64)
        words = inputs["candidate_description_ids"]                            # (b, c, d)
        if training:
            unknown = tf.random.uniform(tf.shape(words)) < WORD_DROPOUT
            whole = tf.random.uniform(tf.shape(words)[:2])[:, :, tf.newaxis] < DESCRIPTION_DROPOUT
            words = tf.where(tf.logical_and(words > 0, tf.logical_or(unknown, whole)), tf.ones_like(words), words)
        word_mask = tf.cast(words > 0, tf.float32)[..., tf.newaxis]
        pooled = tf.reduce_sum(self.words(words) * word_mask, axis=2) / tf.maximum(tf.reduce_sum(word_mask, axis=2), 1.0)
        features = inputs["candidate_features"]
        feature_vectors = tf.concat([table(features[:, :, i]) for i, table in enumerate(self.feature_tables)], axis=-1)
        candidate = self.text_projection(pooled) + self.feature_projection(feature_vectors)   # (b, c, 64)
        scores = tf.reduce_sum(candidate * session[:, tf.newaxis, :], axis=-1) + self.feature_bias(feature_vectors)[..., 0]
        scores = tf.where(tf.cast(inputs["candidate_mask"], tf.bool), scores, tf.fill(tf.shape(scores), -1e9))
        return tf.nn.softmax(scores, axis=-1)


def train_model(train_records, val_records, encoder, field_to_id, vocab, max_len, industry_to_id, context_to_id):
    n_candidates = max(len(r["candidates"]) for r in train_records + val_records)
    train_x, train_y = tensors(train_records, encoder, industry_to_id, context_to_id, n_candidates)
    val_x, val_y = tensors(val_records, encoder, industry_to_id, context_to_id, n_candidates)
    tf.keras.utils.set_random_seed(42)
    model = NextQuestionV2(len(field_to_id) + 1, len(vocab), max_len, len(industry_to_id), len(context_to_id))
    model.compile(optimizer=tf.keras.optimizers.AdamW(learning_rate=1e-3, weight_decay=2e-4),
                  loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    history = model.fit(train_x, train_y, validation_data=(val_x, val_y), epochs=25, batch_size=512, verbose=2,
                        callbacks=[tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=3, restore_best_weights=True)])
    return model, len(history.history["loss"])


def rankings(model, records, encoder, industry_to_id, context_to_id):
    out = []
    for start in range(0, len(records), 2048):
        chunk = records[start:start + 2048]
        x, _ = tensors(chunk, encoder, industry_to_id, context_to_id, max(len(r["candidates"]) for r in chunk))
        probabilities = model(x, training=False).numpy()
        for r, p in zip(chunk, probabilities):
            order = np.argsort(-p[:len(r["candidates"])])
            out.append([r["candidates"][i] for i in order])
    return out


def setup(df, hidden):
    v1_meta = json.loads((MODELS / "v1" / "preprocessing_meta.json").read_text(encoding="utf-8"))
    descriptions = v1_meta["field_descriptions"]
    known = sorted(set(df["onboarding_field"]) - set(hidden))
    field_to_id = {f: i + 1 for i, f in enumerate(known)}
    vocab = build_description_vocab([descriptions[f] for f in known] + [f.replace("_", " ") for f in known])
    encoder = lambda name: encode_description(descriptions[name], vocab, MAX_DESC_LEN)
    industry_to_id = {v: i for i, v in enumerate(sorted(df["industry"].unique()))}
    context_to_id = {v: i for i, v in enumerate(sorted(df["context_value"].unique()))}
    max_len = int(df[~df["onboarding_field"].isin(hidden)].groupby("session_id").size().max())
    return v1_meta, descriptions, field_to_id, vocab, encoder, industry_to_id, context_to_id, max_len


def pct(values):
    return round(100 * float(np.mean(values)), 1) if len(values) else None


# ---------------------------------------------------------------------------------------------
# The held-out test (same questions and pass mark as ml/custom_question_check.py)
# ---------------------------------------------------------------------------------------------

def holdout(args):
    df = pd.read_csv(args.csv).sort_values(["session_id", "event_timestamp"])
    v1_meta, _, field_to_id, vocab, encoder, industry_to_id, context_to_id, max_len = setup(df, HELD_OUT)
    train = steps_of(df, "train", field_to_id, max_len, hidden=HELD_OUT)
    val = steps_of(df, "validation", field_to_id, max_len, hidden=HELD_OUT)
    test = steps_of(df, "test", field_to_id, max_len, hidden=HELD_OUT, v1_meta=v1_meta)
    model, epochs = train_model(train, val, encoder, field_to_id, vocab, max_len, industry_to_id, context_to_id)
    ranked = rankings(model, test, encoder, industry_to_id, context_to_id)
    shipped = tflite_predictions(test, v1_meta)
    target = [r["target"] for r in test]
    on_held = [i for i, r in enumerate(test) if r["target"] in HELD_OUT and r["target"] in r["candidates"]]
    other = [i for i, r in enumerate(test) if r["target"] not in HELD_OUT]

    def block(rows):
        return {
            "steps": len(rows),
            "v2_without_these_questions_top1": pct([target[i] in ranked[i][:1] for i in rows]),
            "v2_without_these_questions_top3": pct([target[i] in ranked[i][:3] for i in rows]),
            "v1_shipped_top1": pct([shipped[i] == target[i] for i in rows]),
            "app_rule_top1": pct([test[i]["rule"] == target[i] for i in rows]),
            "chance_top1": pct([1 / len(test[i]["candidates"]) for i in rows]),
        }

    held, rest = block(on_held), block(other)
    report = {
        "dataset": Path(args.csv).name,
        "model": "version 2 (candidate position features), trained without the held-out questions",
        "held_out_questions": HELD_OUT,
        "epochs": epochs,
        "steps_where_a_held_out_question_is_next": held,
        "per_held_out_question": {q: block([i for i in on_held if target[i] == q]) for q in HELD_OUT},
        "all_other_steps": rest,
        "other_steps_where_a_held_out_question_was_wrongly_picked_pct": pct([ranked[i][0] in HELD_OUT for i in other]),
        "claim_supported": bool(held["v2_without_these_questions_top1"] >= 50 and held["v2_without_these_questions_top3"] >= 80
                                and rest["v1_shipped_top1"] - rest["v2_without_these_questions_top1"] <= 3),
    }
    out = MODELS / "custom_question_check_v2.json"
    out.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


# ---------------------------------------------------------------------------------------------
# Full training, evaluation against version 1, and export
# ---------------------------------------------------------------------------------------------

def mcnemar(a, b):
    only_a, only_b = int((a & ~b).sum()), int((~a & b).sum())
    return {"only_v2_right": only_a, "only_other_right": only_b,
            "z": round((only_a - only_b) / max(math.sqrt(only_a + only_b), 1e-9), 1)}


def export(model, encoder, max_len, path):
    spec = [tf.TensorSpec([1, max_len], tf.int32, name="input_ids"),
            tf.TensorSpec([1, max_len], tf.int32, name="attention_mask"),
            tf.TensorSpec([1], tf.int32, name="industry_id"),
            tf.TensorSpec([1], tf.int32, name="context_id"),
            tf.TensorSpec([1], tf.int32, name="validation_id"),
            tf.TensorSpec([1, MAX_CANDIDATES, MAX_DESC_LEN], tf.int32, name="candidate_description_ids"),
            tf.TensorSpec([1, MAX_CANDIDATES], tf.int32, name="candidate_mask"),
            tf.TensorSpec([1, MAX_CANDIDATES, 5], tf.int32, name="candidate_features")]

    @tf.function(input_signature=spec)
    def forward(input_ids, attention_mask, industry_id, context_id, validation_id,
                candidate_description_ids, candidate_mask, candidate_features):
        return {"output_0": model({
            "input_ids": input_ids, "attention_mask": attention_mask, "industry_id": industry_id,
            "context_id": context_id, "validation_id": validation_id,
            "candidate_description_ids": candidate_description_ids, "candidate_mask": candidate_mask,
            "candidate_features": candidate_features}, training=False)}

    # No trackable object: the notebook found that passing one corrupts the converted weights.
    converter = tf.lite.TFLiteConverter.from_concrete_functions([forward.get_concrete_function()])
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS, tf.lite.OpsSet.SELECT_TF_OPS]
    path.write_bytes(converter.convert())
    return forward


def check_tflite(model, path, records, encoder, industry_to_id, context_to_id):
    interpreter = tf.lite.Interpreter(model_path=str(path))
    interpreter.allocate_tensors()
    inputs = {d["name"]: d for d in interpreter.get_input_details()}
    output = interpreter.get_output_details()[0]
    keys = sorted(["input_ids", "attention_mask", "industry_id", "context_id", "validation_id",
                   "candidate_description_ids", "candidate_mask", "candidate_features"], key=len, reverse=True)
    rng = np.random.default_rng(2026)
    worst, mismatches = 0.0, 0
    for index in rng.choice(len(records), size=50, replace=False):
        x, _ = tensors([records[index]], encoder, industry_to_id, context_to_id, MAX_CANDIDATES)
        keras = model(x, training=False).numpy()[0]
        for name, detail in inputs.items():
            interpreter.set_tensor(detail["index"], x[next(k for k in keys if k in name)])
        interpreter.invoke()
        lite = interpreter.get_tensor(output["index"])[0]
        worst = max(worst, float(np.max(np.abs(keras - lite))))
        mismatches += int(np.argmax(keras) != np.argmax(lite))
    return worst, mismatches


def train(args):
    df = pd.read_csv(args.csv).sort_values(["session_id", "event_timestamp"])
    v1_meta, descriptions, field_to_id, vocab, encoder, industry_to_id, context_to_id, max_len = setup(df, ())
    train_records = steps_of(df, "train", field_to_id, max_len)
    val_records = steps_of(df, "validation", field_to_id, max_len)
    test = steps_of(df, "test", field_to_id, max_len, v1_meta=v1_meta)
    model, epochs = train_model(train_records, val_records, encoder, field_to_id, vocab, max_len, industry_to_id, context_to_id)
    ranked = rankings(model, test, encoder, industry_to_id, context_to_id)

    # Version 1 and the baselines on exactly the same steps.
    v1 = tflite_predictions(test, v1_meta)
    lookup = lookup_predictions(steps_of(df, "train", field_to_id, max_len, v1_meta=v1_meta), test)
    target = np.asarray([r["target"] for r in test])
    right = {
        "v2": np.asarray([r[0] for r in ranked]) == target,
        "v1": np.asarray(v1) == target,
        "rule": np.asarray([r["rule"] for r in test]) == target,
        "lookup": np.asarray(lookup) == target,
    }

    def block(keep):
        rows = np.asarray([i for i, r in enumerate(test) if keep(r)])
        return {"steps": int(len(rows)), **{k: round(float(v[rows].mean()) * 100, 1) for k, v in right.items()},
                "v2_top3": pct([target[i] in ranked[i][:3] for i in rows]),
                "v2_vs_v1": mcnemar(right["v2"][rows], right["v1"][rows]),
                "v2_vs_lookup": mcnemar(right["v2"][rows], right["lookup"][rows])}

    report = {
        "dataset": Path(args.csv).name,
        "epochs": epochs,
        "all_steps": block(lambda r: True),
        "standard_order_sessions": block(lambda r: r["session_out_of_order"] == 0),
        "off_script_sessions": block(lambda r: r["session_out_of_order"] >= 1),
        "banking": block(lambda r: r["industry"] == "banking"),
        "telecom": block(lambda r: r["industry"] == "telecom"),
    }
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    tflite_path = out_dir / "onboarding_model.tflite"
    export(model, encoder, max_len, tflite_path)
    worst, mismatches = check_tflite(model, tflite_path, test, encoder, industry_to_id, context_to_id)
    report["tflite"] = {"bytes": tflite_path.stat().st_size, "largest_probability_difference": worst,
                        "top1_mismatches_in_50": mismatches}
    meta = {
        "model_version": 2,
        "field_vocab": sorted(field_to_id, key=field_to_id.get), "num_classes": len(field_to_id),
        "field_to_id": field_to_id, "max_len": max_len,
        "industry_to_id": industry_to_id, "context_to_id": context_to_id,
        "validation_to_id": {"NONE": 0, "passed": 1, "failed": 2},
        "description_vocab": vocab, "max_desc_len": MAX_DESC_LEN, "max_candidates": MAX_CANDIDATES,
        "field_descriptions": {f: descriptions[f] for f in field_to_id},
        "candidate_features": ["form_position", "gap_from_last_answered", "first_open", "plain_rule_choice", "required"],
        "max_form_position": POSITIONS - 1, "max_gap": GAP,
        "candidate_scoring": "description_and_form_position",
    }
    (out_dir / "preprocessing_meta.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    (out_dir / "next_question_v2_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)
    h = sub.add_parser("holdout")
    h.add_argument("csv")
    t = sub.add_parser("train")
    t.add_argument("csv")
    t.add_argument("--out-dir", default=str(MODELS))
    args = parser.parse_args()
    holdout(args) if args.command == "holdout" else train(args)


if __name__ == "__main__":
    main()
