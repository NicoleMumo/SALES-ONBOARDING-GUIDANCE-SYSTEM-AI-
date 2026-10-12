package com.example.salesonboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.salesonboarding.ui.theme.SalesOnboardingTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SalesOnboardingTheme {
                var signedIn by remember { mutableStateOf(false) }
                var selectedRole by remember { mutableStateOf<UserRole?>(null) }
                var selectedIndustry by remember { mutableStateOf<String?>(null) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!signedIn) {
                        UnifiedLoginScreen(
                            onLogin = { _, _, role ->
                                selectedRole = role
                                signedIn = true
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (selectedRole == UserRole.ADMIN) {
                        AdminDashboardScreen(
                            onSignOut = {
                                signedIn = false
                                selectedRole = null
                                selectedIndustry = null
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (selectedIndustry == null) {
                        IndustrySelectionScreen(
                            onIndustrySelected = { industry -> selectedIndustry = industry },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        val scope = rememberCoroutineScope()
                        val store = remember { InMemoryFieldEntryStore() }
                        val predictor = remember { TfliteNextFieldPredictor(applicationContext.assets) }
                        val controller = rememberOnboardingController(
                            "session-1",
                            scope,
                            store,
                            predictor,
                            industry = selectedIndustry!!
                        )
                        OnboardingScreen(controller, Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SalesOnboardingTheme {
        val scope = rememberCoroutineScope()
        val store = remember { InMemoryFieldEntryStore() }
        val controller = rememberOnboardingController("preview-session", scope, store)
        OnboardingScreen(controller)
    }
}
