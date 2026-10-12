package com.example.salesonboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.salesonboarding.ui.theme.SalesOnboardingTheme
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.LiveValidationState
import com.example.salesonboarding.validation.ValidationErrorDisplayMapper
import kotlinx.coroutines.CoroutineScope

@Composable
fun OnboardingScreen(
    controller: OnboardingFormController,
    modifier: Modifier = Modifier
) {
    val state by controller.stateFlow.collectAsState()
    val guidance by controller.guidanceFlow.collectAsState()

    OnboardingScreenContent(
        fields = controller.fields,
        state = state,
        guidance = guidance,
        onValueChange = { fieldId, value -> controller.updateField(fieldId, value) },
        onSubmit = { controller.submit(state) },
        canContinue = controller.canContinue(state),
        modifier = modifier
    )
}

@Composable
fun OnboardingScreenContent(
    fields: List<FormField>,
    state: LiveValidationState,
    guidance: NextFieldGuidance,
    onValueChange: (fieldId: String, value: String) -> Unit,
    onSubmit: () -> Unit,
    canContinue: Boolean,
    modifier: Modifier = Modifier
) {
    val errors = remember(state) {
        ValidationErrorDisplayMapper.forState(state).associateBy { it.fieldId }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Customer onboarding",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Enter the customer details to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            if (guidance.message.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = when {
                                guidance.correction != null -> "Action needed"
                                guidance.isComplete -> "Onboarding ready"
                                else -> "Next step"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = guidance.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        guidance.riskGuidance?.let { riskGuidance ->
                            Text(
                                text = riskGuidance.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        guidance.prediction?.takeIf { guidance.correction == null && !guidance.isComplete }
                            ?.let { prediction ->
                                Text(
                                    text = "${(prediction.confidence * 100).toInt()}% confidence",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                    }
                }
            }
        }
        items(fields, key = { it.fieldId }) { field ->
            val error = errors[field.fieldId]
            OutlinedTextField(
                value = state.values[field.fieldId].orEmpty(),
                onValueChange = { onValueChange(field.fieldId, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(field.fieldName) },
                placeholder = { Text("Enter ${field.fieldName.lowercase()}") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { display ->
                    { Text(display.message) }
                },
                keyboardOptions = if (field.fieldType == "number") {
                    KeyboardOptions(keyboardType = KeyboardType.Decimal)
                } else {
                    KeyboardOptions.Default
                }
            )
        }
        item {
            Button(
                onClick = onSubmit,
                enabled = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun rememberOnboardingController(
    sessionId: String,
    scope: CoroutineScope,
    entryStore: InMemoryFieldEntryStore,
    predictor: NextFieldPredictor? = null,
    industry: String = "telecom",
    context: String = "individual"
): OnboardingFormController = remember(sessionId, scope, entryStore, predictor, industry, context) {
    OnboardingFormController(
        sessionId,
        scope = scope,
        entryStore = entryStore,
        predictor = predictor,
        industry = industry,
        context = context
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun OnboardingScreenPreview() {
    val sampleFields = OnboardingFormController.DEFAULT_FIELDS
    val sampleState = LiveValidationState(
        values = mapOf(
            "full_name" to "Jane Doe",
            "email_address" to "jane.doe@example.com"
        ),
        touchedFieldIds = setOf("full_name", "email_address")
    )

    SalesOnboardingTheme {
        OnboardingScreenContent(
            fields = sampleFields,
            state = sampleState,
            guidance = NextFieldGuidance(
                prediction = FieldPrediction("mobile_phone_number", 0.95f),
                nextFieldId = "mobile_phone_number",
                message = "Collect Mobile phone number next."
            ),
            onValueChange = { _, _ -> },
            onSubmit = {},
            canContinue = true
        )
    }
}
