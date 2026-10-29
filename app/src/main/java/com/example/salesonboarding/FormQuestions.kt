package com.example.salesonboarding

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

// region Questions

@Composable
internal fun QuestionCard(
    field: FormField,
    allFields: List<FormField>,
    state: LiveValidationState,
    isSuggested: Boolean,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onCheck: () -> Unit
) {
    val value = state.values[field.fieldId].orEmpty()
    val required = field.isRequiredFor(state.values)
    val error = errorFor(field, state)
    val isValid = field.isAnsweredIn(state)
    val choices = field.validationRules.filterIsInstance<OneOf>().flatMap { it.choices }.distinct()
    // A choice or a tick is a finished answer, so it is checked straight away.
    val choose: (String) -> Unit = {
        onValueChange(it)
        onCheck()
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(
                if (required) "${field.fieldName} *" else field.fieldName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (isSuggested) StatusPill("Suggested next", Tone.Ai, showIcon = true)
            if (!required) StatusPill("Optional", Tone.Neutral)
        }
        meaningfulDescription(field)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            choices.isNotEmpty() -> ChoiceInput(field, value, choices, isValid, onChoose = choose)
            field.validationRules.any { it is MustBeTrue } -> ConfirmInput(field, value, onChoose = choose)
            else -> TextInput(
                field = field,
                value = value,
                error = error,
                isValid = isValid,
                hint = primaryFormatHint(field, state.values, allFields),
                imeAction = imeAction,
                onValueChange = onValueChange,
                onCheck = onCheck
            )
        }
        // Text inputs show their error in the box; choices and ticks show it here.
        if (error != null && (choices.isNotEmpty() || field.validationRules.any { it is MustBeTrue })) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (field.fieldId == "identification_document") {
            DocumentOcrScanner()
        }
    }
}

@Composable
private fun TextInput(
    field: FormField,
    value: String,
    error: String?,
    isValid: Boolean,
    hint: String?,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onCheck: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var hadFocus by remember(field.fieldId) { mutableStateOf(false) }
    val multiline = field.fieldId.contains("address") || field.fieldId.contains("notes")
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                // Check the answer when the agent leaves the box, never while typing.
                if (hadFocus && !focus.isFocused) onCheck()
                hadFocus = focus.isFocused
            },
        placeholder = { Text(fieldPlaceholder(field)) },
        shape = MaterialTheme.shapes.medium,
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        maxLines = if (multiline) 4 else 1,
        isError = error != null,
        trailingIcon = when {
            isValid -> {
                { Icon(Icons.Filled.CheckCircle, "Answer accepted", tint = MaterialTheme.colorScheme.primary) }
            }
            error != null -> {
                { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) }
            }
            else -> null
        },
        supportingText = when {
            error != null -> {
                { Text(error) }
            }
            hint != null -> {
                { Text(hint) }
            }
            else -> null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = when (field.fieldType.lowercase(Locale.ROOT)) {
                "number" -> KeyboardType.Decimal
                "email" -> KeyboardType.Email
                "phone" -> KeyboardType.Phone
                else -> KeyboardType.Text
            },
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() }
        )
    )
}

/** Up to four options as large tap targets (two side by side), more as a dropdown. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChoiceInput(
    field: FormField,
    value: String,
    choices: List<String>,
    isValid: Boolean,
    onChoose: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val pick: (String) -> Unit = {
        focusManager.clearFocus() // checks a box still being typed in first
        onChoose(it)
    }
    when {
        choices.size == 2 -> Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            choices.forEach { ChoiceOption(it, value.equals(it, ignoreCase = true), pick, Modifier.weight(1f)) }
        }
        choices.size <= 4 -> Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            choices.forEach { ChoiceOption(it, value.equals(it, ignoreCase = true), pick, Modifier.fillMaxWidth()) }
        }
        else -> {
            var expanded by remember(field.fieldId) { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = if (value.isBlank()) "" else value.toDisplayLabel(),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    placeholder = { Text("Select an option") },
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = {
                        if (isValid) Icon(Icons.Filled.CheckCircle, "Answer accepted", tint = MaterialTheme.colorScheme.primary)
                        else ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    choices.sorted().forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.toDisplayLabel()) },
                            onClick = {
                                expanded = false
                                pick(choice)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceOption(choice: String, selected: Boolean, onPick: (String) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(choice) }),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) scheme.primary else scheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                choice.toDisplayLabel(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                modifier = Modifier.padding(start = Space.xs)
            )
        }
    }
}

/** A statement the customer must confirm, such as accepting the terms. */
@Composable
private fun ConfirmInput(field: FormField, value: String, onChoose: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val checked = value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Checkbox) {
                focusManager.clearFocus()
                onChoose(if (checked) "false" else "true")
            },
        shape = MaterialTheme.shapes.medium,
        color = if (checked) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(if (checked) 1.5.dp else 1.dp, if (checked) scheme.primary else scheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(
                fieldActionLabel(field),
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked) scheme.onPrimaryContainer else scheme.onSurface,
                modifier = Modifier.padding(start = Space.xs)
            )
        }
    }
}

// endregion

// region Reading text from a document photo

@Composable
fun DocumentOcrScanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    var isScanning by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isDisposed = remember(recognizer) { AtomicBoolean(false) }

    DisposableEffect(recognizer) {
        onDispose {
            isDisposed.set(true)
            recognizer.close()
        }
    }

    fun reportScanError(message: String) {
        if (!isDisposed.get()) {
            isScanning = false
            errorMessage = message
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && !isDisposed.get()) {
            isScanning = true
            extractedText = ""
            errorMessage = null
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (!isDisposed.get()) {
                            extractedText = result.text.trim()
                            if (extractedText.isBlank()) {
                                errorMessage = "No readable text found. Try a clear, well-lit document photo."
                            }
                        }
                    }
                    .addOnFailureListener {
                        if (!isDisposed.get()) {
                            errorMessage = "Could not read this image. Try a clearer document photo."
                        }
                    }
                    .addOnCompleteListener {
                        if (!isDisposed.get()) {
                            isScanning = false
                        }
                    }
            } catch (_: IOException) {
                reportScanError("Could not open this image. Choose another document photo.")
            } catch (_: SecurityException) {
                reportScanError("Could not access this image. Choose another document photo.")
            } catch (_: IllegalArgumentException) {
                reportScanError("This image could not be opened. Choose another document photo.")
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.s)
    ) {
        SecondaryButton(
            text = if (isScanning) "Reading document…" else "Read text from a document photo",
            onClick = { imagePicker.launch("image/*") },
            enabled = !isScanning,
            icon = Icons.Filled.AccountBox
        )
        Text(
            "Read on this phone. Nothing is uploaded or saved automatically. Compare the result with the original document; reading the text does not verify identity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        errorMessage?.let { Banner(it, Tone.Negative) }
        if (extractedText.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text("Text found · check before using", style = MaterialTheme.typography.titleSmall)
                    SelectionContainer {
                        Text(extractedText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// endregion

// region Helpers

internal fun FormField.isAnsweredIn(state: LiveValidationState): Boolean =
    !state.values[fieldId].isNullOrBlank() && state.results[fieldId]?.isValid == true

internal fun errorFor(field: FormField, state: LiveValidationState): String? =
    ValidationErrorDisplayMapper.forResult(
        fieldId = field.fieldId,
        result = state.results[field.fieldId],
        touched = field.fieldId in state.touchedFieldIds
    )?.message

/** The answer as the customer would say it: choice labels, "Confirmed" for a tick. */
internal fun displayAnswer(field: FormField, value: String): String = when {
    field.validationRules.any { it is MustBeTrue } ->
        if (value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)) "Confirmed"
        else "Not confirmed"
    field.validationRules.any { it is OneOf } -> value.toDisplayLabel()
    else -> value
}

/** The field's description, unless it only repeats the field's name. */
private fun meaningfulDescription(field: FormField): String? = field.description.takeIf {
    val name = field.fieldName.lowercase(Locale.ROOT)
    it.isNotBlank() &&
        !it.equals("Enter the $name.", ignoreCase = true) &&
        !it.equals("Enter or choose the $name for this customer.", ignoreCase = true)
}

private fun fieldPlaceholder(field: FormField): String = when {
    field.validationRules.any { it is EmailFormat } -> "name@example.com"
    field.validationRules.any { it is KenyanPhoneFormat } -> "0712 345 678 or +254..."
    field.validationRules.any { it is DateFormatAndMinimumAge } -> "YYYY-MM-DD"
    field.fieldId.contains("dob") || field.fieldId.contains("date_of_birth") -> "YYYY-MM-DD"
    field.fieldType.equals("number", ignoreCase = true) -> "e.g. 50000"
    field.fieldId.contains("address") -> "Street, building, or area"
    else -> "Enter ${field.fieldName.lowercase(Locale.ROOT)}"
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
    val choices = field.validationRules.filterIsInstance<OneOf>()
        .flatMap { it.choices }
        .distinct()
        .sorted()
    if (choices.isNotEmpty()) {
        add("Available choices: ${choices.joinToString { it.toDisplayLabel() }}.")
    }

    if (field.validationRules.any { it is EmailFormat }) {
        add("Format: name@example.com")
    }
    if (field.validationRules.any { it is KenyanPhoneFormat }) {
        add("Format: 07XXXXXXXX, 01XXXXXXXX, +2547XXXXXXXX or +2541XXXXXXXX")
    }
    field.validationRules.filterIsInstance<DateFormatAndMinimumAge>().firstOrNull()?.let { rule ->
        add("Format: YYYY-MM-DD (Minimum age: ${rule.minimumAge})")
    }
    if (field.validationRules.any { it is PositiveNumber }) {
        add("Must be a positive number greater than zero")
    }
    field.validationRules.filterIsInstance<RegexPattern>()
        .map { it.message }
        .filter { it != "Enter a value in the required format." }
        .distinct()
        .forEach(::add)

    field.validationRules.filterIsInstance<PatternByFieldValue>().forEach { rule ->
        val dependencyName = fields.firstOrNull { it.fieldId == rule.dependsOnFieldId }
            ?.fieldName ?: rule.dependsOnFieldId.toDisplayLabel()
        val selectedValue = values[rule.dependsOnFieldId]?.trim()?.lowercase(Locale.ROOT)
        val selectedPattern = selectedValue?.let(rule.patternsByValue::get)
        val format = selectedPattern?.let(::describePattern)
        if (format != null) {
            add("${selectedValue?.toDisplayLabel() ?: "Selected option"} format: $format.")
        } else {
            add("Format depends on selected $dependencyName.")
        }
    }
    if (field.validationRules.any { it is MustBeTrue }) {
        add("Confirm this item to continue.")
    }

    field.validationRules.filterIsInstance<RequiredIf>()
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
    else -> "Confirm ${field.fieldName.lowercase(Locale.ROOT)}"
}

private fun String.toDisplayLabel(): String =
    replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }

@Composable
internal fun rememberNetworkStatus(): Boolean {
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

// endregion
