package com.example.salesonboarding

import android.content.res.AssetManager
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    override fun predict(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String
    ): FieldPrediction? {
        require(fieldHistory.all(fieldToId::containsKey)) { "Prediction history contains an unknown field" }
        require(industryToId.containsKey(industry)) { "Unknown industry: $industry" }
        require(contextToId.containsKey(context)) { "Unknown context: $context" }

        val recentIds = fieldHistory.takeLast(maxLength).map { fieldToId.getValue(it) }
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
        val bestIndex = output[0].indices.maxByOrNull { output[0][it] } ?: return null
        return FieldPrediction(
            fieldId = fieldToId.entries.first { it.value == bestIndex + 1 }.key,
            confidence = output[0][bestIndex]
        )
    }

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
    }
}
