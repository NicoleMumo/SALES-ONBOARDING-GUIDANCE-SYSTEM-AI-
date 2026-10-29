package com.example.salesonboarding

import android.content.res.AssetManager
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

/**
 * A suggested next field. [confidence] is the model's score among the fields it was asked
 * to compare, so it always favours the best of a short list. It is not a calibrated probability.
 */
data class FieldPrediction(
    val fieldId: String,
    val confidence: Float
)

fun interface NextFieldPredictor {
    fun predict(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String
    ): FieldPrediction?

    /**
     * Suggests one of [candidates], or null when there is no useful suggestion (the caller
     * should then guide the agent to the first incomplete required field).
     *
     * This default only keeps the overall best field if it is allowed. Real predictors
     * override it to score the candidates directly.
     */
    fun predictAmongCandidates(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String,
        candidates: List<FormField>,
        fieldCatalogue: List<FormField> = candidates
    ): FieldPrediction? {
        if (candidates.isEmpty()) return null
        val allowedIds = candidates.mapTo(mutableSetOf(), FormField::fieldId)
        return predict(fieldHistory, industry, context, lastValidation)
            ?.takeIf { it.fieldId in allowedIds }
    }
}

/**
 * Runs the exported next-field model. The model file and its metadata file must come from
 * the same export; this class refuses to start if they do not match.
 *
 * The model sees field history, industry, customer type, validation status and the candidate
 * fields' descriptions. It never receives the customer's answer values.
 * History is left-padded by [historyTensors]. Calls are serialised because a TensorFlow Lite
 * interpreter is not thread-safe. After [close], predictions return null.
 */
class TfliteNextFieldPredictor(
    assets: AssetManager,
    modelAsset: String = MODEL_ASSET,
    metadataAsset: String = METADATA_ASSET
) : NextFieldPredictor, Closeable {

    // region Metadata from the same export as the model

    private val metadata = JSONObject(assets.open(metadataAsset).bufferedReader().use { it.readText() })
    private val fieldToId = metadata.getJSONObject("field_to_id").toIntMap()
    private val industryToId = metadata.getJSONObject("industry_to_id").toIntMap()
    private val contextToId = metadata.getJSONObject("context_to_id").toIntMap()
    private val validationToId = metadata.getJSONObject("validation_to_id").toIntMap()
    private val maxLength = metadata.getInt("max_len")
    private val descriptionVocab = metadata.optJSONObject("description_vocab")?.toIntMap().orEmpty()
    private val fieldDescriptions = metadata.optJSONObject("field_descriptions")?.toStringMap().orEmpty()
    private val maxDescriptionLength = metadata.optInt("max_desc_len", 0)
    private val maxCandidates = metadata.optInt("max_candidates", 0)

    // endregion

    // region Model

    private val interpreter = Interpreter(loadAsset(assets, modelAsset))

    /** Where each named input sits in the model, found by tensor name. */
    private val inputPositions: Map<String, Int> = findInputPositions()

    /** Catalogue fields the model was trained on. */
    private val modelFields: List<FormField> = KnownFields.all.filter { it.fieldId in fieldToId }
    private val knownFieldTokens: Map<String, Set<String>> =
        modelFields.associate { it.fieldId to FieldTextSimilarity.tokens(it) }

    private val lock = Any()
    @Volatile
    private var closed = false

    init {
        try {
            checkModelMatchesMetadata()
        } catch (e: RuntimeException) {
            interpreter.close()
            throw e
        }
    }

    // endregion

    // region Predictions

    override fun predict(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String
    ): FieldPrediction? =
        predictAmongCandidates(fieldHistory, industry, context, lastValidation, modelFields, modelFields)

    override fun predictAmongCandidates(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String,
        candidates: List<FormField>,
        fieldCatalogue: List<FormField>
    ): FieldPrediction? {
        if (candidates.isEmpty()) return null
        // An industry or customer type the model was not trained on cannot be scored.
        // Return null so the caller falls back to the rule-based order.
        if (industry !in industryToId || context !in contextToId) return null
        // Too many candidates for one model call: skip the suggestion rather than guess.
        if (candidates.size > maxCandidates) return null

        return scoreCandidates(fieldHistory, industry, context, lastValidation, candidates, fieldCatalogue)
    }

    /** Scores the given candidates by comparing their descriptions. */
    private fun scoreCandidates(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String,
        candidates: List<FormField>,
        fieldCatalogue: List<FormField>
    ): FieldPrediction? {
        val modelIds = mapHistoryToKnownFields(fieldHistory, fieldCatalogue).map(fieldToId::getValue)
        val (inputIds, attentionMask) = historyTensors(modelIds)

        val candidateIds = Array(1) { Array(maxCandidates) { IntArray(maxDescriptionLength) } }
        val candidateMask = Array(1) { IntArray(maxCandidates) }
        candidates.forEachIndexed { index, field ->
            candidateIds[0][index] = encodeDescription(descriptionFor(field))
            candidateMask[0][index] = 1
        }

        val output = Array(1) { FloatArray(maxCandidates) }
        val ran = run(
            mapOf(
                "input_ids" to arrayOf(inputIds),
                "attention_mask" to arrayOf(attentionMask),
                "industry_id" to arrayOf(intArrayOf(industryToId.getValue(industry))),
                "context_id" to arrayOf(intArrayOf(contextToId.getValue(context))),
                "validation_id" to arrayOf(intArrayOf(validationToId[lastValidation] ?: 0)),
                "candidate_description_ids" to candidateIds,
                "candidate_mask" to candidateMask
            ),
            output
        )
        val scores = output[0]
        // A closed interpreter or a broken (NaN) output means no usable suggestion.
        if (!ran || candidates.indices.any { !scores[it].isFinite() }) return null

        val best = candidates.indices.maxByOrNull { scores[it] } ?: return null
        return FieldPrediction(candidates[best].fieldId, scores[best])
    }

    /** Runs the interpreter once, with inputs placed by name. Returns false if already closed. */
    private fun run(inputs: Map<String, Any>, output: Array<FloatArray>): Boolean = synchronized(lock) {
        if (closed) return@synchronized false
        val ordered = arrayOfNulls<Any>(inputPositions.size)
        inputs.forEach { (name, value) -> ordered[inputPositions.getValue(name)] = value }
        interpreter.runForMultipleInputsOutputs(ordered.requireNoNulls(), mapOf(0 to output))
        true
    }

    // endregion

    // region Input preparation

    /** The most recent [maxLength] model ids, left-padded with zeros, plus a 1/0 mask of real entries. */
    private fun historyTensors(modelIds: List<Int>): Pair<IntArray, IntArray> {
        val recent = modelIds.takeLast(maxLength)
        val inputIds = IntArray(maxLength)
        val attentionMask = IntArray(maxLength)
        val start = maxLength - recent.size
        recent.forEachIndexed { index, id ->
            inputIds[start + index] = id
            attentionMask[start + index] = 1
        }
        return inputIds to attentionMask
    }

    /** Built-in fields use the descriptions the model was trained on; custom fields use their own text. */
    private fun descriptionFor(field: FormField): String =
        fieldDescriptions[field.fieldId] ?: "${field.fieldName}. ${field.description}"

    /** Must tokenise exactly like the training notebook's `encode_description`. */
    private fun encodeDescription(description: String): IntArray {
        val unknown = descriptionVocab["<unk>"] ?: 1
        val result = IntArray(maxDescriptionLength)
        description.take(MAX_DESCRIPTION_CHARS)
            .lowercase(Locale.ROOT)
            .split(TOKEN_SEPARATORS)
            .filter(String::isNotEmpty)
            .take(maxDescriptionLength)
            .forEachIndexed { index, word -> result[index] = descriptionVocab[word] ?: unknown }
        return result
    }

    /** Keeps trained fields, and maps each custom field to its closest trained field if similar enough. */
    private fun mapHistoryToKnownFields(
        fieldHistory: List<String>,
        fieldCatalogue: List<FormField>
    ): List<String> {
        val catalogueById = fieldCatalogue.associateBy(FormField::fieldId)
        return fieldHistory.mapNotNull { fieldId ->
            if (fieldId in fieldToId) return@mapNotNull fieldId
            val custom = catalogueById[fieldId] ?: return@mapNotNull null
            val tokens = FieldTextSimilarity.tokens(custom)
            val (closestId, similarity) = modelFields
                .map { it.fieldId to FieldTextSimilarity.cosine(tokens, knownFieldTokens.getValue(it.fieldId)) }
                .maxByOrNull { it.second } ?: return@mapNotNull null
            closestId.takeIf { similarity >= MIN_HISTORY_SIMILARITY }
        }
    }

    // endregion

    // region Start-up checks

    private fun findInputPositions(): Map<String, Int> {
        val expected = INPUT_NAMES.take(interpreter.inputTensorCount)
        val tensorNames = (0 until interpreter.inputTensorCount).map { interpreter.getInputTensor(it).name() }
        val byName = expected.associateWith { name -> tensorNames.indexOfFirst { it.contains(name) } }
        val usable = byName.values.all { it >= 0 } && byName.values.toSet().size == expected.size
        // If the tensors are not named as expected, assume the order the model was exported in.
        return if (usable) byName else expected.withIndex().associate { (index, name) -> name to index }
    }

    private fun checkModelMatchesMetadata() {
        check(maxLength > 0) { "Metadata max_len must be positive" }
        check(fieldToId.values.sorted() == (1..fieldToId.size).toList()) {
            "Metadata field_to_id must give each field a unique id from 1 to ${fieldToId.size}"
        }
        check(interpreter.inputTensorCount == INPUT_NAMES.size) {
            "Model has ${interpreter.inputTensorCount} inputs; the app needs the ${INPUT_NAMES.size}-input " +
                    "export that scores candidate descriptions"
        }
        check(descriptionVocab.isNotEmpty() && maxDescriptionLength > 0 && maxCandidates > 0) {
            "Metadata does not describe candidate scoring"
        }
        val sequenceLength = interpreter.getInputTensor(inputPositions.getValue("input_ids")).shape().last()
        check(sequenceLength == maxLength) {
            "Model expects sequences of $sequenceLength but the metadata says $maxLength"
        }
        val outputSize = interpreter.getOutputTensor(0).shape().last()
        check(outputSize == maxCandidates) {
            "Model output has $outputSize scores but the metadata expects $maxCandidates. " +
                    "Replace the model and metadata files together."
        }
    }

    // endregion

    override fun close() {
        synchronized(lock) {
            if (!closed) {
                closed = true
                interpreter.close()
            }
        }
    }

    companion object {
        const val MODEL_ASSET = "onboarding_model.tflite"
        const val METADATA_ASSET = "preprocessing_meta.json"

        private const val MAX_DESCRIPTION_CHARS = 500
        private const val MIN_HISTORY_SIMILARITY = 0.15f

        private val INPUT_NAMES = listOf(
            "input_ids", "attention_mask", "industry_id", "context_id", "validation_id",
            "candidate_description_ids", "candidate_mask"
        )

        // Letters, digits, apostrophes and "/" stay inside a word, as in the training vocabulary.
        private val TOKEN_SEPARATORS = Regex("[\\s,._;:!?()\"-]+")

        /**
         * Creates the predictor, or returns null if the model or metadata is missing or the two
         * do not match. A null predictor means no suggestions: use the rule-based order instead.
         * [onError] receives the reason, for logging (it never contains customer data).
         */
        fun createOrNull(
            assets: AssetManager,
            onError: (Exception) -> Unit
        ): TfliteNextFieldPredictor? =
            try {
                TfliteNextFieldPredictor(assets)
            } catch (e: Exception) {
                onError(e)
                null
            }

        // Read the whole stream: available() is not a reliable size for compressed assets.
        private fun loadAsset(assets: AssetManager, name: String): ByteBuffer {
            val bytes = assets.open(name).use { it.readBytes() }
            return ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .put(bytes)
                .also { it.rewind() }
        }
    }
}

private fun JSONObject.toIntMap(): Map<String, Int> =
    keys().asSequence().associateWith { getInt(it) }

private fun JSONObject.toStringMap(): Map<String, String> =
    keys().asSequence().associateWith { getString(it) }

/** Word-overlap similarity between fields, used to place custom fields next to trained ones. */
private object FieldTextSimilarity {
    private const val MAX_FIELD_TEXT_CHARS = 500
    private val WORD = Regex("[\\p{L}\\p{N}]+")

    private val STOP_WORDS = setOf(
        "a", "an", "and", "as", "at", "by", "for", "from", "in", "into", "is", "it",
        "of", "on", "or", "the", "to", "with", "your", "customer", "customers", "enter",
        "record", "choose", "select", "provided", "information", "field", "details"
    )

    fun tokens(field: FormField): Set<String> =
        WORD.findAll(
            "${field.fieldId} ${field.fieldName} ${field.description}"
                .take(MAX_FIELD_TEXT_CHARS)
                .lowercase(Locale.ROOT)
        )
            .map { it.value }
            .filter { it !in STOP_WORDS && it.length > 1 }
            .toSet()

    /** 1 for identical word sets, 0 for no shared words. */
    fun cosine(first: Set<String>, second: Set<String>): Float {
        if (first.isEmpty() || second.isEmpty()) return 0f
        return first.intersect(second).size / sqrt(first.size.toFloat() * second.size)
    }
}