package com.example.salesonboarding

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
    modifier: Modifier = Modifier
) {
    val c = Sm.colors
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
                Box(Modifier.fillMaxWidth().height(130.dp)) {
                    Image(
                        painter = painterResource(R.drawable.home_photo),
                        contentDescription = null, // decorative
                        contentScale = ContentScale.Crop,
                        alignment = BiasAlignment(horizontalBias = 0f, verticalBias = 0.24f), // 50% across, 62% down
                        modifier = Modifier.matchParentSize()
                    )
                    Box(Modifier.matchParentSize().background(c.window.copy(alpha = 0.18f)))
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
    var selectedContext by remember { mutableStateOf<String?>(null) }
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
                        if (selectedIndustry != id) selectedContext = null
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
    "Document photos" to "If you choose a document photo, text is read on this device to help with data entry. The photo and scan result are not uploaded or saved automatically.",
    "Customer rights" to "The customer may request review of their submitted details through authorized company channels.",
    "Access and retention" to "Answers are saved to your company workspace as you go, so you can resume an unfinished session. Ask your administrator about access and retention."
)

@Composable
fun OnboardingConsentScreen(
    onConsent: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Sm.colors
    var confirmed by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    AppScreen(
        title = "Customer consent",
        subtitle = "Read aloud to the customer",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .toggleable(value = confirmed, role = Role.Checkbox) { confirmed = it },
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
                            .background(if (confirmed) c.ink else c.white, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) { if (confirmed) SmIcon(R.drawable.ic_sm_check, c.lime, size = 17.dp) }
                    Text(
                        "Customer has been informed and agrees to proceed",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = c.ink
                    )
                }
            }
            PrimaryButton("Start onboarding", onClick = onConsent, enabled = confirmed)
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
            FULL_CONSENT_TERMS.forEach { (title, text) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = c.mutedStrong)
                }
            }
        }
    }
}

// endregion
