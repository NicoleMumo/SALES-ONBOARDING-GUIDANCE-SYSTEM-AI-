"""Compare simple next-field baselines on a session- or agent-level split."""

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

import pandas as pd
from sklearn.metrics import f1_score

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = ROOT / "data" / "onboarding_dataset.csv"

BANKING_INDIVIDUAL = [
    "full_name", "email_address", "mobile_phone_number", "date_of_birth",
    "home_address", "identification_document", "identification_document_type",
    "identification_document_number", "profession", "income",
    "assets", "us_person_status", "politically_exposed_person_status",
    "terms_and_conditions_acceptance",
]
BANKING_BUSINESS = ["full_name", "business_name", *BANKING_INDIVIDUAL[1:],
    "commercial_address", "beneficial_owner_information", "business_relationship_purpose",
    "business_relationship_nature", "ownership_and_control_information",
]
TELECOM_BASE = [
    "full_name", "email_address", "contact_phone_number", "date_of_birth", "gender",
    "physical_address", "identification_document", "subscriber_type", "service_type",
    "contract_type", "payment_method", "paperless_billing_preference",
]


def canonical_prediction(history: list[str], industry: str, context: str) -> str:
    if industry == "banking":
        workflow = BANKING_BUSINESS if context == "business" else BANKING_INDIVIDUAL
    else:
        service_fields = {
            "mobile": ["phone_service_selection"],
            "landline": ["phone_service_selection"],
            "broadband": ["internet_service_selection", "streaming_addons_selection"],
            "tv": ["internet_service_selection", "streaming_addons_selection"],
            "iot": [],
        }
        workflow = TELECOM_BASE + service_fields.get(context, []) + ["subscriber_number"]
    return next((field for field in workflow if field not in history), workflow[-1])


def _ranked_lookup(train_rows, key_fn):
    counts = defaultdict(Counter)
    overall = Counter()
    for row in train_rows:
        target = row["target"]
        counts[key_fn(row)][target] += 1
        overall[target] += 1
    fallback = [field for field, _ in overall.most_common()]

    def predict(row):
        return [field for field, _ in counts[key_fn(row)].most_common()] or fallback

    return predict


def _features(dataset: pd.DataFrame):
    rows = []
    ordered = dataset.sort_values(["session_id", "event_timestamp"])
    for _, session in ordered.groupby("session_id", sort=False):
        history = []
        for event in session.to_dict("records"):
            target = event.get("next_onboarding_field")
            field = event.get("onboarding_field")
            if not target or not field:
                continue
            is_revisit = str(event["is_revisit"]).lower() in {"true", "1"} or field in history
            is_retry_or_revisit = is_revisit or event["validation_status"] == "failed"
            prefix = history + [field]
            rows.append({
                "session_id": event["session_id"],
                "target": target,
                "industry": event["industry"],
                "context": event["context_value"],
                "field": field,
                "validation": event["validation_status"],
                "history": prefix,
                "is_retry_or_revisit": is_retry_or_revisit,
            })
            history.append(field)
    return rows


def _metrics(rows, predictions):
    actual = [row["target"] for row in rows]
    if not actual:
        return {"count": 0, "top1_accuracy": 0.0, "top3_accuracy": 0.0, "macro_f1": 0.0}
    top1 = [ranked[0] if ranked else "" for ranked in predictions]
    labels = sorted(set(actual) | set(top1))
    return {
        "count": len(actual),
        "top1_accuracy": sum(a == p for a, p in zip(actual, top1)) / max(1, len(actual)),
        "top3_accuracy": sum(a in ranked[:3] for a, ranked in zip(actual, predictions)) / max(1, len(actual)),
        "macro_f1": float(f1_score(actual, top1, labels=labels, average="macro", zero_division=0)),
    }


def _transformer_predictions(rows):
    import numpy as np
    import tensorflow as tf

    from field_similarity_head import FieldSimilarityHead
    from model_def import ExpandAttentionMask, GatherLastToken, PositionalEmbedding, TransformerBlock

    models_dir = ROOT / "src" / "models"
    metadata = json.loads((models_dir / "preprocessing_meta.json").read_text(encoding="utf-8"))
    model = tf.keras.models.load_model(
        models_dir / "onboarding_model_flexible.keras",
        custom_objects={
            "GatherLastToken": GatherLastToken,
            "PositionalEmbedding": PositionalEmbedding,
            "ExpandAttentionMask": ExpandAttentionMask,
            "TransformerBlock": TransformerBlock,
            "FieldSimilarityHead": FieldSimilarityHead,
        },
        compile=False,
    )
    field_to_id = metadata["field_to_id"]
    max_length = metadata["max_len"]
    input_ids, masks, industries, contexts, validations = [], [], [], [], []
    for row in rows:
        history_ids = [field_to_id[field] for field in row["history"]][-max_length:]
        padding = max_length - len(history_ids)
        input_ids.append([0] * padding + history_ids)
        masks.append([False] * padding + [True] * len(history_ids))
        industries.append(metadata["industry_to_id"][row["industry"]])
        contexts.append(metadata["context_to_id"][row["context"]])
        validations.append(metadata["validation_to_id"].get(row["validation"], 0))

    output = model.predict(
        [
            np.asarray(input_ids, dtype=np.int32),
            np.asarray(masks, dtype=bool),
            np.asarray(industries, dtype=np.int32),
            np.asarray(contexts, dtype=np.int32),
            np.asarray(validations, dtype=np.int32),
        ],
        batch_size=512,
        verbose=0,
    )
    index_to_field = {value - 1: field for field, value in field_to_id.items()}
    return [
        [index_to_field[index] for index in np.argsort(probabilities)[::-1][:5]]
        for probabilities in output
    ]


def evaluate(dataset_path: Path, split_column: str, seed: int) -> dict:
    data = pd.read_csv(dataset_path)
    required = {"session_id", "event_timestamp", "industry", "context_value", "onboarding_field",
                "next_onboarding_field", "validation_status", "is_revisit", split_column}
    missing = required - set(data.columns)
    if missing:
        raise ValueError(f"Dataset is missing columns: {sorted(missing)}")

    train_ids = set(data.loc[data[split_column] == "train", "session_id"])
    test_ids = set(data.loc[data[split_column] == "test", "session_id"])
    rows = _features(data[data.session_id.isin(train_ids | test_ids)])
    train = [row for row in rows if row["session_id"] in train_ids]
    test = [row for row in rows if row["session_id"] in test_ids]
    if not train or not test:
        raise ValueError(f"Split column '{split_column}' must contain train and test sessions.")

    overall = Counter(row["target"] for row in train)
    global_prediction = [field for field, _ in overall.most_common()]
    lookup = _ranked_lookup(
        train, lambda row: (row["industry"], row["context"], row["field"], row["validation"])
    )
    ngram = _ranked_lookup(
        train, lambda row: (row["industry"], row["context"], tuple(row["history"][-2:]), row["validation"])
    )
    models = {
        "canonical_workflow": lambda row: [canonical_prediction(row["history"], row["industry"], row["context"])],
        "global_majority": lambda _row: global_prediction,
        "majority_lookup": lookup,
        "two_field_markov": ngram,
    }
    results = {}
    for name, predict in models.items():
        predictions = [predict(row) for row in test]
        results[name] = {
            "all": _metrics(test, predictions),
            "retry_or_revisit": _metrics(
                [row for row in test if row["is_retry_or_revisit"]],
                [ranked for row, ranked in zip(test, predictions) if row["is_retry_or_revisit"]],
            ),
        }
    transformer_path = ROOT / "src" / "models" / "onboarding_model_flexible.keras"
    if split_column == "split" and transformer_path.is_file():
        predictions = _transformer_predictions(test)
        results["transformer"] = {
            "all": _metrics(test, predictions),
            "retry_or_revisit": _metrics(
                [row for row in test if row["is_retry_or_revisit"]],
                [ranked for row, ranked in zip(test, predictions) if row["is_retry_or_revisit"]],
            ),
        }
    elif split_column == "agent_split":
        results["transformer"] = {
            "status": "not_evaluated",
            "reason": "Retrain the notebook model using agent_split before evaluating this protocol.",
        }
    else:
        results["transformer"] = {
            "status": "not_evaluated",
            "reason": "The saved Keras model artifact is unavailable.",
        }
    return {
        "dataset": str(dataset_path),
        "split_column": split_column,
        "seed": seed,
        "train_sessions": len(train_ids),
        "test_sessions": len(test_ids),
        "test_transitions": len(test),
        "baselines": results,
        "limitation": "Scenario-generated synthetic data; metrics do not establish real-world performance.",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--split-column", choices=("split", "agent_split"), default="split")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = evaluate(args.dataset, args.split_column, args.seed)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result["baselines"], indent=2))
    print(f"Saved metrics to {args.output}")


if __name__ == "__main__":
    main()
