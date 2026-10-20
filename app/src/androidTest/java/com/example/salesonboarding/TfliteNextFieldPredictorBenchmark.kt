package com.example.salesonboarding

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
class TfliteNextFieldPredictorBenchmark {
    private lateinit var predictor: TfliteNextFieldPredictor

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        predictor = TfliteNextFieldPredictor(context.assets)
    }

    @After
    fun tearDown() {
        predictor.close()
    }

    @Test
    fun predictsNextField() {
        var lastPrediction: FieldPrediction? = null
        repeat(WARMUP_ITERATIONS) {
            lastPrediction = predictor.predict(
                fieldHistory = listOf("full_name", "email_address"),
                industry = "telecom",
                context = "individual",
                lastValidation = "passed"
            )
        }

        val durationsNanos = LongArray(MEASURED_ITERATIONS) {
            val startNanos = SystemClock.elapsedRealtimeNanos()
            lastPrediction = predictor.predict(
                fieldHistory = listOf("full_name", "email_address"),
                industry = "telecom",
                context = "individual",
                lastValidation = "passed"
            )
            SystemClock.elapsedRealtimeNanos() - startNanos
        }.sortedArray()

        assertNotNull(lastPrediction)
        val medianNanos = percentile(durationsNanos, 50)
        val p95Nanos = percentile(durationsNanos, 95)
        Log.i(
            TAG,
            "device=${android.os.Build.MODEL}, androidApi=${android.os.Build.VERSION.SDK_INT}, " +
                "iterations=$MEASURED_ITERATIONS, " +
                "medianMs=${medianNanos.toMillisString()}, " +
                "p95Ms=${p95Nanos.toMillisString()}"
        )
    }

    private fun Double.toMillisString(): String =
        String.format(Locale.US, "%.3f", this / NANOS_PER_MILLISECOND)

    private fun percentile(sortedValues: LongArray, percentile: Int): Double {
        val rank = percentile / 100.0 * (sortedValues.size - 1)
        val lowerIndex = rank.toInt()
        val upperIndex = ceil(rank).toInt()
        val weight = rank - lowerIndex
        return sortedValues[lowerIndex] * (1 - weight) + sortedValues[upperIndex] * weight
    }

    private companion object {
        const val TAG = "NextFieldInferenceBenchmark"
        const val WARMUP_ITERATIONS = 50
        const val MEASURED_ITERATIONS = 500
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
