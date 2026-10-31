package com.example.salesonboarding

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Types an answer and leaves the box, as the form does. */
internal fun OnboardingFormController.answer(fieldId: String, value: String) {
    updateField(fieldId, value)
    checkField(fieldId)
}

/** Types an answer and leaves the box, as the form does. */
internal fun LiveValidationSession.answer(fieldId: String, value: String?) {
    updateField(fieldId, value)
    checkField(fieldId)
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFormControllerTest {
    @Test
    fun typingSavesNoCheckAndLeavingTheBoxSavesExactlyOne() = runTest {
        val store = RecordingOnboardingSessionStore()
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("date_of_birth")),
            scope = backgroundScope,
            entryStore = store
        )

        listOf("2", "20", "2000", "2000-0", "2000-01", "2000-01-1", "2000-01-15").forEach {
            controller.updateField("date_of_birth", it)
            runCurrent()
        }
        assertTrue(store.events.isEmpty())

        controller.checkField("date_of_birth")
        runCurrent()

        assertEquals(listOf(SessionEvent("field_validation_passed", "date_of_birth")), store.events)
    }

    @Test
    fun riskInputsAreMeasuredLikeTheTrainingData() = runTest {
        // Training: a step's time is the whole step, and progress is answers passed out of
        // all the form's questions. So each answer is timed from the previous check (or
        // from opening the form), not from its first keystroke.
        var now = 0L
        val controller = OnboardingFormController(
            "session",
            fields = listOf(
                KnownFields.get("full_name"),
                KnownFields.get("email_address"),
                KnownFields.get("profession")
            ),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore(),
            nowMillis = { now }
        )
        runCurrent()

        now = 100_000 // the agent asks, then starts typing 100 s after the form opened
        controller.updateField("full_name", "Jane Wanjiku")
        now = 120_000
        controller.checkField("full_name")
        runCurrent()
        now = 170_000
        controller.updateField("email_address", "jane@example.com")
        now = 180_000
        controller.checkField("email_address")
        runCurrent()

        val events = controller.riskEvents
        assertEquals(listOf(2.0, 1.0), events.map { it.durationMinutes })
        assertEquals(100.0 / 3, events[0].progressPercent, 1e-9)
        assertEquals(200.0 / 3, events[1].progressPercent, 1e-9)
    }

    @Test
    fun continueIsDisabledForInvalidAndEmptyRequiredFields() = runTest {
        val store = InMemoryFieldEntryStore()
        val controller = OnboardingFormController(
            "session",
            fields = listOf(
                KnownFields.get("email_address"),
                KnownFields.get("full_name")
            ),
            scope = backgroundScope,
            entryStore = store
        )

        controller.answer("email_address", "bad")
        runCurrent()

        assertFalse(controller.canContinue())
        assertEquals(
            "Enter a valid email address.",
            ValidationErrorDisplayMapper.forState(controller.stateFlow.value).single().message
        )
    }

    @Test
    fun fixingAllRequiredFieldsEnablesContinueAndSavesEntries() = runTest {
        val store = InMemoryFieldEntryStore()
        val controller = OnboardingFormController(
            "session",
            fields = listOf(
                KnownFields.get("email_address"),
                KnownFields.get("full_name")
            ),
            scope = backgroundScope,
            entryStore = store
        )

        controller.answer("email_address", "agent@example.com")
        controller.answer("full_name", "Jane Wanjiku")
        runCurrent()

        assertTrue(ValidationErrorDisplayMapper.forState(controller.stateFlow.value).isEmpty())
        assertTrue(controller.canContinue())
        assertTrue(controller.submit())
        assertEquals(setOf("email_address", "full_name"), store.savedEntries.map { it.fieldId }.toSet())
    }

    @Test
    fun successfulOnboardingRecordsTheCompleteSessionLifecycle() = runTest {
        val store = RecordingOnboardingSessionStore()
        val fields = listOf(
            KnownFields.get("full_name"),
            KnownFields.get("email_address")
        )
        val controller = OnboardingFormController(
            sessionId = "successful-session",
            fields = fields,
            scope = backgroundScope,
            entryStore = store,
            industry = "telecom",
            context = "individual",
            agentId = "agent@example.com"
        )

        controller.answer("full_name", "Jane Wanjiku")
        controller.answer("email_address", "jane@example.com")
        runCurrent()

        assertTrue(controller.canContinue())
        assertTrue(controller.submit())
        assertEquals(
            SessionStart(
                "successful-session",
                "agent@example.com",
                "telecom",
                "individual",
                "telecom-standard-onboarding",
                controller.playbook.version,
                fields.map { it.fieldId }
            ),
            store.startedSession
        )
        assertEquals(setOf("full_name", "email_address"), store.validatedFields)
        assertEquals(setOf("full_name", "email_address"), store.savedEntries.map { it.fieldId }.toSet())
        assertTrue(store.events.any { it.type == "field_validation_passed" && it.fieldId == "full_name" })
        assertTrue(store.events.any { it.type == "field_validation_passed" && it.fieldId == "email_address" })
        assertEquals("successful-session", store.completedSessionId)
    }

    @Test
    fun draftAutosavesAnswersAndTheFieldToResume() = runTest {
        val store = RecordingOnboardingSessionStore()
        val controller = OnboardingFormController(
            sessionId = "draft-session",
            fields = listOf(
                KnownFields.get("full_name"),
                KnownFields.get("email_address")
            ),
            scope = backgroundScope,
            entryStore = store
        )

        controller.updateField("full_name", "Jane Wanjiku")
        controller.setResumeField("email_address")
        advanceTimeBy(700)
        runCurrent()

        assertEquals("Jane Wanjiku", store.savedDraftEntries.single {
            it.fieldId == "full_name"
        }.value)
        assertEquals("email_address", store.savedDraftResumeFieldId)
        assertFalse(controller.draftSaveStatusFlow.value.isSaving)
        assertEquals(null, controller.draftSaveStatusFlow.value.errorMessage)
    }

    @Test
    fun resumedSessionRestoresAnswersWithoutStartingANewSession() = runTest {
        val store = RecordingOnboardingSessionStore()
        val controller = OnboardingFormController(
            sessionId = "existing-draft",
            fields = listOf(
                KnownFields.get("full_name"),
                KnownFields.get("email_address")
            ),
            scope = backgroundScope,
            entryStore = store,
            initialValues = mapOf("full_name" to "Jane Wanjiku"),
            initialResumeFieldId = "email_address",
            isResumingSession = true
        )
        runCurrent()

        assertEquals("Jane Wanjiku", controller.stateFlow.value.values["full_name"])
        assertEquals("email_address", controller.resumeFieldId)
        assertEquals(null, store.startedSession)
        assertEquals(1, controller.readiness().completedRequiredFieldCount)
    }

    @Test
    fun customFieldValidationWorksAndPreservesHistoryForDynamicModelScoring() = runTest {
        val customField = FormField(
            fieldId = "loyalty_email",
            fieldName = "Loyalty email",
            fieldType = "email",
            isRequired = true,
            validationRules = listOf(Required, EmailFormat),
            description = "Email used for loyalty updates."
        )
        val modelHistories = mutableListOf<List<String>>()
        val controller = OnboardingFormController(
            sessionId = "custom-field-session",
            fields = listOf(customField, KnownFields.get("full_name")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore(),
            predictor = NextFieldPredictor { history, _, _, _ ->
                modelHistories += history
                null
            }
        )
        runCurrent() // the form opens and asks for a first suggestion before anything is typed

        controller.answer("loyalty_email", "customer@example.com")
        runCurrent()
        controller.answer("full_name", "Jane Wanjiku")
        runCurrent()

        assertTrue(controller.canContinue())
        assertEquals(listOf(emptyList(), listOf("loyalty_email")), modelHistories)
    }

    @Test
    fun readinessNamesMissingFieldsAndCalculatesRequiredCompletion() = runTest {
        val fields = listOf(
            KnownFields.get("full_name"),
            KnownFields.get("email_address")
        )
        val controller = OnboardingFormController(
            sessionId = "readiness-session",
            fields = fields,
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.answer("full_name", "Jane Wanjiku")
        runCurrent()

        val readiness = controller.readiness()
        assertEquals(2, readiness.requiredFieldCount)
        assertEquals(1, readiness.completedRequiredFieldCount)
        assertEquals(listOf("Email address"), readiness.incompleteRequiredFields)
        assertEquals(50, readiness.scorePercent)
        assertFalse(readiness.isReady)
    }

    @Test
    fun guidanceTellsAgentToCorrectAnInvalidField() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.answer("email_address", "bad")
        runCurrent()

        assertEquals("email_address", controller.guidanceFlow.value.correction?.fieldId)
        assertEquals(
            "Correct Email address: Enter a valid email address.",
            controller.guidanceFlow.value.message
        )
    }

    private data class SessionStart(
        val sessionId: String,
        val agentId: String,
        val industry: String,
        val context: String,
        val playbookId: String,
        val playbookVersion: String,
        val fieldIds: List<String>
    )

    private data class SessionEvent(
        val type: String,
        val fieldId: String?
    )

    private class RecordingOnboardingSessionStore : OnboardingSessionStore {
        var startedSession: SessionStart? = null
        var completedSessionId: String? = null
        var savedDraftEntries: List<FieldEntry> = emptyList()
        var savedDraftResumeFieldId: String? = null
        val validatedFields = mutableSetOf<String>()
        val events = mutableListOf<SessionEvent>()
        private val entries = mutableListOf<FieldEntry>()

        val savedEntries: List<FieldEntry>
            get() = entries.toList()

        override fun save(entry: FieldEntry) {
            entries.removeAll { it.fieldId == entry.fieldId }
            entries += entry
        }

        override fun saveDraft(
            sessionId: String,
            entries: List<FieldEntry>,
            resumeFieldId: String?,
            onResult: (Result<Unit>) -> Unit
        ) {
            savedDraftEntries = entries
            savedDraftResumeFieldId = resumeFieldId
            onResult(Result.success(Unit))
        }

        override fun startSession(
            sessionId: String,
            agentId: String,
            industry: String,
            context: String,
            playbook: OnboardingPlaybook,
            agentEmail: String
        ) {
            startedSession = SessionStart(
                sessionId,
                agentId,
                industry,
                context,
                playbook.id,
                playbook.version,
                playbook.fields.map { it.fieldId }
            )
        }

        override fun recordValidation(sessionId: String, fieldId: String, isValid: Boolean, message: String?) {
            if (isValid) validatedFields += fieldId
        }

        override fun appendEvent(sessionId: String, type: String, fieldId: String?) {
            events += SessionEvent(type, fieldId)
        }

        override fun markCompleted(sessionId: String) {
            completedSessionId = sessionId
        }

        override fun getSession(
            sessionId: String,
            onResult: (Result<StoredOnboardingSession?>) -> Unit
        ) = error("Retrieval is not part of this controller test")
    }

    @Test
    fun missingRequiredFieldProducesGuidance() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.answer("email_address", "")
        runCurrent()

        assertFalse(controller.canContinue())
        assertEquals("email_address", controller.guidanceFlow.value.correction?.fieldId)
        assertEquals(
            "Correct Email address: Enter a value for this field.",
            controller.guidanceFlow.value.message
        )
    }

    @Test
    fun malformedPhoneNumberProducesFormatGuidance() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("mobile_phone_number")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.answer("mobile_phone_number", "071234567")
        runCurrent()

        assertFalse(controller.canContinue())
        assertEquals(
            "Enter a valid Kenyan phone number (07XXXXXXXX, 01XXXXXXXX, +2547XXXXXXXX or +2541XXXXXXXX).",
            controller.guidanceFlow.value.correction?.message
        )
    }

    @Test
    fun dependentRequiredFieldProducesGuidanceWhenServiceTypeNeedsIt() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(
                KnownFields.get("service_type"),
                KnownFields.get("phone_service_selection")
            ),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.answer("service_type", "mobile")
        controller.answer("phone_service_selection", "")
        runCurrent()

        assertFalse(controller.canContinue())
        assertEquals(
            "phone_service_selection",
            controller.guidanceFlow.value.correction?.fieldId
        )
        assertEquals(
            "Correct Phone service selection: Enter a value because the selected option requires it.",
            controller.guidanceFlow.value.message
        )
    }

    @Test
    fun difficultSessionPublishesHighRiskGuidanceAfterRepeatedErrors() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.recordRiskEvent(
            CompletionRiskEvent(16.0, validationPassed = false, isRevisit = true, progressPercent = 25.0)
        )
        controller.recordRiskEvent(
            CompletionRiskEvent(2.0, validationPassed = false, isRevisit = true, progressPercent = 25.0)
        )
        runCurrent()

        assertEquals(CompletionRiskLevel.HIGH, controller.riskFlow.value?.level)
        assertEquals(
            "Pause and help the customer complete the remaining information.",
            controller.guidanceFlow.value.riskGuidance?.message
        )
    }

    @Test
    fun guidanceTellsAgentWhichRequiredFieldToCollectNext() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(
                KnownFields.get("full_name"),
                KnownFields.get("email_address")
            ),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.answer("full_name", "Jane Wanjiku")
        runCurrent()

        assertEquals("email_address", controller.guidanceFlow.value.nextFieldId)
        assertEquals("Collect Email address next.", controller.guidanceFlow.value.message)
    }

    @Test
    fun guidanceConfirmsWhenAllRequiredFieldsAreComplete() = runTest {
        val controller = OnboardingFormController(
            "session",
            fields = listOf(KnownFields.get("email_address")),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        controller.answer("email_address", "agent@example.com")
        runCurrent()

        assertTrue(controller.guidanceFlow.value.isComplete)
        assertEquals(
            "All required information is complete. You can continue.",
            controller.guidanceFlow.value.message
        )
    }
}

class OnboardingFormControllerRiskTest {

    private val trainedModel = requireNotNull(
        TrainedCompletionRiskModel.fromParts(
            featureNames = TrainedCompletionRiskModel.FEATURE_NAMES,
            weights = listOf(
                0.029819, 0.31253, 0.190293, -0.028253, 0.015226, 0.024499,
                0.056975, -0.033293, -1.070033, 0.127716, -0.020178, -0.02873,
                -0.49848, 1.319212
            ),
            intercept = 1.120239,
            mediumRiskThreshold = 0.6086,
            highRiskThreshold = 0.7112
        )
    )

    private val events = listOf(
        CompletionRiskEvent(5.0, false, false, 10.0),
        CompletionRiskEvent(6.0, false, true, 10.0)
    )

    @Test
    fun usesTheTrainedModelWhenItIsAvailable() = runTest {
        val controller = OnboardingFormController(
            "session",
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore(),
            industry = "banking",
            context = "business",
            riskModel = trainedModel
        )

        events.forEach(controller::recordRiskEvent)

        val expected = trainedModel.score(events, isBanking = true, isBusiness = true)!!.score
        assertEquals(expected, controller.riskFlow.value!!.score, 1e-12)
    }

    @Test
    fun fallsBackToTheHandSetRuleWithoutAModel() = runTest {
        val controller = OnboardingFormController(
            "session",
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore()
        )

        events.forEach(controller::recordRiskEvent)

        assertEquals(CompletionRiskScorer.score(events).score, controller.riskFlow.value!!.score, 1e-12)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NextFieldPredictorTest {
    @Test
    fun validAnswersAreSentToThePredictorAndRecommendationIsPublished() = runTest {
        var receivedHistory: List<String>? = null
        var receivedValidation: String? = null
        val predictor = NextFieldPredictor { history, _, _, validation ->
            if (validation == "passed") {
                receivedHistory = history
                receivedValidation = validation
                FieldPrediction("mobile_phone_number", 0.8f)
            } else {
                null
            }
        }
        val controller = OnboardingFormController(
            sessionId = "session",
            fields = listOf(
                KnownFields.get("full_name"),
                KnownFields.get("email_address"),
                KnownFields.get("mobile_phone_number")
            ),
            scope = backgroundScope,
            entryStore = InMemoryFieldEntryStore(),
            predictor = predictor
        )

        controller.answer("full_name", "Jane Wanjiku")
        runCurrent()
        controller.answer("email_address", "agent@example.com")
        runCurrent()

        assertEquals(listOf("full_name", "email_address"), receivedHistory)
        assertEquals("passed", receivedValidation)
        assertEquals("mobile_phone_number", controller.guidanceFlow.value.prediction?.fieldId)
        assertTrue(controller.guidanceFlow.value.prediction?.confidence == 0.8f)
    }

    @Test
    fun telecomAndBankingWorkflowsPassTheirSelectedIndustryToGuidance() = runTest {
        val receivedIndustries = mutableListOf<String>()
        val predictor = NextFieldPredictor { _, industry, context, validation ->
            assertEquals("individual", context)
            if (validation == "passed") {
                receivedIndustries += industry
                FieldPrediction("email_address", 0.7f)
            } else {
                null
            }
        }

        listOf("telecom", "banking").forEach { industry ->
            val controller = OnboardingFormController(
                sessionId = "session-$industry",
                fields = listOf(KnownFields.get("full_name"), KnownFields.get("email_address")),
                scope = backgroundScope,
                entryStore = InMemoryFieldEntryStore(),
                predictor = predictor,
                industry = industry,
                context = "individual"
            )

            controller.answer("full_name", "Jane Wanjiku")
            runCurrent()

            assertEquals("email_address", controller.guidanceFlow.value.prediction?.fieldId)
            assertEquals(0.7f, controller.guidanceFlow.value.prediction?.confidence)
            assertEquals(industry, receivedIndustries.last())
        }

        assertEquals(listOf("telecom", "banking"), receivedIndustries)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveValidationSessionTest {
    @Test
    fun typingDoesNotCheckTheAnswerLeavingTheBoxDoes() {
        val validation = LiveValidationSession(
            sessionId = "session",
            fields = listOf(KnownFields.get("email_address"))
        )

        validation.updateField("email_address", "agent@")
        validation.updateField("email_address", "agent@exam")
        assertTrue(validation.stateFlow.value.results.isEmpty())

        validation.checkField("email_address")
        assertFalse(validation.stateFlow.value.results.getValue("email_address").isValid)
    }

    @Test
    fun manyKeystrokesThenLeavingTheBoxIsOneAttempt() {
        val validation = LiveValidationSession("session", listOf(KnownFields.get("date_of_birth")))

        "2000-01-15".indices.forEach { end ->
            validation.updateField("date_of_birth", "2000-01-15".substring(0, end + 1))
        }
        validation.checkField("date_of_birth")

        val attempts = validation.stateFlow.value.interactionHistory
        assertEquals(1, attempts.size)
        assertEquals(true, attempts.single().validationPassed)
    }

    @Test
    fun leavingAnUnchangedAnswerRecordsNothing() {
        val validation = LiveValidationSession("session", listOf(KnownFields.get("email_address")))

        validation.answer("email_address", "agent@example.com")
        validation.checkField("email_address")
        validation.checkField("email_address")

        assertEquals(1, validation.stateFlow.value.interactionHistory.size)
    }

    @Test
    fun correctingAWrongAnswerIsASecondAttempt() {
        val validation = LiveValidationSession("session", listOf(KnownFields.get("email_address")))

        validation.answer("email_address", "agent@example")
        validation.answer("email_address", "agent@example.com")

        assertEquals(
            listOf(false, true),
            validation.stateFlow.value.interactionHistory.map { it.validationPassed }
        )
    }

    @Test
    fun checkPendingFieldsChecksEveryUncheckedAnswerOnce() {
        val validation = LiveValidationSession(
            "session",
            listOf(KnownFields.get("full_name"), KnownFields.get("email_address"))
        )

        validation.updateField("full_name", "Jane Wanjiku")
        validation.updateField("email_address", "bad")
        validation.checkPendingFields()
        validation.checkPendingFields()

        val attempts = validation.stateFlow.value.interactionHistory
        assertEquals(listOf("full_name", "email_address"), attempts.map { it.fieldId })
        assertEquals(listOf(true, false), attempts.map { it.validationPassed })
    }

    @Test
    fun changingADependencyRevalidatesOnlyDependentFields() = runTest {
        val fields = listOf(
            KnownFields.get("service_type"),
            KnownFields.get("phone_service_selection"),
            KnownFields.get("email_address")
        )
        val validation = LiveValidationSession("session", fields)

        validation.answer("phone_service_selection", "")
        validation.answer("email_address", "valid@example.com")
        runCurrent()
        assertTrue(validation.stateFlow.value.results.getValue("email_address").isValid)

        validation.answer("service_type", "mobile")
        runCurrent()

        assertFalse(validation.stateFlow.value.results.getValue("phone_service_selection").isValid)
        assertEquals(true, validation.stateFlow.value.results.getValue("email_address").isValid)
    }

    @Test
    fun unrelatedFieldDoesNotChangeWhenAnotherFieldIsEdited() = runTest {
        val fields = listOf(
            KnownFields.get("email_address"),
            KnownFields.get("profession")
        )
        val validation = LiveValidationSession("session", fields)

        validation.answer("email_address", "invalid")
        runCurrent()
        val emailResult = validation.stateFlow.value.results.getValue("email_address")

        validation.answer("profession", "Engineer")
        runCurrent()

        assertEquals(emailResult, validation.stateFlow.value.results.getValue("email_address"))
    }

    @Test
    fun adminRuleOverridesAreUsedByLiveValidation() = runTest {
        val configuration = FieldRuleConfiguration(
            mapOf(
                "profession" to FieldRuleOverride(
                    addRules = listOf(RegexPattern("^[A-Z].*")),
                    removeRules = listOf(NotBlank)
                )
            )
        )
        val validation = LiveValidationSession(
            sessionId = "session",
            fields = listOf(KnownFields.get("profession")),
            configuration = configuration
        )

        validation.answer("profession", "engineer")
        runCurrent()
        assertFalse(validation.stateFlow.value.results.getValue("profession").isValid)
    }
}
