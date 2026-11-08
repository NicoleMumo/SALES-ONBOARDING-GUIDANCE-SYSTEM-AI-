package com.example.salesonboarding

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// region Agent home

@Composable
fun AgentHomeScreen(
    onStartOnboarding: () -> Unit,
    onViewSessions: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    /** The agent's most recent unfinished session, offered as one tap to continue. */
    unfinishedSession: StoredOnboardingSession? = null,
    onContinueSession: (StoredOnboardingSession) -> Unit = {},
    /** Marks that session abandoned, after the agent confirms. */
    onCancelSession: (StoredOnboardingSession) -> Unit = {}
) {
    val c = Sm.colors
    var confirmCancel by remember { mutableStateOf<StoredOnboardingSession?>(null) }
    val guideMoving = LocalContext.current
        .getSharedPreferences(LAST_CHOICE_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean("guide_moving", true)
    AppScreen(
        title = "SalesMesh",
        leading = { AppMark() },
        modifier = modifier,
        actions = { HeaderButton(R.drawable.ic_sm_sign_out, "Sign out", onSignOut) }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = c.ink
        ) {
            Column {
                Box(Modifier.fillMaxWidth().height(if (isShortScreen()) 150.dp else 190.dp)) {
                    Image(
                        painter = painterResource(R.drawable.home_photo),
                        contentDescription = null, // decorative
                        contentScale = ContentScale.Crop,
                        // The laptop and the mouse on the desk, not just the keyboard.
                        alignment = BiasAlignment(horizontalBias = 0.1f, verticalBias = -0.1f),
                        modifier = Modifier.matchParentSize()
                    )
                    // The photo fades into the card, so the headline reads as part of it.
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to c.ink))
                    )
                    // The guide waits on the photo, ready for the next customer.
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 18.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(54.dp).background(c.ink, CircleShape), contentAlignment = Alignment.Center) {
                            GuideMascot(GuideMood.HAPPY, Modifier.size(46.dp), animated = guideMoving)
                        }
                        Text(
                            "Ready when you are.",
                            style = MaterialTheme.typography.titleSmall,
                            color = c.cream
                        )
                    }
                }
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("Make every first conversation ")
                            withStyle(SpanStyle(color = c.lime)) { append("count.") }
                        },
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 36.sp),
                        color = c.cream
                    )
                    PrimaryButton("Start onboarding", onClick = onStartOnboarding)
                }
            }
        }
        unfinishedSession?.let { session ->
            val type = CUSTOMER_TYPES[session.context]?.title ?: session.context.replaceFirstChar { it.titlecase(Locale.ROOT) }
            val name = session.entries.firstOrNull { it.fieldId == "full_name" || it.fieldId == "business_name" }
                ?.value?.takeIf(String::isNotBlank)
            Surface(
                onClick = { onContinueSession(session) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                shape = RoundedCornerShape(16.dp),
                color = c.cream,
                border = BorderStroke(1.5.dp, c.switchOff)
            ) {
                Row(
                    Modifier.padding(start = 22.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Continue last session", style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(name, type).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.mutedStrong
                        )
                        Text(
                            "Cancel session",
                            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                            color = c.cherryDeep,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable(role = Role.Button) { confirmCancel = session }
                        )
                    }
                    Box(Modifier.size(44.dp).background(c.lime, CircleShape), contentAlignment = Alignment.Center) {
                        SmIcon(R.drawable.ic_sm_arrow, c.ink, size = 20.dp)
                    }
                }
            }
        }
        confirmCancel?.let { session ->
            AlertDialog(
                onDismissRequest = { confirmCancel = null },
                title = { Text("Cancel this session?") },
                text = { Text("It will be marked abandoned and can no longer be continued. Unfinished sessions are also cancelled after 48 hours untouched.") },
                confirmButton = {
                    TextButton(onClick = { confirmCancel = null; onCancelSession(session) }) { Text("Cancel session") }
                },
                dismissButton = { TextButton(onClick = { confirmCancel = null }) { Text("Keep it") } }
            )
        }
        Surface(
            onClick = onViewSessions,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            shape = RoundedCornerShape(16.dp),
            color = c.white,
            border = BorderStroke(1.5.dp, c.border)
        ) {
            Row(
                Modifier.padding(start = 22.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My sessions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).background(c.ink, CircleShape), contentAlignment = Alignment.Center) {
                    SmIcon(R.drawable.ic_sm_chevron_right, c.lime, size = 20.dp)
                }
            }
        }
    }
}

// endregion

// region New customer

object IndustrySelectionValidator {
    val supportedIndustries: Set<String> = setOf("telecom", "banking")

    fun validate(industry: String?): String? {
        if (industry.isNullOrBlank()) return "Select an onboarding industry."
        return if (industry in supportedIndustries) {
            null
        } else {
            "Select a valid onboarding industry."
        }
    }

    fun contextsFor(industry: String?): List<String> = when (industry) {
        "banking" -> listOf("individual", "business")
        "telecom" -> listOf("mobile", "broadband", "tv", "iot", "landline")
        else -> emptyList()
    }

    fun validateContext(industry: String?, context: String?): String? =
        if (context != null && context in contextsFor(industry)) null
        else "Select a valid customer context."
}

private const val LAST_CHOICE_PREFERENCES = "salesmesh_form"
private const val LAST_CONTEXT_KEY_PREFIX = "last_context_"

private data class CustomerType(val title: String, val detail: String, @DrawableRes val icon: Int)

private val CUSTOMER_TYPES = mapOf(
    "individual" to CustomerType("Individual", "Person opening an account", R.drawable.ic_sm_agents),
    "business" to CustomerType("Business", "Company or organisation", R.drawable.ic_sm_company),
    "mobile" to CustomerType("Mobile", "A mobile phone line", R.drawable.ic_sm_phone),
    "broadband" to CustomerType("Broadband", "Home or office internet", R.drawable.ic_sm_wifi),
    "tv" to CustomerType("TV", "TV and streaming", R.drawable.ic_sm_tv),
    "iot" to CustomerType("IoT", "A SIM for a connected device", R.drawable.ic_sm_chip),
    "landline" to CustomerType("Landline", "A fixed phone line", R.drawable.ic_sm_landline)
)

@Composable
fun IndustrySelectionScreen(
    onIndustrySelected: (String, String) -> Unit,
    assignedIndustry: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedIndustry by remember(assignedIndustry) { mutableStateOf(assignedIndustry) }
    // The customer type chosen last time for this industry is picked again, to save a tap.
    val preferences = LocalContext.current.getSharedPreferences(LAST_CHOICE_PREFERENCES, Context.MODE_PRIVATE)
    fun lastContextFor(industry: String?): String? =
        industry?.let { preferences.getString("$LAST_CONTEXT_KEY_PREFIX$it", null) }
            ?.takeIf { it in IndustrySelectionValidator.contextsFor(industry) }
    var selectedContext by remember(selectedIndustry) { mutableStateOf(lastContextFor(selectedIndustry)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AppScreen(
        title = "New customer",
        subtitle = selectedIndustry?.displayIndustryName() ?: "Choose an industry",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            PrimaryButton(
                "Continue",
                onClick = {
                    val validationError = IndustrySelectionValidator.validate(selectedIndustry)
                        ?: IndustrySelectionValidator.validateContext(selectedIndustry, selectedContext)
                    if (validationError == null) {
                        preferences.edit()
                            .putString("$LAST_CONTEXT_KEY_PREFIX$selectedIndustry", selectedContext)
                            .apply()
                        onIndustrySelected(requireNotNull(selectedIndustry), requireNotNull(selectedContext))
                    } else {
                        errorMessage = validationError
                    }
                },
                enabled = selectedIndustry != null && selectedContext != null
            )
        }
    ) {
        if (assignedIndustry == null) {
            Text("INDUSTRY", style = smCapsStyle())
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf("banking" to "Banking", "telecom" to "Telecommunications").forEach { (id, label) ->
                    SmChip(label, selected = selectedIndustry == id, onClick = {
                        selectedIndustry = id
                        errorMessage = null
                    })
                }
            }
        }
        if (selectedIndustry != null) {
            Text("CUSTOMER TYPE", style = smCapsStyle())
            IndustrySelectionValidator.contextsFor(selectedIndustry).forEach { context ->
                val type = CUSTOMER_TYPES[context]
                    ?: CustomerType(context.replaceFirstChar { it.titlecase(Locale.ROOT) }, "", R.drawable.ic_sm_info)
                TypeCard(type, selected = selectedContext == context) {
                    selectedContext = context
                    errorMessage = null
                }
            }
        }
        errorMessage?.let { Banner(it, Tone.Negative) }
    }
}

/** One big choice: ink when picked, with a lime badge and a ticked circle. */
@Composable
private fun TypeCard(type: CustomerType, selected: Boolean, onSelect: () -> Unit) {
    val c = Sm.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = RoundedCornerShape(30.dp),
        color = if (selected) c.ink else c.white,
        border = BorderStroke(1.5.dp, if (selected) c.ink else c.border)
    ) {
        Row(
            Modifier.padding(start = 18.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.size(56.dp).background(if (selected) c.lime else c.limeSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) { SmIcon(type.icon, c.ink, size = 26.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    type.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                    color = if (selected) c.cream else c.ink
                )
                if (type.detail.isNotEmpty()) {
                    Text(
                        type.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) c.cream.copy(alpha = 0.82f) else c.mutedStrong
                    )
                }
            }
            Box(
                Modifier
                    .size(30.dp)
                    .background(if (selected) c.lime else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .then(Modifier.background(androidx.compose.ui.graphics.Color.Transparent, CircleShape)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) SmIcon(R.drawable.ic_sm_check, c.ink, size = 16.dp)
                else Box(Modifier.size(30.dp).background(c.white, CircleShape).padding(2.dp).background(c.ink, CircleShape).padding(2.dp).background(c.white, CircleShape))
            }
        }
    }
}

private fun String.displayIndustryName(): String =
    when (lowercase(Locale.ROOT)) {
        "telecom" -> "Telecommunications"
        "banking" -> "Banking"
        else -> replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

@Preview(name = "New customer", showBackground = true, heightDp = 844)
@Composable
private fun IndustrySelectionPreview() {
    SalesOnboardingTheme {
        IndustrySelectionScreen(onIndustrySelected = { _, _ -> }, assignedIndustry = "banking", onBack = {})
    }
}

// endregion

// region Customer consent

/** The four things the agent reads aloud: a title and one short line each. */
private data class ConsentPoint(@DrawableRes val icon: Int, val title: String, val line: String)

private val CONSENT_POINTS = listOf(
    ConsentPoint(R.drawable.ic_sm_info, "Why we collect it", "To set up the account."),
    ConsentPoint(R.drawable.ic_sm_id_card, "Document photos", "Read on this phone. Never uploaded."),
    ConsentPoint(R.drawable.ic_sm_agents, "Customer rights", "They can ask to review their details."),
    ConsentPoint(R.drawable.ic_sm_lock, "Access and retention", "Saved securely.")
)

/** The complete wording, available behind "Read the full terms". */
private val FULL_CONSENT_TERMS = listOf(
    "Purpose of Collection" to "Information is collected to set up the customer account and follow your company's onboarding process.",
    "Document photos" to "If the agent takes a document photo, its text is read on this device to help with data entry. The agent may use the name, ID number or date of birth it found as answers, after checking them. The photo is deleted after reading, and neither the photo nor the rest of the text is uploaded or saved.",
    "Address search" to "If the agent taps Find address or Use my location, the address typed so far, or the phone's location, is sent to OpenStreetMap's address service to suggest the full address. Nothing else is sent, and the agent can type the address instead.",
    "Customer rights" to "The customer may request review of their submitted details through authorized company channels.",
    "Access and retention" to "Answers are saved to your company workspace as you go, so you can resume an unfinished session. Ask your administrator about access and retention."
)

/** Shown in the full terms only when the company allows it; agreeing is optional. */
private val TRAINING_TERMS = "Improving the system" to
    "With the customer's agreement, an anonymised copy of the session's steps is kept to improve the app: which questions were checked, whether each passed, and when. It never includes answers, names or contact details. Saying no does not change the onboarding."

@Composable
fun OnboardingConsentScreen(
    onConsent: (trainingAgreed: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The company allows anonymised steps to improve the system, so the customer is asked. */
    askTrainingConsent: Boolean = false
) {
    val c = Sm.colors
    var confirmed by remember { mutableStateOf(false) }
    var trainingAgreed by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    AppScreen(
        title = "Customer consent",
        subtitle = "Read aloud to the customer",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            if (askTrainingConsent) {
                ConsentTick(
                    checked = trainingAgreed,
                    onCheckedChange = { trainingAgreed = it },
                    text = "Optional: customer agrees that anonymised steps may be used to improve the system"
                )
            }
            ConsentTick(
                checked = confirmed,
                onCheckedChange = { confirmed = it },
                text = "Customer has been informed and agrees to proceed"
            )
            PrimaryButton(
                "Start onboarding",
                onClick = { onConsent(askTrainingConsent && trainingAgreed) },
                enabled = confirmed
            )
        }
    ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            CONSENT_POINTS.forEachIndexed { index, point ->
                if (index > 0) HorizontalDivider(color = c.divider)
                Row(Modifier.padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        SmIcon(point.icon, c.ink, size = 26.dp)
                    }
                    Column {
                        Text(point.title, style = MaterialTheme.typography.titleMedium)
                        Text(point.line, style = MaterialTheme.typography.bodyMedium, color = c.mutedStrong)
                    }
                }
            }
            TextButton(onClick = { showTerms = true }) {
                Text(
                    "Read the full terms",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = c.ink
                )
            }
        }
    }

    if (showTerms) {
        SmSheet(title = "Full terms", onDismiss = { showTerms = false }) {
            (if (askTrainingConsent) FULL_CONSENT_TERMS + TRAINING_TERMS else FULL_CONSENT_TERMS).forEach { (title, text) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = c.mutedStrong)
                }
            }
        }
    }
}

/** A large tick box with its sentence, the whole row tappable. */
@Composable
private fun ConsentTick(checked: Boolean, onCheckedChange: (Boolean) -> Unit, text: String) {
    val c = Sm.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        shape = RoundedCornerShape(16.dp),
        color = c.cream,
        border = BorderStroke(1.5.dp, c.switchOff)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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
                text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = c.ink
            )
        }
    }
}

// endregion
