package com.example.salesonboarding

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TfliteNextFieldPredictorBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

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
        benchmarkRule.measureRepeated {
            predictor.predict(
                fieldHistory = listOf("full_name", "email_address"),
                industry = "telecom",
                context = "individual",
                lastValidation = "passed"
            )
        }
    }
}
