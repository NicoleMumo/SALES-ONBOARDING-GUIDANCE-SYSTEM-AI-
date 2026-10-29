package com.example.salesonboarding

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.tooling.preview.Preview
import com.example.salesonboarding.ui.theme.SalesOnboardingTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.example.salesonboarding.risk.TrainedCompletionRiskModel
import com.example.salesonboarding.validation.FieldRuleConfiguration
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
                var loginRole by remember { mutableStateOf(UserRole.SALES_AGENT) }
                var selectedRole by remember { mutableStateOf<UserRole?>(null) }
                var agentId by remember { mutableStateOf("") }
                var companyId by remember { mutableStateOf("") }
                var companyIndustry by remember { mutableStateOf<String?>(null) }
                var showAgentHome by remember { mutableStateOf(false) }
                var showAgentSessions by remember { mutableStateOf(false) }
                var selectedIndustry by remember { mutableStateOf<String?>(null) }
                var selectedContext by remember { mutableStateOf<String?>(null) }
                var consentGiven by remember { mutableStateOf(false) }
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
                fun completeAgentSignIn(
                    userId: String,
                    tenantId: String,
                    role: UserRole,
                    onResult: (Result<UserRole>) -> Unit
                ) {
                    membershipStore.loadCompanyIndustry(tenantId) { result ->
                        result.onSuccess { industry ->
                            if (industry == null) {
                                FirebaseAuth.getInstance().signOut()
                                onResult(
                                    Result.failure(
                                        IllegalStateException(
                                            "Your company administrator must choose an onboarding industry before agents can sign in."
                                        )
                                    )
                                )
                            } else {
                                agentId = userId
                                companyId = tenantId
                                companyIndustry = industry
                                selectedIndustry = null
                                selectedRole = role
                                showAgentHome = true
                                showAgentSessions = false
                                signedIn = true
                                loadFieldConfiguration(tenantId)
                                onResult(Result.success(role))
                            }
                        }.onFailure { error ->
                            FirebaseAuth.getInstance().signOut()
                            onResult(
                                Result.failure(
                                    IllegalStateException(
                                        "Could not load your company's onboarding industry.",
                                        error
                                    )
                                )
                            )
                        }
                    }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!signedIn && showAdminRegistration) {
                        AccountRegistrationScreen(
                            title = "Administrator registration",
                            instructions = "Create an account, verify your email, then ask the Firebase owner to assign your company ID and administrator access.",
                            completionMessage = "Account created. After verification, ask the Firebase owner to assign your company and admin role before signing in.",
                            onBackToSignIn = { showAdminRegistration = false },
                            onRegister = { _, email, password, onResult ->
                                FirebaseAuth.getInstance()
                                    .createUserWithEmailAndPassword(email, password)
                                    .addOnSuccessListener { authResult ->
                                        val newUser = authResult.user
                                        if (newUser == null) {
                                            FirebaseAuth.getInstance().signOut()
                                            onResult(Result.failure(IllegalStateException("Firebase created no account.")))
                                        } else {
                                            newUser.sendEmailVerification()
                                                .addOnSuccessListener {
                                                    FirebaseAuth.getInstance().signOut()
                                                    onResult(Result.success(Unit))
                                                }
                                                .addOnFailureListener { error ->
                                                    FirebaseAuth.getInstance().signOut()
                                                    onResult(
                                                        Result.failure(
                                                            IllegalStateException(
                                                                "Your account was created, but Firebase could not send the verification email. Contact the project owner before trying to register again.",
                                                                error
                                                            )
                                                        )
                                                    )
                                                }
                                        }
                                    }
                                    .addOnFailureListener { error ->
                                        FirebaseAuth.getInstance().signOut()
                                        onResult(Result.failure(loginFailureForDisplay(error)))
                                    }
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (!signedIn && showAgentRegistration) {
                        AccountRegistrationScreen(
                            title = "Create agent account",
                            instructions = if (REQUIRE_AGENT_EMAIL_VERIFICATION) {
                                "Enter your company ID and the invited work email. Choose a password, then verify your email before signing in."
                            } else {
                                "Enter your company ID and the invited work email, then choose your password."
                            },
                            completionMessage = if (REQUIRE_AGENT_EMAIL_VERIFICATION) {
                                "Your account was created. Check your email and verify the address, then sign in with the password you chose."
                            } else {
                                "Your account was created. Sign in with your email and the password you chose."
                            },
                            onBackToSignIn = { showAgentRegistration = false },
                            onRegister = { companyId, email, password, onResult ->
                                registerAgentAccount(companyId, email, password, onResult)
                            },
                            requiresCompanyId = true,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (!signedIn) {
                        UnifiedLoginScreen(
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
                            onLogin = { requestedCompanyId, email, password, requestedRole, onResult ->
                                FirebaseAuth.getInstance()
                                    .signInWithEmailAndPassword(email, password)
                                    .addOnSuccessListener { authResult ->
                                        val user = authResult.user
                                        if (user == null) {
                                            FirebaseAuth.getInstance().signOut()
                                            onResult(Result.failure(IllegalStateException("The signed-in account is unavailable.")))
                                        } else {
                                            user.getIdToken(true).addOnSuccessListener { token ->
                                                val isCompanyAdmin = token.claims["admin"] == true
                                                val claimedCompanyId = token.claims["companyId"] as? String
                                                val trustedRole = if (isCompanyAdmin) {
                                                    UserRole.ADMIN
                                                } else {
                                                    UserRole.SALES_AGENT
                                                }
                                                if (trustedRole != requestedRole) {
                                                    FirebaseAuth.getInstance().signOut()
                                                    onResult(Result.failure(IllegalStateException("This account does not have ${requestedRole.label.lowercase()} access.")))
                                                } else if (trustedRole == UserRole.ADMIN &&
                                                    !COMPANY_ID_PATTERN.matches(claimedCompanyId.orEmpty())
                                                ) {
                                                    FirebaseAuth.getInstance().signOut()
                                                    onResult(Result.failure(IllegalStateException("This admin account has no valid company assignment. Ask the Firebase owner to set its companyId claim.")))
                                                } else if (trustedRole == UserRole.ADMIN && !user.isEmailVerified) {
                                                    FirebaseAuth.getInstance().signOut()
                                                    onResult(Result.failure(IllegalStateException("Verify this email address before administrator access can be enabled.")))
                                                } else if (trustedRole == UserRole.SALES_AGENT) {
                                                    if (REQUIRE_AGENT_EMAIL_VERIFICATION && !user.isEmailVerified) {
                                                        user.sendEmailVerification().addOnCompleteListener { verification ->
                                                            FirebaseAuth.getInstance().signOut()
                                                            val message = if (verification.isSuccessful) {
                                                                "Verify your email before signing in. We sent a verification link."
                                                            } else {
                                                                "Verify your email before signing in, then try again."
                                                            }
                                                            onResult(
                                                                Result.failure(
                                                                    IllegalStateException(message, verification.exception)
                                                                )
                                                            )
                                                        }
                                                    } else {
                                                        membershipStore.loadAgentMembership(
                                                            user.uid,
                                                            user.email ?: email
                                                        ) { membershipResult ->
                                                            membershipResult.onSuccess { membership ->
                                                                if (membership != null) {
                                                                    if (
                                                                        membership.isActive &&
                                                                        membership.companyId == requestedCompanyId
                                                                    ) {
                                                                        completeAgentSignIn(
                                                                            user.uid,
                                                                            membership.companyId,
                                                                            trustedRole,
                                                                            onResult
                                                                        )
                                                                    } else {
                                                                        FirebaseAuth.getInstance().signOut()
                                                                        onResult(
                                                                            Result.failure(
                                                                                IllegalStateException(
                                                                                    "This account is inactive or belongs to a different company."
                                                                                )
                                                                            )
                                                                        )
                                                                    }
                                                                } else {
                                                                    membershipStore.enrollAgent(
                                                                        requestedCompanyId,
                                                                        user.uid,
                                                                        user.email ?: email
                                                                    ) { enrollment ->
                                                                        enrollment.onSuccess {
                                                                            completeAgentSignIn(
                                                                                user.uid,
                                                                                requestedCompanyId,
                                                                                trustedRole,
                                                                                onResult
                                                                            )
                                                                        }.onFailure { error ->
                                                                            FirebaseAuth.getInstance().signOut()
                                                                            onResult(
                                                                                Result.failure(
                                                                                    IllegalStateException(
                                                                                        error.message
                                                                                            ?: "This email is not invited to that company.",
                                                                                        error
                                                                                    )
                                                                                )
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }.onFailure { error ->
                                                                FirebaseAuth.getInstance().signOut()
                                                                onResult(
                                                                    Result.failure(
                                                                        IllegalStateException(
                                                                            "Could not verify this agent account. Try again when Firebase is available.",
                                                                            error
                                                                        )
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    agentId = user.uid
                                                    companyId = requireNotNull(claimedCompanyId)
                                                    selectedRole = trustedRole
                                                    showAgentHome = false
                                                    showAgentSessions = false
                                                    signedIn = true
                                                    loadFieldConfiguration(companyId)
                                                    onResult(Result.success(trustedRole))
                                                }
                                            }.addOnFailureListener { error ->
                                                FirebaseAuth.getInstance().signOut()
                                                onResult(Result.failure(loginFailureForDisplay(error)))
                                            }
                                        }
                                    }
                                    .addOnFailureListener { error ->
                                        onResult(Result.failure(loginFailureForDisplay(error)))
                                    }
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (selectedRole == UserRole.ADMIN) {
                        AdminDashboardScreen(
                            store = adminStore,
                            companyId = companyId,
                            onConfigurationChanged = { fieldRuleConfiguration = it },
                            onCompanyIndustryChanged = { companyIndustry = it },
                            onSignOut = {
                                FirebaseAuth.getInstance().signOut()
                                signedIn = false
                                selectedRole = null
                                companyId = ""
                                companyIndustry = null
                                selectedIndustry = null
                                selectedContext = null
                                consentGiven = false
                                onboardingSessionId = null
                                resumedSession = null
                                showAgentSessions = false
                            },
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
                            companyIndustry = companyIndustry,
                            onSignOut = {
                                FirebaseAuth.getInstance().signOut()
                                signedIn = false
                                selectedRole = null
                                showAgentHome = false
                                showAgentSessions = false
                                selectedIndustry = null
                                selectedContext = null
                                consentGiven = false
                                onboardingSessionId = null
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
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (!consentGiven) {
                        OnboardingConsentScreen(
                            onConsent = { consentGiven = true },
                            onBack = {
                                selectedIndustry = null
                                selectedContext = null
                                onboardingSessionId = null
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (onboardingSessionId != null) {
                        when {
                            isLoadingFieldConfiguration -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Text("Loading onboarding setup…")
                                Text("This usually takes a few seconds.")
                            }
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
private const val REQUIRE_AGENT_EMAIL_VERIFICATION = false
private val COMPANY_ID_PATTERN = Regex("^[A-Za-z0-9_-]{3,64}$")
private const val FIELD_CONFIGURATION_FALLBACK_WARNING =
    "Firebase field settings could not be loaded. The standard field list is in use; custom fields and admin changes may be missing. Saving still requires Firebase database access."

private fun loginFailureForDisplay(error: Exception): Exception =
    if (error.message.orEmpty().contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) {
        IllegalStateException(
            "Firebase Authentication is not configured for this project. Enable Email/Password under Firebase Console > Authentication > Sign-in method, then try again.",
            error
        )
    } else {
        error
    }

private fun registerAgentAccount(
    companyId: String,
    email: String,
    password: String,
    onResult: (Result<Unit>) -> Unit
) {
    if (!COMPANY_ID_PATTERN.matches(companyId.trim())) {
        onResult(Result.failure(IllegalArgumentException("Enter the company ID provided by your administrator.")))
        return
    }
    val auth = FirebaseAuth.getInstance()
    val normalizedEmail = email.trim().lowercase(java.util.Locale.ROOT)
    auth.createUserWithEmailAndPassword(normalizedEmail, password)
        .addOnSuccessListener { authResult ->
            val newUser = authResult.user
            if (newUser == null) {
                auth.signOut()
                onResult(Result.failure(IllegalStateException("Firebase created no account.")))
                return@addOnSuccessListener
            }

            fun rejectAndRemoveAccount(reason: Exception) {
                newUser.delete().addOnCompleteListener { deletion ->
                    auth.signOut()
                    val error = if (deletion.isSuccessful) {
                        reason
                    } else {
                        IllegalStateException(
                            "${reason.message} The unapproved account could not be removed; contact the administrator.",
                            deletion.exception
                        )
                    }
                    onResult(Result.failure(error))
                }
            }

            if (REQUIRE_AGENT_EMAIL_VERIFICATION) {
                newUser.sendEmailVerification().addOnCompleteListener { verification ->
                    if (verification.isSuccessful) {
                        auth.signOut()
                        onResult(Result.success(Unit))
                    } else {
                        rejectAndRemoveAccount(
                            IllegalStateException(
                                "Your account was created, but Firebase could not send the verification email.",
                                verification.exception
                            )
                        )
                    }
                }
            } else {
                auth.signOut()
                onResult(Result.success(Unit))
            }
        }
        .addOnFailureListener { error ->
            auth.signOut()
            onResult(Result.failure(loginFailureForDisplay(error)))
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
