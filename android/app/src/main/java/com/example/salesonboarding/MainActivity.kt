package com.example.salesonboarding

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SalesOnboardingTheme {
                var signedIn by remember { mutableStateOf(false) }
                var showAdminRegistration by remember { mutableStateOf(false) }
                var showAgentRegistration by remember { mutableStateOf(false) }
                var loginRole by remember { mutableStateOf(UserRole.ADMIN) }
                var selectedRole by remember { mutableStateOf<UserRole?>(null) }
                var agentId by remember { mutableStateOf("") }
                var companyId by remember { mutableStateOf("") }
                var companyIndustry by remember { mutableStateOf<String?>(null) }
                var showAgentHome by remember { mutableStateOf(false) }
                var showAgentSessions by remember { mutableStateOf(false) }
                var selectedIndustry by remember { mutableStateOf<String?>(null) }
                var selectedContext by remember { mutableStateOf<String?>(null) }
                var consentGiven by remember { mutableStateOf(false) }
                // When the customer agreed in this run of the form; saved with the new session.
                var consentAtMillis by remember { mutableStateOf<Long?>(null) }
                var onboardingSessionId by remember { mutableStateOf<String?>(null) }
                var resumedSession by remember { mutableStateOf<StoredOnboardingSession?>(null) }
                var fieldRuleConfiguration by remember { mutableStateOf(FieldRuleConfiguration()) }
                var isLoadingFieldConfiguration by remember { mutableStateOf(true) }
                var fieldConfigurationWarning by remember { mutableStateOf<String?>(null) }
                var fieldConfigurationRequestId by remember { mutableIntStateOf(0) }
                val mainHandler = remember { Handler(Looper.getMainLooper()) }
                val database = remember { SalesMeshApplication.realtimeDatabase().reference }
                val membershipStore = remember { FirebaseAdminDataStore(database) }
                val adminStore = remember(database, companyId) {
                    FirebaseAdminDataStore(database, companyId)
                }
                val sessionHistoryStore = remember(database, companyId) {
                    companyId.takeIf(COMPANY_ID_PATTERN::matches)?.let { tenantId ->
                        FirebaseSessionHistoryStore(database, tenantId)
                    }
                }
                fun loadFieldConfiguration(tenantId: String) {
                    val requestId = fieldConfigurationRequestId + 1
                    fieldConfigurationRequestId = requestId
                    isLoadingFieldConfiguration = true
                    fieldConfigurationWarning = null
                    var completed = false
                    val timeout = Runnable {
                        if (!completed && requestId == fieldConfigurationRequestId) {
                            completed = true
                            isLoadingFieldConfiguration = false
                            fieldRuleConfiguration = FieldRuleConfiguration()
                            fieldConfigurationWarning = FIELD_CONFIGURATION_FALLBACK_WARNING
                        }
                    }
                    mainHandler.postDelayed(timeout, FIELD_CONFIGURATION_TIMEOUT_MS)
                    FirebaseAdminDataStore(database, tenantId).loadRuleConfiguration { result ->
                        if (completed || requestId != fieldConfigurationRequestId) {
                            return@loadRuleConfiguration
                        }
                        completed = true
                        mainHandler.removeCallbacks(timeout)
                        result.onSuccess {
                            fieldRuleConfiguration = it
                            fieldConfigurationWarning = null
                        }.onFailure {
                            fieldRuleConfiguration = FieldRuleConfiguration()
                            fieldConfigurationWarning = FIELD_CONFIGURATION_FALLBACK_WARNING
                        }
                        isLoadingFieldConfiguration = false
                    }
                }
                fun startSignedInSession(user: SignedInUser) {
                    agentId = user.userId
                    companyId = user.companyId
                    selectedRole = user.role
                    if (user.role == UserRole.SALES_AGENT) {
                        companyIndustry = user.companyIndustry
                        selectedIndustry = null
                        showAgentHome = true
                    } else {
                        showAgentHome = false
                    }
                    showAgentSessions = false
                    signedIn = true
                    loadFieldConfiguration(user.companyId)
                }
                fun signOut() {
                    FirebaseAuth.getInstance().signOut()
                    // Clear everything from the signed-in session, so the next person
                    // starts clean and no customer answers stay in memory.
                    signedIn = false
                    selectedRole = null
                    agentId = ""
                    companyId = ""
                    companyIndustry = null
                    showAgentHome = false
                    showAgentSessions = false
                    selectedIndustry = null
                    selectedContext = null
                    consentGiven = false
                    onboardingSessionId = null
                    resumedSession = null
                    fieldRuleConfiguration = FieldRuleConfiguration()
                    fieldConfigurationWarning = null
                    // A rule download still in flight belongs to the old session: ignore it.
                    fieldConfigurationRequestId++
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!signedIn && showAdminRegistration) {
                        AdminRegistrationScreen(
                            onBackToSignIn = { showAdminRegistration = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (!signedIn && showAgentRegistration) {
                        AgentRegistrationScreen(
                            onBackToSignIn = { showAgentRegistration = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (!signedIn) {
                        SignInScreen(
                            membershipStore = membershipStore,
                            onSignedIn = ::startSignedInSession,
                            onRegisterAdmin = {
                                loginRole = UserRole.ADMIN
                                showAgentRegistration = false
                                showAdminRegistration = true
                            },
                            onRegisterAgent = {
                                loginRole = UserRole.SALES_AGENT
                                showAdminRegistration = false
                                showAgentRegistration = true
                            },
                            initialRole = loginRole,
                            onRoleSelected = { loginRole = it },
                            // Full screen: the photo runs under the status bar and the
                            // sheet pads the system bars itself.
                            modifier = Modifier
                        )
                    } else if (selectedRole == UserRole.ADMIN) {
                        AdminDashboardScreen(
                            store = adminStore,
                            companyId = companyId,
                            onConfigurationChanged = { fieldRuleConfiguration = it },
                            onCompanyIndustryChanged = { companyIndustry = it },
                            onSignOut = ::signOut,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (
                        showAgentHome &&
                        showAgentSessions &&
                        selectedIndustry == null &&
                        sessionHistoryStore != null
                    ) {
                        AgentSessionHistoryScreen(
                            agentUid = agentId,
                            store = sessionHistoryStore,
                            onBack = { showAgentSessions = false },
                            onResumeSession = { session ->
                                resumedSession = session
                                onboardingSessionId = session.sessionId
                                selectedIndustry = session.industry
                                selectedContext = session.context
                                consentGiven = true
                                showAgentSessions = false
                                showAgentHome = false
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (showAgentHome && selectedIndustry == null) {
                        AgentHomeScreen(
                            onStartOnboarding = {
                                showAgentSessions = false
                                showAgentHome = false
                                resumedSession = null
                            },
                            onViewSessions = { showAgentSessions = true },
                            onSignOut = ::signOut,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (selectedIndustry == null) {
                        IndustrySelectionScreen(
                            onIndustrySelected = { industry, context ->
                                selectedIndustry = industry
                                selectedContext = context
                                resumedSession = null
                                consentGiven = false
                                onboardingSessionId = UUID.randomUUID().toString()
                            },
                            assignedIndustry = companyIndustry,
                            onBack = { showAgentHome = true },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (!consentGiven) {
                        OnboardingConsentScreen(
                            onConsent = {
                                consentGiven = true
                                consentAtMillis = System.currentTimeMillis()
                            },
                            onBack = {
                                selectedIndustry = null
                                selectedContext = null
                                onboardingSessionId = null
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (onboardingSessionId != null) {
                        when {
                            isLoadingFieldConfiguration -> LoadingState(
                                "Loading onboarding setup…",
                                Modifier.padding(innerPadding),
                                detail = "This usually takes a few seconds."
                            )
                            else -> {
                                val playbook = remember(selectedIndustry, selectedContext, fieldRuleConfiguration) {
                                    OnboardingPlaybooks.forIndustry(
                                        selectedIndustry!!,
                                        fieldRuleConfiguration,
                                        selectedContext!!
                                    )
                                }
                                val scope = rememberCoroutineScope()
                                val store = remember(database, companyId) {
                                    FirebaseFieldEntryStore(database, companyId)
                                }
                                val predictorAttempt = remember {
                                    try {
                                        Result.success(TfliteNextFieldPredictor(applicationContext.assets))
                                    } catch (error: Exception) {
                                        Result.failure(error)
                                    }
                                }
                                val predictor = predictorAttempt.getOrNull()
                                DisposableEffect(predictor) {
                                    onDispose { predictor?.close() }
                                }
                                // A missing or refused file leaves this null, and the
                                // controller falls back to the older hand-set rule.
                                val riskModel = remember {
                                    try {
                                        applicationContext.assets
                                            .open(TrainedCompletionRiskModel.ASSET_NAME)
                                            .bufferedReader()
                                            .use { it.readText() }
                                            .let(TrainedCompletionRiskModel::fromJsonOrNull)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                val controller = rememberOnboardingController(
                                    onboardingSessionId!!,
                                    scope,
                                    store,
                                    predictor,
                                    industry = selectedIndustry!!,
                                    context = selectedContext!!,
                                    agentUid = agentId,
                                    agentEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
                                    fields = playbook.fields,
                                    playbook = playbook,
                                    initialModelUnavailable = predictorAttempt.isFailure,
                                    riskModel = riskModel,
                                    consentAtMillis = consentAtMillis,
                                    initialValues = resumedSession
                                        ?.takeIf { it.sessionId == onboardingSessionId }
                                        ?.entries
                                        ?.associate { it.fieldId to it.value }
                                        .orEmpty(),
                                    initialResumeFieldId = resumedSession
                                        ?.takeIf { it.sessionId == onboardingSessionId }
                                        ?.let { session ->
                                            session.resumeFieldId
                                                ?.takeIf { resumeId ->
                                                    playbook.fields.any { it.fieldId == resumeId }
                                                }
                                                ?: playbook.fields.firstOrNull { field ->
                                                val savedEntry = session.entries.firstOrNull {
                                                    it.fieldId == field.fieldId
                                                }
                                                savedEntry?.value.isNullOrBlank() ||
                                                    savedEntry?.isValid == false
                                            }?.fieldId
                                        },
                                    isResumingSession = resumedSession?.sessionId == onboardingSessionId
                                )
                                OnboardingScreen(
                                    controller = controller,
                                    onNewOnboarding = {
                                        selectedIndustry = null
                                        selectedContext = null
                                        consentGiven = false
                                        onboardingSessionId = null
                                        resumedSession = null
                                        showAgentHome = false
                                        showAgentSessions = false
                                    },
                                    onReturnToDashboard = {
                                        selectedIndustry = null
                                        selectedContext = null
                                        consentGiven = false
                                        onboardingSessionId = null
                                        showAgentHome = true
                                        showAgentSessions = false
                                    },
                                    configurationWarning = fieldConfigurationWarning,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val FIELD_CONFIGURATION_TIMEOUT_MS = 5_000L
private const val FIELD_CONFIGURATION_FALLBACK_WARNING =
    "Firebase field settings could not be loaded. The standard field list is in use; custom fields and admin changes may be missing. Saving still requires Firebase database access."

class SalesMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        realtimeDatabase()
    }

    companion object {
        private const val DATABASE_URL =
            "https://salesmesh-default-rtdb.asia-southeast1.firebasedatabase.app"

        private val database: FirebaseDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            FirebaseDatabase.getInstance(DATABASE_URL).also {
                it.setPersistenceEnabled(true)
            }
        }

        fun realtimeDatabase(): FirebaseDatabase = database
    }
}
