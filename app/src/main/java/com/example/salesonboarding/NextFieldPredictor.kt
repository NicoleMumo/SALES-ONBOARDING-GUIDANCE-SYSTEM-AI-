package com.example.salesonboarding

import android.content.res.AssetManager
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.KnownFields
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

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

class TfliteNextFieldPredictor(
    assets: AssetManager,
    modelAsset: String = MODEL_ASSET,
    metadataAsset: String = METADATA_ASSET
) : NextFieldPredictor, Closeable {
    private val interpreter = Interpreter(loadAsset(assets, modelAsset))
    private val metadata = JSONObject(assets.open(metadataAsset).bufferedReader().use { it.readText() })
    private val fieldToId = metadata.getJSONObject("field_to_id").toMap()
    private val industryToId = metadata.getJSONObject("industry_to_id").toMap()
    private val contextToId = metadata.getJSONObject("context_to_id").toMap()
    private val validationToId = metadata.getJSONObject("validation_to_id").toMap()
    private val maxLength = metadata.getInt("max_len")
    private val descriptionVocab = metadata.optJSONObject("description_vocab")?.toMap().orEmpty()
    private val fieldDescriptions = metadata.optJSONObject("field_descriptions")?.toStringMap().orEmpty()
    private val maxDescriptionLength = metadata.optInt("max_desc_len", 0)
    private val maxCandidates = metadata.optInt("max_candidates", 0)
    private val hasDynamicCandidateScoring =
        interpreter.inputTensorCount == 7 && descriptionVocab.isNotEmpty() &&
            maxDescriptionLength > 0 && maxCandidates > 0

    override fun predict(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String
    ): FieldPrediction? {
        val probabilities = predictProbabilities(fieldHistory, industry, context, lastValidation)
        val best = probabilities.maxByOrNull { it.value } ?: return null
        return FieldPrediction(best.key, best.value)
    }

    override fun predictAmongCandidates(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String,
        candidates: List<FormField>,
        fieldCatalogue: List<FormField>
    ): FieldPrediction? {
        if (candidates.isEmpty()) return null
        require(industryToId.containsKey(industry)) { "Unknown industry: $industry" }
        require(contextToId.containsKey(context)) { "Unknown context: $context" }

        if (hasDynamicCandidateScoring && candidates.size <= maxCandidates) {
            val dynamicScores = scoreDynamicCandidates(
                fieldHistory, industry, context, lastValidation, candidates, fieldCatalogue
            )
            if (dynamicScores != null) return dynamicScores
        }

        val probabilities = predictProbabilities(fieldHistory, industry, context, lastValidation)
        val knownFields = KnownFields.all.associateBy(FormField::fieldId)
        val scoredCandidates = candidates.map { candidate ->
            val exactScore = probabilities[candidate.fieldId]
            val score = exactScore ?: estimateCustomFieldScore(candidate, probabilities, knownFields)
            candidate to score
        }
        val best = scoredCandidates.maxByOrNull { it.second } ?: return null
        return if (best.second > 0f) FieldPrediction(best.first.fieldId, best.second) else null
    }

    private fun predictProbabilities(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String
    ): Map<String, Float> {
        require(industryToId.containsKey(industry)) { "Unknown industry: $industry" }
        require(contextToId.containsKey(context)) { "Unknown context: $context" }

        val recentIds = fieldHistory.mapNotNull(fieldToId::get).takeLast(maxLength)
        val inputIds = IntArray(maxLength)
        val attentionMask = IntArray(maxLength)
        val start = maxLength - recentIds.size
        recentIds.forEachIndexed { index, value ->
            inputIds[start + index] = value
            attentionMask[start + index] = 1
        }

        val output = Array(1) { FloatArray(fieldToId.size) }
        interpreter.runForMultipleInputsOutputs(
            arrayOf(
                arrayOf(inputIds),
                arrayOf(attentionMask),
                arrayOf(intArrayOf(industryToId.getValue(industry))),
                arrayOf(intArrayOf(contextToId.getValue(context))),
                arrayOf(intArrayOf(validationToId[lastValidation] ?: 0))
            ),
            mapOf(0 to output)
        )
        return fieldToId.entries.associate { (fieldId, id) ->
            fieldId to output[0][id - 1]
        }
    }

    private fun scoreDynamicCandidates(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String,
        candidates: List<FormField>,
        fieldCatalogue: List<FormField>
    ): FieldPrediction? {
        val modelHistory = mapHistoryToKnownFields(fieldHistory, fieldCatalogue)
        val recentIds = modelHistory.takeLast(maxLength).map { fieldToId.getValue(it) }
        val inputIds = IntArray(maxLength)
        val attentionMask = IntArray(maxLength)
        val start = maxLength - recentIds.size
        recentIds.forEachIndexed { index, value ->
            inputIds[start + index] = value
            attentionMask[start + index] = 1
        }

        val candidateIds = Array(1) { Array(maxCandidates) { IntArray(maxDescriptionLength) } }
        val candidateMask = Array(1) { IntArray(maxCandidates) }
        candidates.forEachIndexed { index, field ->
            val description = fieldDescriptions[field.fieldId]
                ?: "${field.fieldName}. ${field.description}"
            candidateIds[0][index] = encodeDescription(description)
            candidateMask[0][index] = 1
        }
        val output = Array(1) { FloatArray(maxCandidates) }
        interpreter.runForMultipleInputsOutputs(
            arrayOf(
                arrayOf(inputIds),
                arrayOf(attentionMask),
                arrayOf(intArrayOf(industryToId.getValue(industry))),
                arrayOf(intArrayOf(contextToId.getValue(context))),
                arrayOf(intArrayOf(validationToId[lastValidation] ?: 0)),
                candidateIds,
                candidateMask
            ),
            mapOf(0 to output)
        )
        val bestIndex = candidates.indices.maxByOrNull { output[0][it] } ?: return null
        return FieldPrediction(candidates[bestIndex].fieldId, output[0][bestIndex])
    }

    private fun encodeDescription(description: String): IntArray {
        val tokenIds = description.lowercase(Locale.ROOT)
            .replace('-', ' ')
            .replace(',', ' ')
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .map { descriptionVocab[it] ?: descriptionVocab["<unk>"] ?: 1 }
            .take(maxDescriptionLength)
        return IntArray(maxDescriptionLength).also { result ->
            tokenIds.forEachIndexed { index, tokenId -> result[index] = tokenId }
        }
    }

    private fun mapHistoryToKnownFields(
        fieldHistory: List<String>,
        fieldCatalogue: List<FormField>
    ): List<String> {
        val fieldsById = fieldCatalogue.associateBy(FormField::fieldId)
        val knownFields = KnownFields.all.filter { it.fieldId in fieldToId }
        return fieldHistory.mapNotNull { fieldId ->
            if (fieldId in fieldToId) fieldId
            else fieldsById[fieldId]?.let { unknown ->
                val closest = knownFields.maxByOrNull { lexicalSimilarity(unknown, it) }
                closest?.takeIf { lexicalSimilarity(unknown, it) >= MIN_HISTORY_SIMILARITY }?.fieldId
            }
        }
    }

    private fun estimateCustomFieldScore(
        candidate: FormField,
        probabilities: Map<String, Float>,
        knownFields: Map<String, FormField>
    ): Float = probabilities.entries.sumOf { (fieldId, probability) ->
        val knownField = knownFields[fieldId] ?: return@sumOf 0.0
        val similarity = lexicalSimilarity(candidate, knownField)
        (probability * similarity * similarity).toDouble()
    }.toFloat()

    private fun lexicalSimilarity(first: FormField, second: FormField): Float {
        val firstTokens = normalizedTokens(first)
        val secondTokens = normalizedTokens(second)
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) return 0f
        val intersection = firstTokens.intersect(secondTokens).size.toFloat()
        return intersection / kotlin.math.sqrt(firstTokens.size * secondTokens.size.toFloat())
    }

    private fun normalizedTokens(field: FormField): Set<String> =
        Regex("[\\p{L}\\p{N}]+")
            .findAll("${field.fieldId} ${field.fieldName} ${field.description}".lowercase(Locale.ROOT))
            .map { it.value }
            .filter { it !in STOP_WORDS && it.length > 1 }
            .toSet()

    private fun JSONObject.toStringMap(): Map<String, String> =
        keys().asSequence().associateWith { getString(it) }

    override fun close() {
        interpreter.close()
    }

    private fun JSONObject.toMap(): Map<String, Int> =
        keys().asSequence().associateWith { getInt(it) }

    private fun loadAsset(assets: AssetManager, name: String): ByteBuffer =
        assets.open(name).use { input ->
            ByteBuffer.allocateDirect(input.available()).order(ByteOrder.nativeOrder()).also { buffer ->
                buffer.put(input.readBytes())
                buffer.rewind()
            }
        }

    companion object {
        const val MODEL_ASSET = "onboarding_model.tflite"
        const val METADATA_ASSET = "preprocessing_meta.json"
        private const val MIN_HISTORY_SIMILARITY = 0.15f
        private val STOP_WORDS = setOf(
            "a", "an", "and", "as", "at", "by", "for", "from", "in", "into", "is", "it",
            "of", "on", "or", "the", "to", "with", "your", "customer", "customers", "enter",
            "record", "choose", "select", "provided", "information", "field", "details"
        )
    }
}
