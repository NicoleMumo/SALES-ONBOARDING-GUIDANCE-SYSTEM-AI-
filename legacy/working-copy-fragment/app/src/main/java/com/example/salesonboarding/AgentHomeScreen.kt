package com.example.salesonboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AgentHomeScreen(
    onStartOnboarding: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "SalesMesh",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Agent home",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Start a new customer onboarding session or return to your account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Customer onboarding",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Choose the customer industry and capture the required information with guided validation."
                )
                Button(
                    onClick = onStartOnboarding,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start onboarding")
                }
            }
        }

        Text(
            text = "Saved sessions will appear here when persistent session storage is connected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }
    }
}
