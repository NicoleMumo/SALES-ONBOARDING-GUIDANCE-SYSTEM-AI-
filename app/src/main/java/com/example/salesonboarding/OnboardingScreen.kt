package com.example.salesonboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.salesonboarding.ui.theme.SalesOnboardingTheme
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.FieldEntryStore
import com.example.salesonboarding.validation.LiveValidationState
import com.example.salesonboarding.validation.ValidationErrorDisplayMapper
import com.example.salesonboarding.validation.RequiredIf
import com.example.salesonboarding.validation.OneOf
import kotlinx.coroutines.CoroutineScope
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.DisposableEffect

@Composable
fun OnboardingScreen(
    controller: OnboardingFormController,
    modifier: Modifier = Modifier
) {
    val state by controller.stateFlow.collectAsState()
    val guidance by controller.guidanceFlow.collectAsState()
    val readiness = controller.readiness(state)
    val isOnline = rememberNetworkStatus()

    OnboardingScreenContent(
        playbook = controller.playbook,
        fields = controller.fields,
        state = state,
        guidance = guidance,
        readiness = readiness,
        isOnline = isOnline,
        onValueChange = { fieldId, value -> controller.updateField(fieldId, value) },
        onSubmit = { controller.submit(state) },
        canContinue = controller.canContinue(state),
        modifier = modifier
    )
}

@Composable
fun OnboardingScreenContent(
    playbook: OnboardingPlaybook,
    fields: List<FormField>,
    state: LiveValidationState,
    guidance: NextFieldGuidance,
    readiness: OnboardingReadiness,
    isOnline: Boolean,
    onValueChange: (fieldId: String, value: String) -> Unit,
    onSubmit: () -> Unit,
    canContinue: Boolean,
    modifier: Modifier = Modifier
) {
    val errors = remember(state) {
        ValidationErrorDisplayMapper.forState(state).associateBy { it.fieldId }
    }
    val invalidFieldCount = errors.size
    var submitted by remember { mutableStateOf(false) }
    val completedCount = readiness.completedRequiredFieldCount
    val currentFieldId = guidance.correction?.fieldId ?: guidance.nextFieldId
    val currentField = fields.firstOrNull { it.fieldId == currentFieldId }
        ?: fields.firstOrNull { field ->
            val required = field.isRequired || field.validationRule.any {
                it is RequiredIf && state.values[it.dependsOnFieldId]
                    ?.equals(it.expectedValue, ignoreCase = true) == true
            }
            required && (
                state.values[field.fieldId].isNullOrBlank() ||
                    state.results[field.fieldId]?.isValid != true
                )
        }
    val currentStep = currentField?.let { fields.indexOf(it) + 1 } ?: fields.size
    val progress = if (readiness.requiredFieldCount == 0) 1f
    else completedCount.toFloat() / readiness.requiredFieldCount

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
                    text = playbook.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Standard playbook ${playbook.version}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isOnline) {
                        "Online — session syncs with your team."
                    } else {
                        "Offline — changes are saved on this device and will sync when connected."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOnline) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Follow the steps in order so every customer receives the same onboarding.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$completedCount of ${readiness.requiredFieldCount} required fields complete",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (invalidFieldCount == 0) {
                        "Validation is up to date"
                    } else {
                        "$invalidFieldCount field${if (invalidFieldCount == 1) "" else "s"} need attention"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (invalidFieldCount == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Customer readiness: ${readiness.scorePercent}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = if (readiness.isReady) {
                                "Ready to submit. Required information is complete."
                            } else if (readiness.incompleteRequiredFields.isNotEmpty()) {
                                "Still needed: ${readiness.incompleteRequiredFields.take(3).joinToString()}"
                            } else {
                                "Review these fields: ${readiness.invalidOptionalFields.take(3).joinToString()}"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (currentField != null) {
                            Text(
                                text = "Next: Step $currentStep of ${fields.size} — ${currentField.fieldName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
            }
        }
        item {
            if (guidance.message.isNotBlank()) {
                val predictedField = guidance.prediction?.let { prediction ->
                    fields.firstOrNull { it.fieldId == prediction.fieldId }
                }
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
                                else -> "AI guidance"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = guidance.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        predictedField?.let { field ->
                            Text(
                                text = "Predicted next field: ${field.fieldName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
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
                                    text = "${(prediction.confidence.coerceIn(0f, 1f) * 100).toInt()}% model confidence",
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
            val isValid = field.fieldId in state.touchedFieldIds &&
                state.results[field.fieldId]?.isValid == true
            if (field.validationRule.filterIsInstance<OneOf>().firstOrNull() != null) {
                val choices = field.validationRule.filterIsInstance<OneOf>()
                    .flatMap { it.choices }
                    .distinct()
                ChoiceField(
                    field = field,
                    value = state.values[field.fieldId].orEmpty(),
                    choices = choices,
                    error = error?.message,
                    isValid = isValid,
                    onValueChange = { value ->
                        submitted = false
                        onValueChange(field.fieldId, value)
                    }
                )
            } else if (field.fieldType.equals("boolean", ignoreCase = true)) {
                Column {
                    Row {
                        Checkbox(
                            checked = state.values[field.fieldId].equals("true", ignoreCase = true) ||
                                state.values[field.fieldId].equals("yes", ignoreCase = true),
                            onCheckedChange = {
                                submitted = false
                                onValueChange(field.fieldId, if (it) "true" else "false")
                            }
                        )
                        Text(field.fieldName)
                    }
                    error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                    if (field.description.isNotBlank()) {
                        Text(field.description, style = MaterialTheme.typography.bodySmall)
                    }
                    if (error == null && isValid) Text("Looks good")
                }
            } else {
                OutlinedTextField(
                    value = state.values[field.fieldId].orEmpty(),
                    onValueChange = {
                        submitted = false
                        onValueChange(field.fieldId, it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(field.fieldName) },
                    placeholder = { Text("Enter ${field.fieldName.lowercase()}") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (
                        error != null || isValid || field.description.isNotBlank()
                    ) {
                        {
                            Column {
                                error?.let { Text(it.message) }
                                if (field.description.isNotBlank()) Text(field.description)
                                if (error == null && isValid) Text("Looks good")
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field.fieldType.lowercase()) {
                            "number" -> KeyboardType.Decimal
                            "email" -> KeyboardType.Email
                            "phone" -> KeyboardType.Phone
                            else -> KeyboardType.Text
                        },
                        imeAction = ImeAction.Next
                    )
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Review before continuing",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (readiness.isReady) {
                            "The required steps are complete. Review the customer details and continue."
                        } else {
                            "Resolve the readiness items above before completing this onboarding."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    submitted = true
                    onSubmit()
                },
                enabled = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(
                    text = if (submitted) "Submitted" else "Continue",
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
    entryStore: FieldEntryStore,
    predictor: NextFieldPredictor? = null,
    industry: String = "telecom",
    context: String = "individual",
    agentId: String = "unknown-agent",
    fields: List<com.example.salesonboarding.validation.FormField> = OnboardingFormController.DEFAULT_FIELDS,
    playbook: OnboardingPlaybook? = null
): OnboardingFormController = remember(sessionId, scope, entryStore, predictor, industry, context, agentId, fields, playbook) {
    OnboardingFormController(
        sessionId,
        fields = fields,
        scope = scope,
        entryStore = entryStore,
        predictor = predictor,
        industry = industry,
        context = context,
        agentId = agentId,
        playbook = playbook
    )
}

@Composable
private fun ChoiceField(
    field: FormField,
    value: String,
    choices: List<String>,
    error: String?,
    isValid: Boolean,
    onValueChange: (String) -> Unit
) {
    var expanded by remember(field.fieldId) { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value.replace('_', ' ').replaceFirstChar { it.uppercase() },
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            label = { Text(field.fieldName) },
            readOnly = true,
            isError = error != null,
            supportingText = {
                Column {
                    error?.let { Text(it) }
                    if (field.description.isNotBlank()) Text(field.description)
                    if (error == null && isValid) Text("Looks good")
                }
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.sorted().forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onValueChange(choice)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun rememberNetworkStatus(): Boolean {
    val context = LocalContext.current.applicationContext
    var isOnline by remember { mutableStateOf(context.hasValidatedNetwork()) }
    DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = connectivityManager.getNetworkCapabilities(network)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            }

            override fun onLost(network: Network) {
                isOnline = context.hasValidatedNetwork()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                isOnline = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    return isOnline
}

private fun Context.hasValidatedNetwork(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return connectivityManager.activeNetwork
        ?.let(connectivityManager::getNetworkCapabilities)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
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
        val playbook = OnboardingPlaybooks.forIndustry(
            "telecom",
            com.example.salesonboarding.validation.FieldRuleConfiguration()
        )
        OnboardingScreenContent(
            playbook = playbook,
            fields = playbook.fields,
            state = sampleState,
            guidance = NextFieldGuidance(
                prediction = FieldPrediction("mobile_phone_number", 0.95f),
                nextFieldId = "mobile_phone_number",
                message = "Collect Mobile phone number next."
            ),
            readiness = OnboardingReadiness(2, 2, emptyList(), emptyList()),
            isOnline = true,
            onValueChange = { _, _ -> },
            onSubmit = {},
            canContinue = true
        )
    }
}
