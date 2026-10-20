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
import com.google.firebase.database.FirebaseDatabase
import com.example.salesonboarding.validation.FieldRuleConfiguration
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SalesOnboardingTheme {
                var signedIn by remember { mutableStateOf(false) }
                var selectedRole by remember { mutableStateOf<UserRole?>(null) }
                var agentId by remember { mutableStateOf("") }
                var showAgentHome by remember { mutableStateOf(false) }
                var selectedIndustry by remember { mutableStateOf<String?>(null) }
                var onboardingSessionId by remember { mutableStateOf<String?>(null) }
                var fieldRuleConfiguration by remember { mutableStateOf(FieldRuleConfiguration()) }
                val database = remember { FirebaseDatabase.getInstance().reference }
                val adminStore = remember { FirebaseAdminDataStore(database) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!signedIn) {
                        UnifiedLoginScreen(
                            onLogin = { email, _, role ->
                                agentId = email
                                selectedRole = role
                                showAgentHome = role == UserRole.SALES_AGENT
                                signedIn = true
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (selectedRole == UserRole.ADMIN) {
                        AdminDashboardScreen(
                            store = adminStore,
                            onConfigurationChanged = { fieldRuleConfiguration = it },
                            onSignOut = {
                                signedIn = false
                                selectedRole = null
                                selectedIndustry = null
                                onboardingSessionId = null
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (showAgentHome && selectedIndustry == null) {
                        AgentHomeScreen(
                            onStartOnboarding = { showAgentHome = false },
                            onSignOut = {
                                signedIn = false
                                selectedRole = null
                                showAgentHome = false
                                selectedIndustry = null
                                onboardingSessionId = null
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (selectedIndustry == null) {
                        IndustrySelectionScreen(
                            onIndustrySelected = { industry ->
                                selectedIndustry = industry
                                onboardingSessionId = UUID.randomUUID().toString()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (onboardingSessionId != null) {
                        val scope = rememberCoroutineScope()
                        val store = remember {
                            FirebaseFieldEntryStore(database)
                        }
                        val predictor = remember { TfliteNextFieldPredictor(applicationContext.assets) }
                        val controller = rememberOnboardingController(
                            onboardingSessionId!!,
                            scope,
                            store,
                            predictor,
                            industry = selectedIndustry!!,
                            agentId = agentId,
                            fields = OnboardingFormController.DEFAULT_FIELDS.map {
                                fieldRuleConfiguration.fieldFor(it)
                            }
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
