package com.example.salesonboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.salesonboarding.validation.ValidationErrorDisplayMapper
import kotlinx.coroutines.CoroutineScope

@Composable
fun OnboardingScreen(
    controller: OnboardingFormController,
    modifier: Modifier = Modifier
) {
    val state by controller.stateFlow.collectAsState()
    val guidance by controller.guidanceFlow.collectAsState()
    val errors = remember(state) {
        ValidationErrorDisplayMapper.forState(state).associateBy { it.fieldId }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Customer onboarding",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        item {
            guidance.prediction?.let { prediction ->
                Text(
                    text = "Recommended next step: ${controller.fields.firstOrNull { it.fieldId == prediction.fieldId }?.fieldName ?: prediction.fieldId}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        items(controller.fields, key = { it.fieldId }) { field ->
            val error = errors[field.fieldId]
            OutlinedTextField(
                value = state.values[field.fieldId].orEmpty(),
                onValueChange = { controller.updateField(field.fieldId, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(field.fieldName) },
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
                onClick = { controller.submit(state) },
                enabled = controller.canContinue(state),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
fun rememberOnboardingController(
    sessionId: String,
    scope: CoroutineScope,
    entryStore: InMemoryFieldEntryStore,
    predictor: NextFieldPredictor? = null
): OnboardingFormController = remember(sessionId, scope, entryStore, predictor) {
    OnboardingFormController(sessionId, scope = scope, entryStore = entryStore, predictor = predictor)
}
