package com.example.salesonboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
    fun validate(industry: String?): String? {
        if (industry.isNullOrBlank()) return "Select an onboarding industry."
        return when (industry) {
            "telecom", "banking" -> null
            else -> "Select a valid onboarding industry."
        }
    }
}

@Composable
fun IndustrySelectionScreen(
    onIndustrySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndustry by remember { mutableStateOf<String?>(null) }
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
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Choose onboarding flow",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Select the industry for this customer session before onboarding starts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
                onClick = { selectedIndustry = option.id; errorMessage = null }
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
                if (validationError == null) {
                    onIndustrySelected(requireNotNull(selectedIndustry))
                } else {
                    errorMessage = validationError
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = true
        ) {
            Text("Continue to onboarding")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IndustrySelectionPreview() {
    SalesOnboardingTheme {
        IndustrySelectionScreen(onIndustrySelected = {})
    }
}
