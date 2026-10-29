package com.example.salesonboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "SM",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "SalesMesh",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "SALES WORKSPACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(onClick = onSignOut) {
                Text("Sign out")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                ) {
                    Text(
                        "YOUR CUSTOMER WORKSPACE",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    "Make every first conversation count.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Start a customer profile, get guided through the right questions, and review the details before saving.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (companyIndustry != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    ) {
                        Text(
                            "Company playbook · ${companyIndustry.displayIndustryName()}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onStartOnboarding,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 15.dp)
                ) {
                    Text(
                        "Start customer onboarding",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Your saved work",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Find clients you have already onboarded and check their saved session details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onViewSessions,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 13.dp)
                ) {
                    Text("View my onboarded clients")
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "A clear process, every time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            WorkflowStep(number = "1", title = "Choose the customer type", detail = "Open the matching industry and service playbook.")
            WorkflowStep(number = "2", title = "Follow the guidance", detail = "Complete each field with validation and next-step suggestions.")
            WorkflowStep(number = "3", title = "Review and save", detail = "Check the information before completing the customer session.")
        }

        Text(
            "Need help with your account? Contact your administrator.",
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkflowStep(number: String, title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier.padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

private data class IndustryOption(
    val id: String,
    val title: String
)

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

@Composable
fun IndustrySelectionScreen(
    onIndustrySelected: (String, String) -> Unit,
    assignedIndustry: String? = null,
    modifier: Modifier = Modifier
) {
    var selectedIndustry by remember(assignedIndustry) { mutableStateOf(assignedIndustry) }
    var selectedContext by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val options = listOf(
        IndustryOption(
            id = "telecom",
            title = "Telecom"
        ),
        IndustryOption(
            id = "banking",
            title = "Banking"
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                "SALESMESH",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Let’s get you set up",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Answer a few questions. We’ll guide you to the next step.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What are you signing up for?", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (assignedIndustry == null) {
                        options.forEach { option ->
                            FilterChip(
                                selected = selectedIndustry == option.id,
                                onClick = {
                                    if (selectedIndustry != option.id) selectedContext = null
                                    selectedIndustry = option.id
                                    errorMessage = null
                                },
                                label = { Text(option.title) }
                            )
                        }
                    } else {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            enabled = false,
                            label = { Text(assignedIndustry.displayIndustryName()) }
                        )
                    }
                }
            }

            if (selectedIndustry != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account type", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndustrySelectionValidator.contextsFor(selectedIndustry).forEach { context ->
                            FilterChip(
                                selected = selectedContext == context,
                                onClick = {
                                    selectedContext = context
                                    errorMessage = null
                                },
                                label = { Text(context.replaceFirstChar { it.titlecase(Locale.ROOT) }) }
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Text(
            "Field suggestions are computed on this device.",
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = {
                val validationError = IndustrySelectionValidator.validate(selectedIndustry)
                    ?: IndustrySelectionValidator.validateContext(selectedIndustry, selectedContext)
                if (validationError == null) {
                    onIndustrySelected(requireNotNull(selectedIndustry), requireNotNull(selectedContext))
                } else {
                    errorMessage = validationError
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = selectedIndustry != null && selectedContext != null,
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text("Start")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IndustrySelectionPreview() {
    SalesOnboardingTheme {
        IndustrySelectionScreen(onIndustrySelected = { _, _ -> })
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
                    description = "Information is collected to set up the customer account and follow your company's onboarding process."
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConsentPoint(
                    title = "Document photos",
                    description = "If you choose a document photo, text is read on this device to help with data entry. The photo and scan result are not uploaded or saved automatically."
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConsentPoint(
                    title = "Customer rights",
                    description = "The customer may request review of their submitted details through authorized company channels."
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ConsentPoint(
                    title = "Access and retention",
                    description = "Answers are saved to your company workspace as you go, so you can resume an unfinished session. Ask your administrator about access and retention."
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

// endregion
