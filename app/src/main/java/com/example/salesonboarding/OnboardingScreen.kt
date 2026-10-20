package com.example.salesonboarding

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.salesonboarding.ui.theme.SalesOnboardingTheme
import com.example.salesonboarding.validation.DateFormatAndMinimumAge
import com.example.salesonboarding.validation.EmailFormat
import com.example.salesonboarding.validation.FieldEntry
import com.example.salesonboarding.validation.FieldEntryStore
import com.example.salesonboarding.validation.FormField
import com.example.salesonboarding.validation.KenyanPhoneFormat
import com.example.salesonboarding.validation.LiveValidationState
import com.example.salesonboarding.validation.MustBeTrue
import com.example.salesonboarding.validation.OneOf
import com.example.salesonboarding.validation.PatternByFieldValue
import com.example.salesonboarding.validation.PositiveNumber
import com.example.salesonboarding.validation.RegexPattern
import com.example.salesonboarding.validation.RequiredIf
import com.example.salesonboarding.validation.ValidationContext
import com.example.salesonboarding.validation.ValidationErrorDisplayMapper
import com.example.salesonboarding.validation.ValidationResult
import kotlinx.coroutines.CoroutineScope

@Composable
fun OnboardingScreen(
    controller: OnboardingFormController,
    onNewOnboarding: () -> Unit = {},
    onReturnToDashboard: () -> Unit = {},
    configurationWarning: String? = null,
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
        onSubmit = { onResult -> controller.submit(state, onResult) },
        canContinue = controller.canContinue(state),
        onNewOnboarding = onNewOnboarding,
        onReturnToDashboard = onReturnToDashboard,
        configurationWarning = configurationWarning,
        modifier = modifier
    )
}

@Composable
fun OnboardingConsentScreen(
    onConsent: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Customer Consent",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Please verify customer permission before collecting personal details.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ConsentPoint(
                    title = "Purpose of Collection",
                    description = "Information is used solely to verify identity and set up the customer account in compliance with regulations."
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConsentPoint(
                    title = "Data Security & Retention",
                    description = "Stored securely in Firebase with restricted organizational access. Deletion is governed by company policy."
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConsentPoint(
                    title = "Customer Rights",
                    description = "The customer may request review of their submitted details through authorized company channels."
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { confirmed = !confirmed },
            shape = MaterialTheme.shapes.medium,
            color = if (confirmed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = if (confirmed) 1.5.dp else 1.dp,
                color = if (confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = confirmed,
                    onCheckedChange = null
                )
                Text(
                    "Customer has been informed and agreed to proceed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (confirmed) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (confirmed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onConsent,
                enabled = confirmed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Start Onboarding", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun ConsentPoint(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
    onSubmit: (onResult: (Result<Unit>) -> Unit) -> Boolean,
    canContinue: Boolean,
    onNewOnboarding: () -> Unit = {},
    onReturnToDashboard: () -> Unit = {},
    configurationWarning: String? = null,
    modifier: Modifier = Modifier
) {
    val errors = remember(state) {
        ValidationErrorDisplayMapper.forState(state).associateBy { it.fieldId }
    }
    var submitted by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var activeFieldId by remember(playbook.id, playbook.version) {
        mutableStateOf(fields.firstOrNull()?.fieldId)
    }
    var visitedFieldIds by remember(playbook.id, playbook.version) {
        mutableStateOf(emptyList<String>())
    }
    val completedCount = readiness.completedRequiredFieldCount
    val currentField = fields.firstOrNull { it.fieldId == activeFieldId }
        ?: fields.firstOrNull { it.fieldId !in visitedFieldIds }
        ?: fields.lastOrNull()
    val currentFieldRequired = currentField?.let { field ->
        field.isRequired || field.validationRule.filterIsInstance<RequiredIf>().any { rule ->
            state.values[rule.dependsOnFieldId]
                ?.trim()
                ?.equals(rule.expectedValue.trim(), ignoreCase = true) == true
        }
    } == true
    val currentFieldHasBeenEdited = currentField?.let { field ->
        field.fieldId in state.touchedFieldIds || !state.values[field.fieldId].isNullOrBlank()
    } == true

    fun validateField(field: FormField): ValidationResult {
        val value = state.values[field.fieldId]
        val required = field.isRequired || field.validationRule.any {
            it is RequiredIf && state.values[it.dependsOnFieldId]
                ?.equals(it.expectedValue, ignoreCase = true) == true
        }
        if (!required && value.isNullOrBlank()) return ValidationResult.valid
        return field.validate(
            FieldEntry(
                entryId = "${playbook.id}:${field.fieldId}",
                sessionId = playbook.id,
                fieldId = field.fieldId,
                value = value
            ),
            ValidationContext(state.values)
        )
    }

    val currentFieldValidation = currentField?.let(::validateField)
    val currentFieldError = currentField?.let { field ->
        if (!currentFieldHasBeenEdited) null
        else currentFieldValidation?.errorMessage ?: errors[field.fieldId]?.message
    }
    val currentFieldIndex = currentField?.let { fields.indexOf(it) } ?: -1
    val visitedAfterCurrent = currentField?.let {
        (visitedFieldIds + it.fieldId).distinct()
    } ?: visitedFieldIds
    val correctionTarget = guidance.correction?.fieldId?.let { id ->
        fields.firstOrNull { it.fieldId == id && id != currentField?.fieldId }
    }
    val predictedTarget = guidance.nextFieldId?.let { id ->
        fields.firstOrNull {
            it.fieldId == id && id != currentField?.fieldId && id !in visitedAfterCurrent
        }
    }
    val incompleteTarget = fields.firstOrNull {
        it.fieldId != currentField?.fieldId && !validateField(it).isValid
    }
    val orderedUnvisitedTarget = fields.drop((currentFieldIndex + 1).coerceAtMost(fields.size))
        .firstOrNull { it.fieldId !in visitedAfterCurrent }
        ?: fields.take(currentFieldIndex.coerceAtLeast(0))
            .firstOrNull { it.fieldId !in visitedAfterCurrent }
    val nextField = correctionTarget ?: predictedTarget ?: incompleteTarget ?: orderedUnvisitedTarget
    val progress = if (readiness.requiredFieldCount == 0) 1f
    else completedCount.toFloat() / readiness.requiredFieldCount

    if (submitted) {
        OnboardingCompleteScreen(
            fields = fields,
            values = state.values,
            onNewOnboarding = onNewOnboarding,
            onReturnToDashboard = onReturnToDashboard,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Configuration warning if present
            if (configurationWarning != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = configurationWarning,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // 2. Modern, sleek progress header
            item {
                ProgressHeader(
                    title = playbook.title,
                    completedCount = completedCount,
                    requiredCount = readiness.requiredFieldCount,
                    progress = progress,
                    isOnline = isOnline
                )
            }

            // 3. Urgent Correction Banner (only shown when a prior field needs attention)
            guidance.correction?.let { correction ->
                val correctionField = fields.firstOrNull {
                    it.fieldId == correction.fieldId && it.fieldId != currentField?.fieldId
                }
                if (correctionField != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeFieldId = correctionField.fieldId
                                },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("⚠️", style = MaterialTheme.typography.titleMedium)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Please review ${correctionField.fieldName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        correction.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    "Fix →",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // 4. Completion Risk Coaching Tip (only appears when risk is elevated)
            guidance.riskGuidance?.let { riskGuidance ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("💡", style = MaterialTheme.typography.titleMedium)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Agent Coaching Tip",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    riskGuidance.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // 5. Active Field Card (clean, focused, uncluttered)
            item {
                currentField?.let { field ->
                    val error = currentFieldError
                    val isValid = currentFieldValidation?.isValid == true &&
                        !state.values[field.fieldId].isNullOrBlank()
                    val formatHint = primaryFormatHint(field, state.values, fields)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Title & Requirement Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    field.fieldName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                RequirementBadge(required = currentFieldRequired)
                            }

                            // Description (only if meaningful)
                            val description = field.description.takeIf {
                                it.isNotBlank() &&
                                    !it.equals("Enter the ${field.fieldName.lowercase()}.", ignoreCase = true) &&
                                    !it.equals("Enter or choose the ${field.fieldName.lowercase()} for this customer.", ignoreCase = true)
                            }
                            if (description != null) {
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Field Input controls
                            val oneOfRule = field.validationRule.filterIsInstance<OneOf>().firstOrNull()
                            if (oneOfRule != null) {
                                val choices = oneOfRule.choices.distinct()
                                ChoiceField(
                                    field = field,
                                    value = state.values[field.fieldId].orEmpty(),
                                    choices = choices,
                                    error = error,
                                    isValid = isValid,
                                    onValueChange = { value ->
                                        submitted = false
                                        onValueChange(field.fieldId, value)
                                    }
                                )
                            } else if (field.fieldType.equals("boolean", ignoreCase = true)) {
                                val checked =
                                    state.values[field.fieldId].equals("true", ignoreCase = true) ||
                                        state.values[field.fieldId].equals("yes", ignoreCase = true)

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                submitted = false
                                                onValueChange(
                                                    field.fieldId,
                                                    if (checked) "false" else "true"
                                                )
                                            },
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            width = if (checked) 1.5.dp else 1.dp,
                                            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = null
                                            )
                                            Text(
                                                fieldActionLabel(field),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    if (error != null) {
                                        Text(
                                            error,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            } else {
                                val isMultiline = field.fieldId.contains("address") ||
                                    field.fieldId.contains("notes")

                                OutlinedTextField(
                                    value = state.values[field.fieldId].orEmpty(),
                                    onValueChange = {
                                        submitted = false
                                        onValueChange(field.fieldId, it)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(field.fieldName) },
                                    placeholder = { Text(fieldPlaceholder(field)) },
                                    shape = MaterialTheme.shapes.medium,
                                    singleLine = !isMultiline,
                                    minLines = if (isMultiline) 3 else 1,
                                    maxLines = if (isMultiline) 4 else 1,
                                    isError = error != null,
                                    trailingIcon = {
                                        if (isValid) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        "✓",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        } else if (error != null) {
                                            Text(
                                                "!",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    },
                                    supportingText = {
                                        when {
                                            error != null -> Text(
                                                error,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            isValid -> Text(
                                                "Looks good",
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            formatHint != null -> Text(
                                                formatHint,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = when (field.fieldType.lowercase()) {
                                            "number" -> KeyboardType.Decimal
                                            "email" -> KeyboardType.Email
                                            "phone" -> KeyboardType.Phone
                                            else -> KeyboardType.Text
                                        },
                                        imeAction = ImeAction.Done
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Sticky Bottom Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Submit error alert
                submitError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Subtitle indicating next step foresight without screen clutter
                if (nextField != null && nextField.fieldId != currentField?.fieldId) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Suggested next:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    nextField.fieldName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        if (!currentFieldRequired && state.values[currentField?.fieldId].isNullOrBlank()) {
                            Text(
                                "Optional field",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            val previousFieldId = visitedFieldIds.lastOrNull()
                            if (previousFieldId != null) {
                                visitedFieldIds = visitedFieldIds.dropLast(1)
                                activeFieldId = previousFieldId
                            }
                        },
                        enabled = visitedFieldIds.isNotEmpty() && !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Back")
                    }

                    val isOptionalSkip = !currentFieldRequired &&
                        state.values[currentField?.fieldId].isNullOrBlank() &&
                        nextField != null

                    val isFieldSatisfied = currentFieldValidation?.isValid == true || isOptionalSkip

                    Button(
                        onClick = {
                            val field = currentField
                            if (field != null && (currentFieldValidation?.isValid == true || isOptionalSkip)) {
                                val next = nextField
                                visitedFieldIds = if (next == null) {
                                    visitedAfterCurrent
                                } else {
                                    visitedAfterCurrent.filterNot { it == next.fieldId }
                                }
                                if (next != null) {
                                    activeFieldId = next.fieldId
                                } else if (canContinue) {
                                    isSubmitting = true
                                    submitError = null
                                    val accepted = onSubmit { result ->
                                        isSubmitting = false
                                        result.onSuccess { submitted = true }
                                            .onFailure {
                                                submitError = it.message
                                                    ?: "Could not save this onboarding session."
                                            }
                                    }
                                    if (!accepted) isSubmitting = false
                                }
                            }
                        },
                        enabled = !isSubmitting && isFieldSatisfied && (nextField != null || canContinue),
                        modifier = Modifier
                            .weight(2f)
                            .height(50.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Text(
                                when {
                                    isOptionalSkip -> "Skip this field"
                                    nextField != null -> "Continue"
                                    canContinue -> "Complete onboarding"
                                    else -> "Review answers"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(
    title: String,
    completedCount: Int,
    requiredCount: Int,
    progress: Float,
    isOnline: Boolean
) {
    val clamped = progress.coerceIn(0f, 1f)
    val percentage = (clamped * 100).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "$completedCount of $requiredCount required fields completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        "$percentage%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LinearProgressIndicator(
                progress = { clamped },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (!isOnline) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        "● Offline mode · Changes will sync when reconnected",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun RequirementBadge(required: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (required) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = if (required) "Required" else "Optional",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (required) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OnboardingCompleteScreen(
    fields: List<FormField>,
    values: Map<String, String?>,
    onNewOnboarding: () -> Unit,
    onReturnToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filledFieldCount = fields.count { !values[it.fieldId].isNullOrBlank() }
    val customerFields = listOf("full_name", "mobile_phone_number", "email_address")
        .mapNotNull { fieldId ->
            val field = fields.firstOrNull { it.fieldId == fieldId } ?: return@mapNotNull null
            values[fieldId]?.takeIf(String::isNotBlank)?.let { field.fieldName to it }
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "✓",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Onboarding Completed!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Customer details were verified and stored in Firebase.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Customer summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "$filledFieldCount of ${fields.size} fields",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    if (customerFields.isEmpty()) {
                        Text(
                            "Customer record saved successfully.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        customerFields.forEach { (label, value) ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    value,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onNewOnboarding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Onboard Another Customer", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onReturnToDashboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Return to Dashboard")
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
    agentUid: String = "unknown-agent",
    fields: List<FormField> = OnboardingFormController.DEFAULT_FIELDS,
    playbook: OnboardingPlaybook? = null,
    initialModelUnavailable: Boolean = false,
    agentEmail: String = ""
): OnboardingFormController = remember(
    sessionId, scope, entryStore, predictor, industry, context,
    agentUid, fields, playbook, initialModelUnavailable, agentEmail
) {
    OnboardingFormController(
        sessionId,
        fields = fields,
        scope = scope,
        entryStore = entryStore,
        predictor = predictor,
        industry = industry,
        context = context,
        agentId = agentUid,
        playbook = playbook,
        initialModelUnavailable = initialModelUnavailable,
        agentEmail = agentEmail
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChoiceField(
    field: FormField,
    value: String,
    choices: List<String>,
    error: String?,
    isValid: Boolean,
    onValueChange: (String) -> Unit
) {
    // Usability upgrade: If <= 4 choices, use 1-tap choice cards instead of a multi-tap dropdown!
    if (choices.size <= 4) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                val isSelected = value.equals(choice, ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onValueChange(choice) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            choice.toDisplayLabel(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Text(
                                "✓",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    } else {
        // Dropdown for longer lists
        var expanded by remember(field.fieldId) { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = value.replace('_', ' ').replaceFirstChar { it.uppercase() },
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                label = { Text(field.fieldName) },
                placeholder = { Text("Select an option") },
                readOnly = true,
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                isError = error != null,
                supportingText = {
                    when {
                        error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                        isValid -> Text("Looks good", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                choices.sorted().forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.toDisplayLabel()) },
                        onClick = {
                            onValueChange(choice)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun fieldPlaceholder(field: FormField): String = when {
    field.validationRule.any { it is EmailFormat } -> "name@example.com"
    field.validationRule.any { it is KenyanPhoneFormat } -> "0712 345 678 or +254..."
    field.validationRule.any { it is DateFormatAndMinimumAge } -> "YYYY-MM-DD"
    field.fieldId.contains("dob") || field.fieldId.contains("date_of_birth") -> "YYYY-MM-DD"
    field.fieldType.equals("number", ignoreCase = true) -> "e.g. 50000"
    field.fieldId.contains("address") -> "Street, building, or area"
    else -> "Enter ${field.fieldName.lowercase()}"
}

private fun primaryFormatHint(
    field: FormField,
    values: Map<String, String?>,
    fields: List<FormField>
): String? = fieldGuidanceDetails(field, values, fields)
    .filterNot {
        it.startsWith("Available choices:") ||
            it.startsWith("Required when ")
    }
    .firstOrNull()

private fun fieldGuidanceDetails(
    field: FormField,
    values: Map<String, String?>,
    fields: List<FormField>
): List<String> = buildList {
    val choices = field.validationRule.filterIsInstance<OneOf>()
        .flatMap { it.choices }
        .distinct()
        .sorted()
    if (choices.isNotEmpty()) {
        add("Available choices: ${choices.joinToString { it.toDisplayLabel() }}.")
    }

    if (field.validationRule.any { it is EmailFormat }) {
        add("Format: name@example.com")
    }
    if (field.validationRule.any { it is KenyanPhoneFormat }) {
        add("Format: 07XXXXXXXX or +2547XXXXXXXX")
    }
    field.validationRule.filterIsInstance<DateFormatAndMinimumAge>().firstOrNull()?.let { rule ->
        add("Format: YYYY-MM-DD (Minimum age: ${rule.minimumAge})")
    }
    if (field.validationRule.any { it is PositiveNumber }) {
        add("Must be a positive number greater than zero")
    }
    field.validationRule.filterIsInstance<RegexPattern>()
        .map { it.message }
        .filter { it != "Enter a value in the required format." }
        .distinct()
        .forEach(::add)

    field.validationRule.filterIsInstance<PatternByFieldValue>().forEach { rule ->
        val dependencyName = fields.firstOrNull { it.fieldId == rule.dependsOnFieldId }
            ?.fieldName ?: rule.dependsOnFieldId.toDisplayLabel()
        val selectedValue = values[rule.dependsOnFieldId]?.trim()?.lowercase()
        val selectedPattern = selectedValue?.let(rule.patternsByValue::get)
        val format = selectedPattern?.let(::describePattern)
        if (format != null) {
            add("${selectedValue?.toDisplayLabel() ?: "Selected option"} format: $format.")
        } else {
            add("Format depends on selected $dependencyName.")
        }
    }
    if (field.validationRule.any { it is MustBeTrue }) {
        add("Confirm this item to continue.")
    }

    field.validationRule.filterIsInstance<RequiredIf>()
        .groupBy { it.dependsOnFieldId }
        .forEach { (dependencyId, rules) ->
            val dependencyName = fields.firstOrNull { it.fieldId == dependencyId }
                ?.fieldName ?: dependencyId.toDisplayLabel()
            val expectedValues = rules.map { it.expectedValue }.distinct().sorted()
            add("Required when $dependencyName is ${expectedValues.joinToString(" or ") { it.toDisplayLabel() }}.")
        }
}.distinct()

private fun describePattern(pattern: String): String? = when (pattern) {
    "^\\d{7,9}$" -> "7 to 9 digits, e.g. 12345678"
    "^[A-Za-z]\\d{7}$" -> "One letter followed by 7 digits, e.g. A1234567"
    "^[A-Za-z0-9]{5,20}$" -> "5 to 20 letters or numbers"
    "^[A-Za-z0-9-]{5,30}$" -> "5 to 30 letters, numbers, or hyphens"
    else -> null
}

private fun fieldActionLabel(field: FormField): String = when (field.fieldId) {
    "terms_and_conditions_acceptance" -> "Customer has read and accepted the terms"
    "selfie_liveness_check" -> "Liveness verification check passed"
    "face_match_verification" -> "Face match identity check passed"
    else -> "Confirm ${field.fieldName.lowercase()}"
}

private fun String.toDisplayLabel(): String =
    replace('_', ' ').replaceFirstChar { it.uppercase() }

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
            onSubmit = { onResult ->
                onResult(Result.success(Unit))
                true
            },
            canContinue = true
        )
    }
}