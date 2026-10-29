package com.example.salesonboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.util.Locale

// region Sign-in screen

enum class UserRole(val label: String) {
    SALES_AGENT("Sales agent"),
    ADMIN("Administrator")
}

data class LoginFormState(
    val companyId: String = "",
    val email: String = "",
    val password: String = "",
    val role: UserRole = UserRole.SALES_AGENT,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false
)

object LoginFormValidator {
    fun validate(email: String, password: String): String? =
        validateEmail(email) ?: if (password.isBlank()) "Enter your password." else null

    fun validateCompanyId(companyId: String): String? =
        if (COMPANY_ID_PATTERN.matches(companyId.trim())) null
        else "Enter the company ID provided by your administrator."

    fun validateEmail(email: String): String? {
        if (email.isBlank()) return "Enter your email address."
        if (!EMAIL_PATTERN.matches(email.trim())) {
            return "Enter a valid email address."
        }
        return null
    }

    private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
}

@Composable
fun UnifiedLoginScreen(
    onLogin: (
        companyId: String,
        email: String,
        password: String,
        role: UserRole,
        onResult: (Result<UserRole>) -> Unit
    ) -> Unit,
    onRegisterAdmin: () -> Unit,
    onRegisterAgent: () -> Unit,
    initialRole: UserRole = UserRole.SALES_AGENT,
    onRoleSelected: (UserRole) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(LoginFormState(role = initialRole)) }
    val isAgent = state.role == UserRole.SALES_AGENT
    val enabled = !state.isSubmitting

    fun submit() {
        val error = (if (isAgent) LoginFormValidator.validateCompanyId(state.companyId) else null)
            ?: LoginFormValidator.validate(state.email, state.password)
        if (error != null) {
            state = state.copy(errorMessage = error)
            return
        }
        state = state.copy(isSubmitting = true, errorMessage = null)
        onLogin(state.companyId.trim(), state.email.trim(), state.password, state.role) { result ->
            result.onFailure { failure ->
                state = state.copy(
                    isSubmitting = false,
                    errorMessage = failure.message ?: "Sign-in failed. Try again."
                )
            }
        }
    }

    AuthFrame(modifier) {
        Text("Sign in", style = MaterialTheme.typography.headlineSmall)
        RoleToggle(state.role, enabled) { role ->
            state = state.copy(role = role, errorMessage = null)
            onRoleSelected(role)
        }
        if (isAgent) {
            AuthTextField(
                value = state.companyId,
                onValueChange = { state = state.copy(companyId = it.trim(), errorMessage = null) },
                label = "Company ID",
                enabled = enabled
            )
        }
        AuthTextField(
            value = state.email,
            onValueChange = { state = state.copy(email = it, errorMessage = null) },
            label = "Email address",
            enabled = enabled,
            keyboardType = KeyboardType.Email
        )
        PasswordField(
            value = state.password,
            onValueChange = { state = state.copy(password = it, errorMessage = null) },
            label = "Password",
            enabled = enabled,
            onDone = ::submit
        )
        state.errorMessage?.let { Banner(it, Tone.Negative) }
        PrimaryButton("Sign in", onClick = ::submit, isLoading = state.isSubmitting)
        TextButton(
            onClick = if (isAgent) onRegisterAgent else onRegisterAdmin,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
            Text(if (isAgent) "Create an agent account" else "Register an administrator account")
        }
        Text(
            if (isAgent) "Use the company ID and invited work email from your administrator."
            else "Admin access is granted by your organization after registration.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// endregion

// region Registration screen

@Composable
fun AccountRegistrationScreen(
    title: String,
    instructions: String,
    completionMessage: String,
    onBackToSignIn: () -> Unit,
    onRegister: (
        companyId: String,
        email: String,
        password: String,
        onResult: (Result<Unit>) -> Unit
    ) -> Unit,
    requiresCompanyId: Boolean = false,
    modifier: Modifier = Modifier
) {
    var companyId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmedPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var registered by remember { mutableStateOf(false) }
    BackHandler(enabled = !isSubmitting, onBack = onBackToSignIn)

    fun submit() {
        val companyIdError = if (requiresCompanyId) LoginFormValidator.validateCompanyId(companyId) else null
        val validationError = when {
            companyIdError != null -> companyIdError
            LoginFormValidator.validateEmail(email) != null -> LoginFormValidator.validateEmail(email)
            password.length < MIN_PASSWORD_LENGTH -> "Use a password with at least $MIN_PASSWORD_LENGTH characters."
            password != confirmedPassword -> "The passwords do not match."
            else -> null
        }
        if (validationError != null) {
            errorMessage = validationError
            return
        }
        isSubmitting = true
        onRegister(companyId.trim(), email.trim(), password) { result ->
            isSubmitting = false
            result.onSuccess {
                registered = true
                errorMessage = null
            }.onFailure {
                errorMessage = it.message ?: "Could not create the account. Try again."
            }
        }
    }

    AuthFrame(modifier) {
        if (registered) {
            Text("Registration complete", style = MaterialTheme.typography.headlineSmall)
            Banner(completionMessage, Tone.Positive)
            PrimaryButton("Back to sign in", onClick = onBackToSignIn)
            return@AuthFrame
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (requiresCompanyId) {
            AuthTextField(
                value = companyId,
                onValueChange = { companyId = it.trim(); errorMessage = null },
                label = "Company ID",
                enabled = !isSubmitting
            )
        }
        AuthTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = "Email address",
            enabled = !isSubmitting,
            keyboardType = KeyboardType.Email
        )
        PasswordField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = "Choose a password (at least $MIN_PASSWORD_LENGTH characters)",
            enabled = !isSubmitting,
            imeAction = ImeAction.Next
        )
        PasswordField(
            value = confirmedPassword,
            onValueChange = { confirmedPassword = it; errorMessage = null },
            label = "Confirm password",
            enabled = !isSubmitting,
            onDone = ::submit
        )
        errorMessage?.let { Banner(it, Tone.Negative) }
        PrimaryButton("Create account", onClick = ::submit, isLoading = isSubmitting)
        TextButton(onClick = onBackToSignIn, modifier = Modifier.fillMaxWidth(), enabled = !isSubmitting) {
            Text("Back to sign in")
        }
    }
}

private const val MIN_PASSWORD_LENGTH = 8

// endregion

// region Shared parts of the sign-in screens

/** The brand header and one card, scrollable and kept above the keyboard. */
@Composable
private fun AuthFrame(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.l, vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xl)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s)) {
            AppMark(size = 64.dp)
            Text("SalesMesh", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Consistent customer onboarding, made simple.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.l),
                content = content
            )
        }
    }
}

/** Two large options: sales agent or administrator. */
@Composable
private fun RoleToggle(selected: UserRole, enabled: Boolean, onSelect: (UserRole) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        UserRole.entries.forEach { role ->
            val isSelected = role == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(role) },
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) scheme.primaryContainer else scheme.surface,
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) scheme.primary else scheme.outline)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(Space.m)) {
                    Text(
                        role.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
}

/** A password box with Show/Hide. The keyboard's Done key runs [onDone] (sign in, create account). */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = {
                focusManager.clearFocus()
                onDone()
            }
        ),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }, enabled = enabled) {
                Text(if (visible) "Hide" else "Show")
            }
        }
    )
}

@Preview(name = "Sign in", showBackground = true, heightDp = 900)
@Composable
private fun SignInPreview() {
    SalesOnboardingTheme(darkTheme = false) {
        UnifiedLoginScreen(onLogin = { _, _, _, _, _ -> }, onRegisterAdmin = {}, onRegisterAgent = {})
    }
}

// endregion

// region Firebase sign-in and registration
//
// The screens above only collect what the person typed; these functions do the
// Firebase work. Every failed step after Firebase has signed the person in signs
// them out again, so a half-finished sign-in never leaves a session behind.

/** Must be true, with the matching database rule, before real customer data is used. */
private const val REQUIRE_AGENT_EMAIL_VERIFICATION = false

/** Who signed in, once Firebase and the company records agree they may. */
data class SignedInUser(
    val userId: String,
    val companyId: String,
    val role: UserRole,
    /** The company's onboarding industry. Loaded for agents only; the admin dashboard loads its own. */
    val companyIndustry: String? = null
)

/** The sign-in screen, connected to Firebase. [onSignedIn] runs before the screen is told of the success. */
@Composable
fun SignInScreen(
    membershipStore: AdminDataStore,
    onSignedIn: (SignedInUser) -> Unit,
    onRegisterAdmin: () -> Unit,
    onRegisterAgent: () -> Unit,
    initialRole: UserRole,
    onRoleSelected: (UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedLoginScreen(
        onLogin = { companyId, email, password, role, onResult ->
            signIn(membershipStore, companyId, email, password, role) { result ->
                result.onSuccess(onSignedIn)
                onResult(result.map(SignedInUser::role))
            }
        },
        onRegisterAdmin = onRegisterAdmin,
        onRegisterAgent = onRegisterAgent,
        initialRole = initialRole,
        onRoleSelected = onRoleSelected,
        modifier = modifier
    )
}

@Composable
fun AdminRegistrationScreen(onBackToSignIn: () -> Unit, modifier: Modifier = Modifier) {
    AccountRegistrationScreen(
        title = "Administrator registration",
        instructions = "Create an account, verify your email, then ask the Firebase owner to assign your company ID and administrator access.",
        completionMessage = "Account created. After verification, ask the Firebase owner to assign your company and admin role before signing in.",
        onBackToSignIn = onBackToSignIn,
        onRegister = { _, email, password, onResult -> registerAdminAccount(email, password, onResult) },
        modifier = modifier
    )
}

@Composable
fun AgentRegistrationScreen(onBackToSignIn: () -> Unit, modifier: Modifier = Modifier) {
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
        onBackToSignIn = onBackToSignIn,
        onRegister = ::registerAgentAccount,
        requiresCompanyId = true,
        modifier = modifier
    )
}

/**
 * Signs in with Firebase, then checks the role in the account's token against the role
 * the person chose. Admins need a company claim and a verified email; agents need an
 * active membership of the company they typed (or an invitation, which enrols them).
 */
private fun signIn(
    membershipStore: AdminDataStore,
    requestedCompanyId: String,
    email: String,
    password: String,
    requestedRole: UserRole,
    onResult: (Result<SignedInUser>) -> Unit
) {
    FirebaseAuth.getInstance()
        .signInWithEmailAndPassword(email, password)
        .addOnSuccessListener { authResult ->
            val user = authResult.user
            if (user == null) {
                signOutAndFail(onResult, IllegalStateException("The signed-in account is unavailable."))
                return@addOnSuccessListener
            }
            user.getIdToken(true)
                .addOnSuccessListener { token ->
                    checkAccess(membershipStore, user, token.claims, requestedCompanyId, email, requestedRole, onResult)
                }
                .addOnFailureListener { error -> signOutAndFail(onResult, loginFailureForDisplay(error)) }
        }
        .addOnFailureListener { error ->
            onResult(Result.failure(loginFailureForDisplay(error)))
        }
}

private fun checkAccess(
    membershipStore: AdminDataStore,
    user: FirebaseUser,
    claims: Map<String, Any>,
    requestedCompanyId: String,
    email: String,
    requestedRole: UserRole,
    onResult: (Result<SignedInUser>) -> Unit
) {
    val claimedCompanyId = claims["companyId"] as? String
    val trustedRole = if (claims["admin"] == true) UserRole.ADMIN else UserRole.SALES_AGENT
    when {
        trustedRole != requestedRole -> signOutAndFail(
            onResult,
            IllegalStateException("This account does not have ${requestedRole.label.lowercase()} access.")
        )
        trustedRole == UserRole.ADMIN && !COMPANY_ID_PATTERN.matches(claimedCompanyId.orEmpty()) -> signOutAndFail(
            onResult,
            IllegalStateException("This admin account has no valid company assignment. Ask the Firebase owner to set its companyId claim.")
        )
        trustedRole == UserRole.ADMIN && !user.isEmailVerified -> signOutAndFail(
            onResult,
            IllegalStateException("Verify this email address before administrator access can be enabled.")
        )
        trustedRole == UserRole.ADMIN -> onResult(
            Result.success(SignedInUser(user.uid, requireNotNull(claimedCompanyId), trustedRole))
        )
        REQUIRE_AGENT_EMAIL_VERIFICATION && !user.isEmailVerified ->
            user.sendEmailVerification().addOnCompleteListener { verification ->
                val message = if (verification.isSuccessful) {
                    "Verify your email before signing in. We sent a verification link."
                } else {
                    "Verify your email before signing in, then try again."
                }
                signOutAndFail(onResult, IllegalStateException(message, verification.exception))
            }
        else -> signInAgent(membershipStore, user, requestedCompanyId, email, onResult)
    }
}

private fun signInAgent(
    membershipStore: AdminDataStore,
    user: FirebaseUser,
    requestedCompanyId: String,
    email: String,
    onResult: (Result<SignedInUser>) -> Unit
) {
    val userEmail = user.email ?: email
    membershipStore.loadAgentMembership(user.uid, userEmail) { membershipResult ->
        membershipResult.onSuccess { membership ->
            when {
                membership == null -> membershipStore.enrollAgent(requestedCompanyId, user.uid, userEmail) { enrollment ->
                    enrollment.onSuccess {
                        loadAgentCompany(membershipStore, user.uid, requestedCompanyId, onResult)
                    }.onFailure { error ->
                        signOutAndFail(
                            onResult,
                            IllegalStateException(error.message ?: "This email is not invited to that company.", error)
                        )
                    }
                }
                membership.isActive && membership.companyId == requestedCompanyId ->
                    loadAgentCompany(membershipStore, user.uid, membership.companyId, onResult)
                else -> signOutAndFail(
                    onResult,
                    IllegalStateException("This account is inactive or belongs to a different company.")
                )
            }
        }.onFailure { error ->
            signOutAndFail(
                onResult,
                IllegalStateException("Could not verify this agent account. Try again when Firebase is available.", error)
            )
        }
    }
}

/** Agents can only start once their company has chosen its onboarding industry. */
private fun loadAgentCompany(
    membershipStore: AdminDataStore,
    userId: String,
    companyId: String,
    onResult: (Result<SignedInUser>) -> Unit
) {
    membershipStore.loadCompanyIndustry(companyId) { result ->
        result.onSuccess { industry ->
            if (industry == null) {
                signOutAndFail(
                    onResult,
                    IllegalStateException("Your company administrator must choose an onboarding industry before agents can sign in.")
                )
            } else {
                onResult(Result.success(SignedInUser(userId, companyId, UserRole.SALES_AGENT, industry)))
            }
        }.onFailure { error ->
            signOutAndFail(onResult, IllegalStateException("Could not load your company's onboarding industry.", error))
        }
    }
}

private fun registerAdminAccount(email: String, password: String, onResult: (Result<Unit>) -> Unit) {
    FirebaseAuth.getInstance()
        .createUserWithEmailAndPassword(email, password)
        .addOnSuccessListener { authResult ->
            val newUser = authResult.user
            if (newUser == null) {
                signOutAndFail(onResult, IllegalStateException("Firebase created no account."))
                return@addOnSuccessListener
            }
            newUser.sendEmailVerification()
                .addOnSuccessListener {
                    FirebaseAuth.getInstance().signOut()
                    onResult(Result.success(Unit))
                }
                .addOnFailureListener { error ->
                    signOutAndFail(
                        onResult,
                        IllegalStateException(
                            "Your account was created, but Firebase could not send the verification email. Contact the project owner before trying to register again.",
                            error
                        )
                    )
                }
        }
        .addOnFailureListener { error -> signOutAndFail(onResult, loginFailureForDisplay(error)) }
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
    val normalizedEmail = email.trim().lowercase(Locale.ROOT)
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

private fun <T> signOutAndFail(onResult: (Result<T>) -> Unit, error: Throwable) {
    FirebaseAuth.getInstance().signOut()
    onResult(Result.failure(error))
}

private fun loginFailureForDisplay(error: Exception): Exception =
    if (error.message.orEmpty().contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) {
        IllegalStateException(
            "Firebase Authentication is not configured for this project. Enable Email/Password under Firebase Console > Authentication > Sign-in method, then try again.",
            error
        )
    } else {
        error
    }

// endregion
