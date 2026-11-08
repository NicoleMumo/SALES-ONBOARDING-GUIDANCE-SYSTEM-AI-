"""How well does the next-question model cope when agents are messier than the simulator?

The simulated sessions are tidy: most questions come where the form puts them, which is why the
held-out test scored so high. This check adds noise to the TEST sessions only (every method was
trained on clean data) and scores the model, the app's plain rule and the best lookup on exactly
the same noisy steps.

Noise at level p (0%, 10%, 20%, 30%): in a share p of sessions the agent breaks the order again:
one question is moved to a random point in the session, and each pair of neighbouring questions
is swapped with probability p / 2. In the held-out check the moved question is a held-out one
whenever the session has one, so the questions the model never saw also turn up out of place.

Two parts:
- shipped: the shipped model (ml/models/onboarding_model.tflite) on all suggestion steps;
- held-out: the model trained without the four held-out questions (as in next_question_v2.py
  holdout), on the steps where a held-out question is the right next one.

A good result: the model falls more slowly than the rule and the lookup as the noise grows.
Fixed seeds, simulated data only. Usage:
    python ml/noise_check.py ml/data/onboarding_dataset_v3.csv [--out ml/models/noise_check.json]
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent))
import next_question_v2 as v2  # noqa: E402
from offscript_breakdown import lookup_predictions  # noqa: E402
from try_next_question import ShippedModel  # noqa: E402

LEVELS = [0.0, 0.1, 0.2, 0.3]
MODELS = Path(__file__).resolve().parent / "models"


def add_noise(df, level, seed, prefer=()):
    """A copy of the test sessions in which a share [level] of agents break the order again."""
    rng = np.random.default_rng(seed)
    parts = [df[df["split"] != "test"]]
    for _, group in df[df["split"] == "test"].groupby("session_id", sort=True):
        rows = list(group.index)
        if level > 0 and rng.random() < level and len(rows) > 2:
            preferred = [i for i in rows if group.at[i, "onboarding_field"] in prefer]
            moved = rng.choice(preferred) if preferred else rng.choice(rows)
            rows.remove(moved)
            rows.insert(int(rng.integers(0, len(rows) + 1)), moved)
            for k in range(len(rows) - 1):
                if rng.random() < level / 2:
                    rows[k], rows[k + 1] = rows[k + 1], rows[k]
        parts.append(group.loc[rows])
    # Keep each session's (possibly new) order: steps_of reads rows in the order given.
    return pd.concat(parts)


def score(records, chosen):
    target = np.asarray([r["target"] for r in records])
    return round(100 * float(np.mean(np.asarray(chosen) == target)), 1) if len(records) else None


def shipped_part(df, field_to_id, max_len):
    model = ShippedModel()
    meta = model.meta
    train = v2.steps_of(df, "train", field_to_id, max_len, v1_meta=None)
    for r in train:
        r["context_id"] = meta["context_to_id"][r["context_name"]]
        r["validation_id"] = meta["validation_to_id"].get(r["validation"], 0)
    out = {}
    for level in LEVELS:
        noisy = add_noise(df, level, seed=2026)
        test = v2.steps_of(noisy, "test", field_to_id, max_len)
        for r in test:
            r["context_id"] = meta["context_to_id"][r["context_name"]]
            r["validation_id"] = meta["validation_to_id"].get(r["validation"], 0)
        playbook = {}
        picks = []
        for r in test:
            key = (r["industry_name"], r["context_name"])
            playbook.setdefault(key, (v2.standard_order(*key), v2.required_fields(*key)))
            history = r["history"]
            ranked = model.rank(history, r["industry_name"], r["context_name"], r["validation"],
                                r["candidates"], playbook[key][0], playbook[key][1])
            picks.append(ranked[0][0])
        out[f"{int(level * 100)}%"] = {
            "steps": len(test),
            "model": score(test, picks),
            "rule": score(test, [r["rule"] for r in test]),
            "lookup": score(test, lookup_predictions(train, test)),
        }
        print("shipped", level, out[f"{int(level * 100)}%"], flush=True)
    return out


def held_out_part(df):
    hidden = v2.HELD_OUT
    v1_meta, _, field_to_id, vocab, encoder, industry_to_id, context_to_id, max_len = v2.setup(df, hidden)
    train = v2.steps_of(df, "train", field_to_id, max_len, hidden=hidden)
    val = v2.steps_of(df, "validation", field_to_id, max_len, hidden=hidden)
    model, _ = v2.train_model(train, val, encoder, field_to_id, vocab, max_len, industry_to_id, context_to_id)
    out = {}
    for level in LEVELS:
        noisy = add_noise(df, level, seed=7, prefer=hidden)
        test = [r for r in v2.steps_of(noisy, "test", field_to_id, max_len, hidden=hidden)
                if r["target"] in hidden and r["target"] in r["candidates"]]
        ranked = v2.rankings(model, test, encoder, industry_to_id, context_to_id)
        out[f"{int(level * 100)}%"] = {
            "steps": len(test),
            "model_never_saw_them_top1": score(test, [x[0] for x in ranked]),
            "model_never_saw_them_top3": round(100 * float(np.mean([r["target"] in x[:3] for r, x in zip(test, ranked)])), 1),
            "rule": score(test, [r["rule"] for r in test]),
            "chance": round(100 * float(np.mean([1 / len(r["candidates"]) for r in test])), 1),
        }
        print("held-out", level, out[f"{int(level * 100)}%"], flush=True)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("csv")
    parser.add_argument("--out", default=str(MODELS / "noise_check.json"))
    args = parser.parse_args()
    df = pd.read_csv(args.csv).sort_values(["session_id", "event_timestamp"]).reset_index(drop=True)
    _, _, field_to_id, _, _, _, _, max_len = v2.setup(df, ())
    report = {
        "dataset": Path(args.csv).name,
        "note": "Noise added to test sessions only; every method trained on clean data. Simulated data.",
        "noise": "share of sessions where one question moves to a random point and neighbours swap with probability p/2",
        "shipped_model_all_steps": shipped_part(df, field_to_id, max_len),
        "held_out_questions_steps": held_out_part(df),
    }
    Path(args.out).write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
