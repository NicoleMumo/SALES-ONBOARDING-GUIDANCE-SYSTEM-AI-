import tensorflow as tf
from tensorflow.keras import layers


class PositionalEmbedding(layers.Layer):
    """Learned positional embedding, added to token embeddings."""

    def __init__(self, max_sequence_length, embedding_dim, **kwargs):
        super().__init__(**kwargs)
        self.max_sequence_length = max_sequence_length
        self.embedding_dim = embedding_dim
        self.supports_masking = True  # pass the incoming mask straight through

    def build(self, input_shape):
        self.position_embedding = self.add_weight(
            name="position_embedding",
            shape=(self.max_sequence_length, self.embedding_dim),
            initializer="uniform",
            trainable=True,
        )
        super().build(input_shape)

    def call(self, inputs):
        sequence_length = tf.shape(inputs)[1]
        return inputs + self.position_embedding[:sequence_length]

    def compute_mask(self, inputs, mask=None):
        return mask

    def get_config(self):
        config = super().get_config()
        config.update({
            "max_sequence_length": self.max_sequence_length,
            "embedding_dim": self.embedding_dim,
        })
        return config


class ExpandAttentionMask(layers.Layer):
    """(batch, seq_len) boolean padding mask -> (batch, 1, seq_len) for MultiHeadAttention."""

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.supports_masking = False  # this layer *produces* the mask, it doesn't consume one

    def build(self, input_shape):
        super().build(input_shape)

    def call(self, padding_mask):
        return tf.cast(padding_mask[:, tf.newaxis, :], tf.bool)

    def compute_mask(self, inputs, mask=None):
        # This layer's output is an attention-mask tensor, not a sequence with a
        # keras-managed mask. Stop propagation explicitly instead of staying silent.
        return None


class TransformerBlock(layers.Layer):
    def __init__(self, embedding_dim, num_heads, feed_forward_dim, dropout_rate=0.1, **kwargs):
        super().__init__(**kwargs)
        self.embedding_dim = embedding_dim
        self.num_heads = num_heads
        self.feed_forward_dim = feed_forward_dim
        self.dropout_rate = dropout_rate
        self.supports_masking = True

        self.self_attention = layers.MultiHeadAttention(
            num_heads=num_heads,
            key_dim=embedding_dim // num_heads,
            dropout=dropout_rate,
            name="self_attention",
        )
        self.feed_forward_network = tf.keras.Sequential(
            [
                layers.Dense(feed_forward_dim, activation="relu"),
                layers.Dense(embedding_dim),
                layers.Dropout(dropout_rate),
            ],
            name="feed_forward",
        )
        self.layer_norm_1 = layers.LayerNormalization(epsilon=1e-6)
        self.layer_norm_2 = layers.LayerNormalization(epsilon=1e-6)
        self.dropout_1 = layers.Dropout(dropout_rate)
        self.dropout_2 = layers.Dropout(dropout_rate)

    def build(self, input_shape):
        # Building sublayers explicitly here (rather than relying only on their
        # own lazy build-on-first-call) is what Keras 3 wants from a composite
        # layer that creates weight-owning sublayers in __init__.
        self.self_attention.build(input_shape, input_shape)
        self.feed_forward_network.build(input_shape)
        self.layer_norm_1.build(input_shape)
        self.layer_norm_2.build(input_shape)
        self.dropout_1.build(input_shape)
        self.dropout_2.build(input_shape)
        super().build(input_shape)

    def call(self, inputs, attention_mask=None, training=None):
        attention_output = self.self_attention(
            query=inputs,
            key=inputs,
            value=inputs,
            attention_mask=attention_mask,
            training=training,
        )
        attention_output = self.dropout_1(attention_output, training=training)
        first_output = self.layer_norm_1(inputs + attention_output)

        feed_forward_output = self.feed_forward_network(first_output, training=training)
        feed_forward_output = self.dropout_2(feed_forward_output, training=training)

        return self.layer_norm_2(first_output + feed_forward_output)

    def compute_mask(self, inputs, mask=None):
        return mask

    def get_config(self):
        config = super().get_config()
        config.update({
            "embedding_dim": self.embedding_dim,
            "num_heads": self.num_heads,
            "feed_forward_dim": self.feed_forward_dim,
            "dropout_rate": self.dropout_rate,
        })
        return config


class GatherLastToken(layers.Layer):
    """
    Pools a (batch, seq_len, dim) sequence down to (batch, dim) by taking the
    LAST position.

    This is only correct because the sequences are LEFT-padded, so the final
    real token is always at index -1, for every row, regardless of how many
    real tokens precede it. It does NOT take an attention mask as input and
    does NOT compute sum(mask) - 1 -- that formula is the right-padding
    formula and silently points at a padding slot on left-padded data (see
    the validation assertion at data-load time, which checks mask[:, -1] is
    always True before this layer is ever used).
    """

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        # The output is a pooled, fixed-size vector -- there is no longer a
        # time axis, so there is nothing meaningful to mask downstream.
        self.supports_masking = False

    def build(self, input_shape):
        super().build(input_shape)

    def call(self, inputs):
        return inputs[:, -1, :]

    def compute_mask(self, inputs, mask=None):
        return None

    def compute_output_shape(self, input_shape):
        return (input_shape[0], input_shape[2])


def build_backbone(input_ids, padding_mask, vocab_size, embedding_dim, num_heads,
                    feed_forward_dim, num_blocks, dropout_rate, max_seq_len, prefix=""):
    """Shared token+position+transformer-blocks backbone used by every model
    variant below. Returns (sequence_output, token_embeddings).

    Masking design: the Embedding layer does NOT use mask_zero. Padding is
    handled by a single explicit source of truth -- the `padding_mask` input
    -- expanded once and threaded through every TransformerBlock's
    attention_mask argument. Keeping mask_zero=True on the Embedding *and*
    passing an explicit mask is redundant: it creates two separate masking
    mechanisms that the custom layers below (GatherLastToken in particular)
    were never designed to reconcile, which is exactly what produced the
    "custom layers do not implement mask propagation" warnings. Turning off
    the automatic mask keeps one unambiguous mechanism end to end.
    """
    token_embeddings = layers.Embedding(
        input_dim=vocab_size,
        output_dim=embedding_dim,
        mask_zero=False,
        name=f"{prefix}token_embedding",
    )(input_ids)

    x = PositionalEmbedding(
        max_seq_len, embedding_dim, name=f"{prefix}positional_embedding",
    )(token_embeddings)

    expanded_mask = ExpandAttentionMask(
        name=f"{prefix}expanded_attention_mask",
    )(padding_mask)

    for block_number in range(num_blocks):
        x = TransformerBlock(
            embedding_dim=embedding_dim,
            num_heads=num_heads,
            feed_forward_dim=feed_forward_dim,
            dropout_rate=dropout_rate,
            name=f"{prefix}transformer_block_{block_number + 1}",
        )(x, attention_mask=expanded_mask)

    return x, token_embeddings
