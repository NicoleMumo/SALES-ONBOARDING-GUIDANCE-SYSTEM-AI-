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
import androidx.compose.runtime.LaunchedEffect
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
                var showRegistration by remember { mutableStateOf(false) }
                var selectedRole by remember { mutableStateOf<UserRole?>(null) }
                var agentId by remember { mutableStateOf("") }
                var companyId by remember { mutableStateOf("") }
                var companyIndustry by remember { mutableStateOf<String?>(null) }
                // The admin allows anonymised steps to improve the system (Company page).
                var companySharesForTraining by remember { mutableStateOf(false) }
                // The customer of this run of the form agreed to that, on the consent screen.
                var trainingAgreed by remember { mutableStateOf(false) }
                var showAgentHome by remember { mutableStateOf(false) }
                // A sales agent's companies, the one open now, and its message (AgentCompanies.kt).
                var agentCompanies by remember { mutableStateOf<List<CompanyMembership>>(emptyList()) }
                var openedCompany by remember { mutableStateOf<OpenedCompany?>(null) }
                var showCompanyPicker by remember { mutableStateOf(false) }
                var showCompanyBrief by remember { mutableStateOf(false) }
                var openingCompanyId by remember { mutableStateOf<String?>(null) }
                var companyError by remember { mutableStateOf<String?>(null) }
                var isCheckingInvitations by remember { mutableStateOf(false) }
                var companyInfo by remember { mutableStateOf<String?>(null) }
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
                /** Opens one of the agent's companies: its settings, questions, sessions and message. */
                fun openCompany(membership: CompanyMembership) {
                    openingCompanyId = membership.companyId
                    companyError = null
                    openAgentCompany(membershipStore, membership) { result ->
                        openingCompanyId = null
                        result.onSuccess { opened ->
                            openedCompany = opened
                            companyId = opened.companyId
                            companyIndustry = opened.industry
                            companySharesForTraining = opened.sharesForTraining
                            selectedIndustry = null
                            selectedContext = null
                            showAgentSessions = false
                            showCompanyPicker = false
                            showCompanyBrief = true
                            loadFieldConfiguration(opened.companyId)
                        }.onFailure {
                            companyError = it.message
                            showCompanyPicker = true
                        }
                    }
                }
                fun startSignedInSession(user: SignedInUser) {
                    agentId = user.userId
                    companyId = user.companyId
                    selectedRole = user.role
                    showAgentSessions = false
                    signedIn = true
                    if (user.role == UserRole.SALES_AGENT) {
                        agentCompanies = user.companies
                        selectedIndustry = null
                        showAgentHome = true
                        // An agent of one company goes straight in; with more, they choose.
                        val only = user.companies.singleOrNull()
                        if (only != null) openCompany(only) else showCompanyPicker = true
                    } else {
                        showAgentHome = false
                        loadFieldConfiguration(user.companyId)
                    }
                }
                /** Joins any company that invited this agent's email since they signed in. */
                fun checkInvitations() {
                    val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
                    isCheckingInvitations = true
                    companyError = null
                    companyInfo = null
                    membershipStore.loadInvitedCompanies(email) { invited ->
                        val known = agentCompanies.map { it.companyId }.toSet()
                        val toJoin = invited.getOrDefault(emptyList()).filter { it !in known }
                        fun finish() {
                            membershipStore.loadAgentCompanies(agentId, email) { reloaded ->
                                isCheckingInvitations = false
                                val active = reloaded.getOrDefault(agentCompanies).filter { it.isActive }
                                val added = active.count { it.companyId !in known }
                                agentCompanies = active
                                companyInfo = if (added == 0) "No new invitations for $email." else
                                    "Added $added ${if (added == 1) "company" else "companies"}."
                            }
                        }
                        fun joinNext(index: Int) {
                            if (index == toJoin.size) finish()
                            else membershipStore.enrollAgent(toJoin[index], agentId, email) { joinNext(index + 1) }
                        }
                        joinNext(0)
                    }
                }
                /** Opens an unfinished session where it stopped (from My sessions or the home screen). */
                fun resumeSession(session: StoredOnboardingSession) {
                    resumedSession = session
                    onboardingSessionId = session.sessionId
                    selectedIndustry = session.industry
                    selectedContext = session.context
                    consentGiven = true
                    showAgentSessions = false
                    showAgentHome = false
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
                    companySharesForTraining = false
                    agentCompanies = emptyList()
                    openedCompany = null
                    showCompanyPicker = false
                    showCompanyBrief = false
                    openingCompanyId = null
                    companyError = null
                    companyInfo = null
                    trainingAgreed = false
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
                    if (!signedIn && showRegistration) {
                        RegistrationScreen(
                            onBackToSignIn = { showRegistration = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (!signedIn) {
                        SignInScreen(
                            membershipStore = membershipStore,
                            onSignedIn = ::startSignedInSession,
                            onRegister = { showRegistration = true },
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
                    } else if (showCompanyPicker || openedCompany == null) {
                        CompanyPickerScreen(
                            companies = agentCompanies,
                            openingCompanyId = openingCompanyId,
                            errorMessage = companyError,
                            isChecking = isCheckingInvitations,
                            infoMessage = companyInfo,
                            onPick = ::openCompany,
                            onCheckInvitations = ::checkInvitations,
                            onSignOut = ::signOut,
                            // Back to the company already open, if there is one.
                            onBack = openedCompany?.let { { showCompanyPicker = false; companyError = null } },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (showCompanyBrief) {
                        CompanyBriefScreen(
                            company = openedCompany!!,
                            onStart = {
                                showCompanyBrief = false
                                showAgentHome = true
                            },
                            onSwitchCompany = { showCompanyPicker = true },
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
                            onResumeSession = ::resumeSession,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (showAgentHome && selectedIndustry == null) {
                        // The latest unfinished session, offered on the home screen. Sessions
                        // left untouched for 48 hours are marked abandoned instead (SessionExpiry).
                        var unfinished by remember(agentId) { mutableStateOf<StoredOnboardingSession?>(null) }
                        var sessionsLoaded by remember(agentId) { mutableIntStateOf(0) }
                        val sessionWriter = remember(database, companyId) {
                            companyId.takeIf(COMPANY_ID_PATTERN::matches)?.let { FirebaseFieldEntryStore(database, it) }
                        }
                        LaunchedEffect(agentId, sessionHistoryStore, sessionsLoaded) {
                            sessionHistoryStore?.loadAgentSessions(agentId) { result ->
                                val now = System.currentTimeMillis()
                                val open = result.getOrNull().orEmpty().filter { it.status == "in_progress" }
                                open.filter { SessionExpiry.isStale(it, now) }
                                    .forEach { stale -> sessionWriter?.abandonSession(stale.sessionId) {} }
                                unfinished = open.firstOrNull { !SessionExpiry.isStale(it, now) }
                            }
                        }
                        AgentHomeScreen(
                            companyName = openedCompany?.name,
                            onSwitchCompany = {
                                companyError = null
                                showCompanyPicker = true
                            },
                            onShowCompanyMessage = { showCompanyBrief = true },
                            onStartOnboarding = {
                                showAgentSessions = false
                                showAgentHome = false
                                resumedSession = null
                            },
                            onViewSessions = { showAgentSessions = true },
                            onSignOut = ::signOut,
                            unfinishedSession = unfinished,
                            onContinueSession = ::resumeSession,
                            onCancelSession = { session ->
                                unfinished = null
                                sessionWriter?.abandonSession(session.sessionId) { sessionsLoaded++ }
                            },
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
                            onConsent = { agreed ->
                                consentGiven = true
                                consentAtMillis = System.currentTimeMillis()
                                trainingAgreed = agreed
                            },
                            askTrainingConsent = companySharesForTraining,
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
                                    // A resumed session keeps the choice its customer made.
                                    shareForTraining = companySharesForTraining && (
                                        resumedSession?.takeIf { it.sessionId == onboardingSessionId }
                                            ?.trainingConsent ?: trainingAgreed
                                        ),
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
