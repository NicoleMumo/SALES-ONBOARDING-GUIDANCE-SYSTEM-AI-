package com.example.salesonboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.salesonboarding.ui.theme.SalesOnboardingTheme

private data class IndustryOption(
    val id: String,
    val title: String,
    val description: String
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
            title = "Telecommunications",
            description = "Mobile, broadband, TV, IoT, and voice service sign-ups."
        ),
        IndustryOption(
            id = "banking",
            title = "Banking",
            description = "Account onboarding, KYC checks, and financial onboarding flows."
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "New customer",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Choose an industry",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "We’ll guide you through the right steps for this customer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (assignedIndustry == null) {
            options.forEach { option ->
                val isSelected = selectedIndustry == option.id
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    onClick = {
                        selectedIndustry = option.id
                        selectedContext = null
                        errorMessage = null
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Company industry: ${if (assignedIndustry == "banking") "Banking" else "Telecommunications"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (selectedIndustry != null) {
            Text("Choose customer context", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndustrySelectionValidator.contextsFor(selectedIndustry).forEach { context ->
                    FilterChip(
                        selected = selectedContext == context,
                        onClick = { selectedContext = context; errorMessage = null },
                        label = { Text(context.replaceFirstChar(Char::uppercase)) }
                    )
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

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
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedIndustry != null && selectedContext != null,
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text("Continue")
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
