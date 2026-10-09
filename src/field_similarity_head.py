"""
FieldSimilarityHead -- replaces the fixed Dense(NUM_CLASSES) output layer.

Standard softmax classification: logits = session_vector @ W + b, where W
is a (embedding_dim, num_classes) matrix of NUM_CLASSES independently
learned vectors, one per class, fixed at training time. No way to add a
class later without adding a row to W and retraining.

This layer instead learns a *shared* text encoder that turns any field's
one-sentence description into a vector. Logits = session_vector @
field_description_vectors.T. The known fields' vectors are computed from
their descriptions using that shared encoder -- so a brand-new field,
described in the same style, gets a real vector from the same encoder
without touching any trained weights.

This is the standard "dual encoder" / zero-shot classification pattern:
one encoder for the input, one (shared, generalizable) encoder for the
candidate labels, prediction = similarity.
"""

import numpy as np
import tensorflow as tf
from tensorflow.keras import layers


def build_description_vocab(descriptions):
    """Tiny whitespace tokenizer + vocab, no external dependencies."""
    vocab = {"<pad>": 0, "<unk>": 1}
    for desc in descriptions:
        for word in desc.lower().replace("-", " ").replace(",", " ").split():
            if word not in vocab:
                vocab[word] = len(vocab)
    return vocab


def encode_description(description, vocab, max_desc_len):
    words = description.lower().replace("-", " ").replace(",", " ").split()
    ids = [vocab.get(w, vocab["<unk>"]) for w in words][:max_desc_len]
    return ids + [vocab["<pad>"]] * (max_desc_len - len(ids))


class FieldSimilarityHead(layers.Layer):
    """
    Predicts by comparing a session vector against field-description
    vectors, instead of a fixed per-class weight matrix.

    - `field_names`, `field_descriptions`: the known fields at training
      time, in a fixed order (index i is class i).
    - `vocab`: word -> id, built once over the training descriptions and
      reused at inference (including for brand-new field descriptions).
    - The word-embedding + pooling + projection sublayers are the *shared,
      trainable* text encoder. They are what generalizes to new fields.
    """

    def __init__(self, field_names, field_descriptions, vocab, max_desc_len,
                 text_embed_dim, output_dim, **kwargs):
        super().__init__(**kwargs)
        self.field_names = list(field_names)
        self.field_descriptions = list(field_descriptions)
        self.vocab = vocab
        self.max_desc_len = max_desc_len
        self.text_embed_dim = text_embed_dim
        self.output_dim = output_dim

        encoded = [encode_description(d, vocab, max_desc_len) for d in field_descriptions]
        self.description_token_ids = tf.constant(encoded, dtype=tf.int32)  # (num_fields, max_desc_len)

        self.word_embedding = layers.Embedding(len(vocab), text_embed_dim, mask_zero=True,
                                                 name="field_description_word_embedding")
        self.text_projection = layers.Dense(output_dim, name="field_description_projection")
        self.session_projection = layers.Dense(output_dim, name="session_vector_projection")

    def build(self, input_shape):
        self.word_embedding.build((None, self.max_desc_len))
        self.text_projection.build((None, self.text_embed_dim))
        self.session_projection.build(input_shape)
        super().build(input_shape)

    def encode_descriptions(self, token_ids):
        """token_ids: (n, max_desc_len) -> (n, output_dim). Used both for the
        known fields (internally) and for scoring brand-new descriptions at
        inference (called directly, outside the Keras graph)."""
        word_vectors = self.word_embedding(token_ids)                 # (n, max_desc_len, text_embed_dim)
        mask = tf.cast(token_ids != 0, tf.float32)[:, :, tf.newaxis]  # ignore padding in the average
        pooled = tf.reduce_sum(word_vectors * mask, axis=1) / tf.maximum(tf.reduce_sum(mask, axis=1), 1.0)
        return self.text_projection(pooled)                           # (n, output_dim)

    def call(self, session_vector):
        field_vectors = self.encode_descriptions(self.description_token_ids)  # (num_fields, output_dim)
        session_vector = self.session_projection(session_vector)              # (batch, output_dim)
        logits = tf.matmul(session_vector, field_vectors, transpose_b=True)   # (batch, num_fields)
        return logits

    def score_new_description(self, session_vector_batch, description):
        """Score ONE new, never-trained-on field description against a batch
        of already-computed session vectors. This is the new capability:
        no retraining, just a forward pass through the same shared encoder."""
        token_ids = tf.constant([encode_description(description, self.vocab, self.max_desc_len)],
                                 dtype=tf.int32)
        new_field_vector = self.encode_descriptions(token_ids)                # (1, output_dim)
        session_vector = self.session_projection(session_vector_batch)        # (batch, output_dim)
        return tf.matmul(session_vector, new_field_vector, transpose_b=True)[:, 0]  # (batch,)

    def get_config(self):
        config = super().get_config()
        config.update({
            "field_names": self.field_names, "field_descriptions": self.field_descriptions,
            "vocab": self.vocab, "max_desc_len": self.max_desc_len,
            "text_embed_dim": self.text_embed_dim, "output_dim": self.output_dim,
        })
        return config
