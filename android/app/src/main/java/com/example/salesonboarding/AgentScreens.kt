package com.example.salesonboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Locale

// region Agent home

@Composable
fun AgentHomeScreen(
    onStartOnboarding: () -> Unit,
    onViewSessions: () -> Unit,
    onSignOut: () -> Unit,
    companyIndustry: String? = null,
    modifier: Modifier = Modifier
) {
    AppScreen(
        title = "SalesMesh",
        subtitle = companyIndustry?.let { "${it.displayIndustryName()} onboarding" } ?: "Sales workspace",
        modifier = modifier,
        actions = {
            IconButton(onClick = onSignOut) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                AppMark(size = 48.dp)
                Text(
                    "Make every first conversation count.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Choose the customer type, go through each section with the customer, then review and submit.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                PrimaryButton(
                    "Start customer onboarding",
                    onClick = onStartOnboarding,
                    icon = Icons.AutoMirrored.Filled.ArrowForward
                )
            }
        }
        SectionCard(
            title = "Your sessions",
            subtitle = "Customers you have onboarded, and unfinished sessions you can continue."
        ) {
            SecondaryButton("View my sessions", onClick = onViewSessions, icon = Icons.AutoMirrored.Filled.List)
        }
        SectionCard(title = "How it works") {
            WorkflowStep(1, "Choose the customer type", "The right form opens for your company and the customer.")
            WorkflowStep(2, "Go section by section", "Answers are checked when you leave a box. The AI suggests what to ask next.")
            WorkflowStep(3, "Review and submit", "Check every answer with the customer before saving.")
        }
        Text(
            "Need help with your account? Contact your administrator.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkflowStep(number: Int, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun String.displayIndustryName(): String =
    when (lowercase(Locale.ROOT)) {
        "telecom" -> "Telecommunications"
        "banking" -> "Banking"
        else -> replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

// endregion

// region Form choice

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

/** What each customer type is called and means, for the agent. */
private val CONTEXT_LABELS = mapOf(
    "individual" to ("Individual" to "A person opening an account"),
    "business" to ("Business" to "A company or organisation"),
    "mobile" to ("Mobile" to "A mobile phone line"),
    "broadband" to ("Broadband" to "Home or office internet"),
    "tv" to ("TV" to "TV and streaming"),
    "iot" to ("IoT" to "A SIM for a connected device"),
    "landline" to ("Landline" to "A fixed phone line")
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
        subtitle = "Choose the form for this customer.",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            Text(
                "Question suggestions are worked out on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                enabled = selectedIndustry != null && selectedContext != null,
                icon = Icons.AutoMirrored.Filled.ArrowForward
            )
        }
    ) {
        if (assignedIndustry == null) {
            SectionCard(title = "Industry") {
                listOf("banking" to "Banking", "telecom" to "Telecommunications").forEach { (id, label) ->
                    OptionRow(label, null, selectedIndustry == id) {
                        if (selectedIndustry != id) selectedContext = null
                        selectedIndustry = id
                        errorMessage = null
                    }
                }
            }
        } else {
            StatusPill("${assignedIndustry.displayIndustryName()} · set by your company", Tone.Neutral)
        }
        if (selectedIndustry != null) {
            SectionCard(title = "Customer type") {
                IndustrySelectionValidator.contextsFor(selectedIndustry).forEach { context ->
                    val (label, detail) = CONTEXT_LABELS[context]
                        ?: (context.replaceFirstChar { it.titlecase(Locale.ROOT) } to null)
                    OptionRow(label, detail, selectedContext == context) {
                        selectedContext = context
                        errorMessage = null
                    }
                }
            }
        }
        errorMessage?.let { Banner(it, Tone.Negative) }
    }
}

@Composable
private fun OptionRow(title: String, detail: String?, selected: Boolean, onSelect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) scheme.primary else scheme.outline)
    ) {
        Row(Modifier.padding(horizontal = Space.m, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = Space.s)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) scheme.onPrimaryContainer else scheme.onSurface)
                detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Preview(name = "Form choice", showBackground = true)
@Composable
private fun IndustrySelectionPreview() {
    SalesOnboardingTheme(darkTheme = false) {
        IndustrySelectionScreen(onIndustrySelected = { _, _ -> }, assignedIndustry = "telecom", onBack = {})
    }
}

// endregion

// region Customer consent

@Composable
fun OnboardingConsentScreen(
    onConsent: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmed by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    AppScreen(
        title = "Customer consent",
        subtitle = "Check the customer agrees before you collect personal details.",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            PrimaryButton(
                "Start onboarding",
                onClick = onConsent,
                enabled = confirmed,
                icon = Icons.AutoMirrored.Filled.ArrowForward
            )
        }
    ) {
        SectionCard(title = "Tell the customer") {
            ConsentPoint(
                Icons.Filled.Info,
                "Why we collect it",
                "Information is collected to set up the customer account and follow your company's onboarding process."
            )
            HorizontalDivider(color = scheme.outlineVariant)
            ConsentPoint(
                Icons.Filled.AccountBox,
                "Document photos",
                "If you choose a document photo, text is read on this device to help with data entry. The photo and scan result are not uploaded or saved automatically."
            )
            HorizontalDivider(color = scheme.outlineVariant)
            ConsentPoint(
                Icons.Filled.Person,
                "Customer rights",
                "The customer may request review of their submitted details through authorized company channels."
            )
            HorizontalDivider(color = scheme.outlineVariant)
            ConsentPoint(
                Icons.Filled.Lock,
                "Access and retention",
                "Answers are saved to your company workspace as you go, so you can resume an unfinished session. Ask your administrator about access and retention."
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = confirmed, role = Role.Checkbox) { confirmed = it },
            shape = MaterialTheme.shapes.medium,
            color = if (confirmed) scheme.primaryContainer else scheme.surface,
            border = BorderStroke(if (confirmed) 1.5.dp else 1.dp, if (confirmed) scheme.primary else scheme.outline)
        ) {
            Row(Modifier.padding(horizontal = Space.m, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = confirmed, onCheckedChange = null)
                Text(
                    "Customer has been informed and agreed to proceed",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (confirmed) scheme.onPrimaryContainer else scheme.onSurface,
                    modifier = Modifier.padding(start = Space.s)
                )
            }
        }
    }
}

@Composable
private fun ConsentPoint(icon: ImageVector, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// endregion
