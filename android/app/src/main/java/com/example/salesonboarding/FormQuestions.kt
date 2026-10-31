package com.example.salesonboarding

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

// region Questions

/**
 * One question: its name (a `*` if required, "Optional" if not), one line saying what it
 * means, then the box to answer in. A wrong answer is explained under the box.
 */
@Composable
internal fun QuestionBlock(
    field: FormField,
    allFields: List<FormField>,
    state: LiveValidationState,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onCheck: () -> Unit
) {
    val c = Sm.colors
    val value = state.values[field.fieldId].orEmpty()
    val required = field.isRequiredFor(state.values)
    val error = errorFor(field, state)
    val isValid = field.isAnsweredIn(state)
    val choices = field.validationRules.filterIsInstance<OneOf>().flatMap { it.choices }.distinct()
    val isConfirm = field.validationRules.any { it is MustBeTrue }
    // A choice or a tick is a finished answer, so it is checked straight away.
    val choose: (String) -> Unit = {
        onValueChange(it)
        onCheck()
    }

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                buildAnnotatedString {
                    append(field.fieldName)
                    if (required) withStyle(SpanStyle(color = c.cherry)) { append(" *") }
                },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = c.ink,
                modifier = Modifier.weight(1f)
            )
            if (!required) {
                Text("Optional", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        meaningfulDescription(field)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 19.sp),
                color = c.mutedStrong,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            choices.isNotEmpty() -> ChoiceInput(field, value, choices, isValid, onChoose = choose)
            isConfirm -> ConfirmInput(field, value, onChoose = choose)
            else -> TextInput(
                field = field,
                value = value,
                hasError = error != null,
                isValid = isValid,
                imeAction = imeAction,
                onValueChange = onValueChange,
                onCheck = onCheck
            )
        }
        if (error != null) {
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmIcon(R.drawable.ic_sm_alert, c.cherry, size = 16.dp)
                Text(error, style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = c.cherryDeep)
            }
        } else if (!isValid && choices.isEmpty() && !isConfirm) {
            primaryFormatHint(field, state.values, allFields)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (field.fieldId == "identification_document") {
            DocumentOcrScanner()
        }
    }
}

/** The 60 dp answer box: thin olive outline, ink when focused, cherry when wrong. */
@Composable
private fun TextInput(
    field: FormField,
    value: String,
    hasError: Boolean,
    isValid: Boolean,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onCheck: () -> Unit
) {
    val c = Sm.colors
    val focusManager = LocalFocusManager.current
    var hadFocus by remember(field.fieldId) { mutableStateOf(false) }
    var focused by remember(field.fieldId) { mutableStateOf(false) }
    val multiline = field.fieldId.contains("address") || field.fieldId.contains("notes")
    val shape = RoundedCornerShape(14.dp)
    val borderColor = when {
        hasError -> c.cherry
        focused -> c.ink
        else -> c.switchOff
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (multiline) 84.dp else 60.dp)
            .background(c.white, shape)
            .border(if (focused || hasError) 2.dp else 1.5.dp, borderColor, shape)
            .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    fieldPlaceholder(field),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal),
                    color = c.muted.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        // Check the answer when the agent leaves the box, never while typing.
                        if (hadFocus && !focus.isFocused) onCheck()
                        hadFocus = focus.isFocused
                        focused = focus.isFocused
                    }
                    .semantics { contentDescription = field.fieldName },
                singleLine = !multiline,
                minLines = if (multiline) 2 else 1,
                maxLines = if (multiline) 4 else 1,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, color = c.ink),
                cursorBrush = SolidColor(c.ink),
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
        if (isValid) Tick()
    }
}

/** The small ink circle with a lime tick that marks an accepted answer. */
@Composable
private fun Tick() {
    val c = Sm.colors
    Box(
        Modifier
            .size(32.dp)
            .background(c.ink, CircleShape)
            .semantics { contentDescription = "Answer accepted" },
        contentAlignment = Alignment.Center
    ) { SmIcon(R.drawable.ic_sm_check, c.lime, size = 16.dp) }
}

/** Two or more fixed answers: side by side for two, stacked up to four, a dropdown beyond. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChoiceInput(
    field: FormField,
    value: String,
    choices: List<String>,
    isValid: Boolean,
    onChoose: (String) -> Unit
) {
    val c = Sm.colors
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
            val shape = RoundedCornerShape(14.dp)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .background(c.white, shape)
                        .border(if (expanded) 2.dp else 1.5.dp, if (expanded) c.ink else c.switchOff, shape)
                        .padding(start = 18.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (value.isBlank()) "Choose an option" else value.toDisplayLabel(),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                        color = if (value.isBlank()) c.muted.copy(alpha = 0.7f) else c.ink,
                        modifier = Modifier.weight(1f)
                    )
                    if (isValid) Tick() else SmIcon(R.drawable.ic_sm_chevron_down, c.ink)
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = c.white
                ) {
                    choices.sorted().forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.toDisplayLabel(), style = MaterialTheme.typography.bodyLarge) },
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
    val c = Sm.colors
    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(choice) }),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) c.ink else c.white,
        border = BorderStroke(1.5.dp, if (selected) c.ink else c.switchOff)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                choice.toDisplayLabel(),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = if (selected) c.cream else c.ink,
                modifier = Modifier.weight(1f)
            )
            if (selected) SmIcon(R.drawable.ic_sm_check, c.lime, size = 20.dp)
        }
    }
}

/** A statement the customer must confirm, such as accepting the terms. */
@Composable
private fun ConfirmInput(field: FormField, value: String, onChoose: (String) -> Unit) {
    val c = Sm.colors
    val focusManager = LocalFocusManager.current
    val checked = value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Checkbox) {
                focusManager.clearFocus()
                onChoose(if (checked) "false" else "true")
            },
        shape = RoundedCornerShape(16.dp),
        color = c.cream,
        border = BorderStroke(1.5.dp, c.switchOff)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(c.ink, RoundedCornerShape(9.dp))
                    .padding(2.dp)
                    .background(if (checked) c.ink else c.white, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) { if (checked) SmIcon(R.drawable.ic_sm_check, c.lime, size = 17.dp) }
            Text(
                fieldActionLabel(field),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = c.ink
            )
        }
    }
}

// endregion

// region Reading text from a document photo

/**
 * "Read from photo": on the identification document question, the agent can choose a photo
 * of the document and the phone reads its printed text (Google ML Kit, on the phone). The
 * text is shown to compare with the card; it is never put into an answer, uploaded or saved.
 */
@Composable
fun DocumentOcrScanner(
    modifier: Modifier = Modifier
) {
    val c = Sm.colors
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

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .heightIn(min = 44.dp)
                .clickable(enabled = !isScanning, role = Role.Button) { imagePicker.launch("image/*") },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmIcon(R.drawable.ic_sm_id_card, c.ink, size = 20.dp)
            Text(
                if (isScanning) "Reading…" else "Read from photo",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline
                ),
                color = c.ink
            )
        }
        Text(
            "Read on this phone. Not uploaded or saved. It does not check identity.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted
        )
        errorMessage?.let { Banner(it, Tone.Negative, modifier = Modifier.padding(top = 8.dp)) }
        if (extractedText.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = c.cream,
                border = BorderStroke(1.dp, c.line)
            ) {
                Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text("Text found. Check it against the card.", style = MaterialTheme.typography.titleSmall)
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
