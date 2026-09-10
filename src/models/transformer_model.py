import json
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow.keras import layers


DEFAULT_CONFIG_PATH = Path("data/transformer_inputs/preprocessing_config.json")
DEFAULT_DATA_DIR = Path("data/transformer_inputs")


class PositionalEmbedding(layers.Layer):
    def __init__(self, max_sequence_length, embedding_dim, **kwargs):
        super().__init__(**kwargs)
        self.max_sequence_length = max_sequence_length
        self.embedding_dim = embedding_dim
        self.position_embedding = self.add_weight(
            name="position_embedding",
            shape=(max_sequence_length, embedding_dim),
            initializer="uniform",
            trainable=True,
        )

    def call(self, inputs):
        seq_len = tf.shape(inputs)[1]
        positional_inputs = self.position_embedding[:seq_len]
        return inputs + positional_inputs


class TransformerBlock(layers.Layer):
    def __init__(self, embedding_dim, num_heads, ff_dim, dropout_rate=0.1, **kwargs):
        super().__init__(**kwargs)
        self.embedding_dim = embedding_dim
        self.num_heads = num_heads
        self.ff_dim = ff_dim
        self.dropout_rate = dropout_rate

        self.self_attention = layers.MultiHeadAttention(
            num_heads=num_heads,
            key_dim=embedding_dim // num_heads,
            dropout=dropout_rate,
        )
        self.norm1 = layers.LayerNormalization(epsilon=1e-6)
        self.norm2 = layers.LayerNormalization(epsilon=1e-6)
        self.ffn = tf.keras.Sequential(
            [
                layers.Dense(ff_dim, activation="relu"),
                layers.Dense(embedding_dim),
                layers.Dropout(dropout_rate),
            ]
        )
        self.dropout1 = layers.Dropout(dropout_rate)
        self.dropout2 = layers.Dropout(dropout_rate)

    def call(self, inputs, attention_mask=None):
        attention_output = self.self_attention(
            query=inputs,
            value=inputs,
            key=inputs,
            attention_mask=attention_mask,
        )
        attention_output = self.dropout1(attention_output)
        residual_output = self.norm1(inputs + attention_output)

        feed_forward_output = self.ffn(residual_output)
        feed_forward_output = self.dropout2(feed_forward_output)
        return self.norm2(residual_output + feed_forward_output)


def load_preprocessed_data(data_dir=DEFAULT_DATA_DIR):
    data_dir = Path(data_dir)

    with open(data_dir / "preprocessing_config.json", "r", encoding="utf-8") as file:
        config = json.load(file)

    train_x = np.load(data_dir / "train_inputs.npy").astype(np.int32)
    train_mask = np.load(data_dir / "train_masks.npy").astype(bool)
    train_y = np.load(data_dir / "train_targets.npy").astype(np.int32) - 1

    val_x = np.load(data_dir / "validation_inputs.npy").astype(np.int32)
    val_mask = np.load(data_dir / "validation_masks.npy").astype(bool)
    val_y = np.load(data_dir / "validation_targets.npy").astype(np.int32) - 1

    test_x = np.load(data_dir / "test_inputs.npy").astype(np.int32)
    test_mask = np.load(data_dir / "test_masks.npy").astype(bool)
    test_y = np.load(data_dir / "test_targets.npy").astype(np.int32) - 1

    return config, (train_x, train_mask, train_y), (val_x, val_mask, val_y), (test_x, test_mask, test_y)


def build_transformer_model(
    vocab_size,
    max_sequence_length,
    embedding_dim=64,
    num_heads=4,
    ff_dim=128,
    num_transformer_blocks=2,
    dropout_rate=0.1,
    name="onboarding_transformer",
):
    input_ids = layers.Input(shape=(max_sequence_length,), dtype=tf.int32, name="input_ids")
    attention_mask = layers.Input(shape=(max_sequence_length,), dtype=tf.bool_, name="attention_mask")

    token_embedding = layers.Embedding(
        input_dim=vocab_size,
        output_dim=embedding_dim,
        mask_zero=True,
        name="token_embedding",
    )

    x = token_embedding(input_ids)
    x = PositionalEmbedding(max_sequence_length, embedding_dim)(x)

    attention_mask_4d = tf.cast(attention_mask[:, tf.newaxis, tf.newaxis, :], tf.bool)

    for _ in range(num_transformer_blocks):
        x = TransformerBlock(
            embedding_dim=embedding_dim,
            num_heads=num_heads,
            ff_dim=ff_dim,
            dropout_rate=dropout_rate,
        )(x, attention_mask=attention_mask_4d)

    last_valid_index = tf.reduce_sum(tf.cast(attention_mask, tf.int32), axis=1) - 1
    pooled = tf.gather(x, last_valid_index, batch_dims=1, axis=1)
    outputs = layers.Dense(
        units=33,
        activation="softmax",
        name="next_field_probabilities",
    )(pooled)

    model = tf.keras.Model(
        inputs=[input_ids, attention_mask],
        outputs=outputs,
        name=name,
    )
    return model


def main():
    config, train_data, val_data, test_data = load_preprocessed_data(DEFAULT_DATA_DIR)
    max_sequence_length = config["max_sequence_length"]
    vocab_size = config["vocabulary_size"] + 1

    model = build_transformer_model(
        vocab_size=vocab_size,
        max_sequence_length=max_sequence_length,
        embedding_dim=64,
        num_heads=4,
        ff_dim=128,
        num_transformer_blocks=2,
        dropout_rate=0.1,
    )

    train_x, train_mask, train_y = train_data
    model_output = model([train_x[:2], train_mask[:2]])
    print(f"Model output shape: {model_output.shape}")
    print(f"Model built successfully with {model.count_params()} parameters.")


if __name__ == "__main__":
    main()
