package com.example.salesonboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.salesonboarding.ui.theme.SalesOnboardingTheme
import java.util.Locale

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

private fun String.displayIndustryName(): String =
    when (this) {
        "telecom" -> "Telecommunications"
        "banking" -> "Banking"
        else -> replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

@Preview(showBackground = true)
@Composable
fun IndustrySelectionPreview() {
    SalesOnboardingTheme {
        IndustrySelectionScreen(onIndustrySelected = { _, _ -> })
    }
}
