# Code removed from the Android app

Pieces of Kotlin taken out of files that are still in use. Each was unused or
only served something the app no longer ships. The full files as they were are
in commit `9c3e1be` (`git show 9c3e1be:<path>`).

## 1. Next-field predictor: the fixed-output model path

File: `app/src/main/java/com/example/salesonboarding/NextFieldPredictor.kt`

Why removed: it served an older 5-input export that gave one score per known
field. The app ships the 7-input export that scores candidate descriptions, so
this path never ran. A 5-input model is now refused at start-up, and the app
then uses the playbook order, as it does for any model it cannot load.

```kotlin
    private val hasDynamicCandidateScoring =
        interpreter.inputTensorCount == DYNAMIC_INPUT_COUNT && descriptionVocab.isNotEmpty() &&
                maxDescriptionLength > 0 && maxCandidates > 0

        return if (hasDynamicCandidateScoring) {
            // Too many candidates for one model call: skip the suggestion rather than guess.
            if (candidates.size > maxCandidates) null
            else scoreDynamicCandidates(fieldHistory, industry, context, lastValidation, candidates, fieldCatalogue)
        } else {
            scoreWithFixedOutput(fieldHistory, industry, context, lastValidation, candidates)
        }

    /** Older export: one score per known field. Custom fields are estimated from similar known fields. */
    private fun scoreWithFixedOutput(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String,
        candidates: List<FormField>
    ): FieldPrediction? {
        val probabilities = predictProbabilities(fieldHistory, industry, context, lastValidation) ?: return null
        val best = candidates
            .map { it to (probabilities[it.fieldId] ?: estimateCustomFieldScore(it, probabilities)) }
            .maxByOrNull { it.second } ?: return null
        return if (best.second > 0f) FieldPrediction(best.first.fieldId, best.second) else null
    }

    private fun predictProbabilities(
        fieldHistory: List<String>,
        industry: String,
        context: String,
        lastValidation: String
    ): Map<String, Float>? {
        val (inputIds, attentionMask) = historyTensors(fieldHistory.mapNotNull(fieldToId::get))
        val output = Array(1) { FloatArray(fieldToId.size) }
        val ran = run(
            mapOf(
                "input_ids" to arrayOf(inputIds),
                "attention_mask" to arrayOf(attentionMask),
                "industry_id" to arrayOf(intArrayOf(industryToId.getValue(industry))),
                "context_id" to arrayOf(intArrayOf(contextToId.getValue(context))),
                "validation_id" to arrayOf(intArrayOf(validationToId[lastValidation] ?: 0))
            ),
            output
        )
        if (!ran || output[0].any { !it.isFinite() }) return null
        return fieldToId.entries.associate { (fieldId, id) -> fieldId to output[0][id - 1] }
    }

    private fun estimateCustomFieldScore(candidate: FormField, probabilities: Map<String, Float>): Float {
        val tokens = FieldTextSimilarity.tokens(candidate)
        var score = 0f
        for ((fieldId, probability) in probabilities) {
            val known = knownFieldTokens[fieldId] ?: continue
            val similarity = FieldTextSimilarity.cosine(tokens, known)
            score += probability * similarity * similarity
        }
        return score
    }

    // start-up check
        check(interpreter.inputTensorCount == LEGACY_INPUT_COUNT || hasDynamicCandidateScoring) {
            "Model has ${interpreter.inputTensorCount} inputs but the metadata does not describe candidate scoring"
        }
        val expectedOutputSize = if (hasDynamicCandidateScoring) maxCandidates else fieldToId.size

        private const val LEGACY_INPUT_COUNT = 5
        private const val DYNAMIC_INPUT_COUNT = 7
```

## 2. Selfie and face-match questions

Files: `validation/KnownFields.kt`, `OnboardingScreen.kt`

Why removed: the app's forms dropped these steps, and the model was retrained
without them (32 fields). They were still in the catalogue, so the admin rules
list showed two "required" switches that changed nothing. Saved sessions keep
their own copy of the form, so old sessions are not affected.

```kotlin
        mustBeTrue(
            "face_match_verification",
            "Record the result of your organization's face-match check."
        ),
        mustBeTrue(
            "selfie_liveness_check",
            "Record the result of your organization's live-selfie check."
        ),

    // OnboardingScreen.kt, fieldActionLabel
    "selfie_liveness_check" -> "Liveness verification check passed"
    "face_match_verification" -> "Face match identity check passed"
```

## 3. Session-store compatibility overloads

File: `FirebaseFieldEntryStore.kt`

Why removed: `startSession` and `completeSession` each had two forms, one
forwarding to the other. The app only calls the form with the agent's email
and the form with the list of field ids.

```kotlin
    // OnboardingSessionStore
    fun startSession(
        sessionId: String,
        agentId: String,
        industry: String,
        context: String,
        playbook: OnboardingPlaybook
    )
    fun startSession(
        sessionId: String,
        agentId: String,
        industry: String,
        context: String,
        playbook: OnboardingPlaybook,
        agentEmail: String
    ) {
        startSession(sessionId, agentId, industry, context, playbook)
    }
    fun completeSession(
        sessionId: String,
        entries: List<FieldEntry>,
        fieldIds: List<String>,
        onResult: (Result<Unit>) -> Unit
    ) = completeSession(sessionId, entries, onResult)

    // FirebaseFieldEntryStore
    override fun startSession(
        sessionId: String,
        agentId: String,
        industry: String,
        context: String,
        playbook: OnboardingPlaybook
    ) = startSession(sessionId, agentId, industry, context, playbook, "")

    override fun completeSession(
        sessionId: String,
        entries: List<FieldEntry>,
        onResult: (Result<Unit>) -> Unit
    ) = completeSession(sessionId, entries, entries.map(FieldEntry::fieldId), onResult)
```

## 4. The deprecated `validationRule` alias

File: `validation/ValidationModels.kt`

Why removed: an old name for `validationRules`, kept for callers that were
never updated. All five callers now use `validationRules`.

```kotlin
    /** Backward-compatibility alias for older callers still reading the legacy property name. */
    @Deprecated(
        message = "Use validationRules instead.",
        replaceWith = ReplaceWith("validationRules")
    )
    val validationRule: List<ValidationRule>
        get() = validationRules
```

## 5. `TfliteNextFieldPredictor.createOrNull` and `GreetingPreview`

Files: `NextFieldPredictor.kt`, `MainActivity.kt` (removed in the commit after `d606683`)

Why removed: nothing called `createOrNull`; `MainActivity` builds the predictor
directly because it needs to know whether loading failed. `GreetingPreview` was
an Android Studio preview of the onboarding screen, which already has its own
preview (`OnboardingScreenPreview`).

```kotlin
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

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SalesOnboardingTheme {
        val scope = rememberCoroutineScope()
        val store = remember { InMemoryFieldEntryStore() }
        val controller = rememberOnboardingController("preview-session", scope, store)
        OnboardingScreen(controller)
    }
}
```

## 6. `KnownFields.fieldIds`

File: `validation/KnownFields.kt`

Why removed: nothing read it. Its comment (the catalogue must match the
model's fields) moved to `KnownFields.all`, and `ValidationRulesTest` now
checks that rule.

```kotlin
    val fieldIds: Set<String> get() = byId.keys
```
