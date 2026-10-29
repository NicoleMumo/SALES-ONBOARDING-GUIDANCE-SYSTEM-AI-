package com.example.salesonboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrandMark()
                Text(
                    text = "SalesMesh",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Consistent customer onboarding, made simple.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (state.role == UserRole.SALES_AGENT) {
                                "Sales agent login"
                            } else {
                                "Administrator login"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Sign in with your SalesMesh account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UserRole.values().forEach { role ->
                            FilterChip(
                                selected = state.role == role,
                                onClick = {
                                    state = state.copy(role = role, errorMessage = null)
                                    onRoleSelected(role)
                                },
                                label = { Text(role.label) }
                            )
                        }
                    }

                    if (state.role == UserRole.SALES_AGENT) {
                        OutlinedTextField(
                            value = state.companyId,
                            onValueChange = {
                                state = state.copy(companyId = it.trim(), errorMessage = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Company ID") },
                            singleLine = true,
                            enabled = !state.isSubmitting
                        )
                    }

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = {
                            state = state.copy(email = it, errorMessage = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !state.isSubmitting
                    )

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = {
                            state = state.copy(password = it, errorMessage = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            androidx.compose.material3.TextButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !state.isSubmitting
                            ) {
                                Text(if (passwordVisible) "Hide" else "Show")
                            }
                        },
                        isError = state.errorMessage != null,
                        supportingText = state.errorMessage?.let { message ->
                            { Text(message) }
                        },
                        enabled = !state.isSubmitting
                    )

                    Button(
                        onClick = {
                            val companyIdError = if (state.role == UserRole.SALES_AGENT) {
                                LoginFormValidator.validateCompanyId(state.companyId)
                            } else {
                                null
                            }
                            val error = companyIdError
                                ?: LoginFormValidator.validate(state.email, state.password)
                            if (error == null) {
                                state = state.copy(isSubmitting = true)
                                onLogin(
                                    state.companyId.trim(),
                                    state.email.trim(),
                                    state.password,
                                    state.role
                                ) { result ->
                                    result.onFailure { failure ->
                                        state = state.copy(
                                            isSubmitting = false,
                                            errorMessage = failure.message ?: "Sign-in failed. Try again."
                                        )
                                    }
                                }
                            } else {
                                state = state.copy(errorMessage = error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSubmitting,
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sign in")
                        }
                    }

                    if (state.role == UserRole.SALES_AGENT) {
                        androidx.compose.material3.TextButton(
                            onClick = onRegisterAgent,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSubmitting
                        ) {
                            Text("Create an agent account")
                        }
                    } else {
                        androidx.compose.material3.TextButton(
                            onClick = onRegisterAdmin,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSubmitting
                        ) {
                            Text("Register an administrator account")
                        }
                    }
                }
            }

            Text(
                text = if (state.role == UserRole.SALES_AGENT) {
                    "Use the company ID and invited work email from your administrator."
                } else {
                    "Admin access is granted by your organization after registration."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
private fun BrandMark() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = MaterialTheme.shapes.large
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SM",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (registered) "Registration complete" else title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (registered) {
                    Text(completionMessage, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = onBackToSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text("Back to sign in")
                    }
                } else {
                    Text(
                        instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (requiresCompanyId) {
                        OutlinedTextField(
                            value = companyId,
                            onValueChange = { companyId = it.trim(); errorMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Company ID") },
                            singleLine = true,
                            enabled = !isSubmitting
                        )
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !isSubmitting
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Choose a password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isSubmitting
                    )
                    OutlinedTextField(
                        value = confirmedPassword,
                        onValueChange = { confirmedPassword = it; errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isSubmitting
                    )

                    errorMessage?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        onClick = {
                            val emailError = LoginFormValidator.validateEmail(email)
                            val companyIdError = if (requiresCompanyId) {
                                LoginFormValidator.validateCompanyId(companyId)
                            } else {
                                null
                            }
                            val validationError = when {
                                companyIdError != null -> companyIdError
                                emailError != null -> emailError
                                password.length < MIN_PASSWORD_LENGTH ->
                                    "Use a password with at least $MIN_PASSWORD_LENGTH characters."
                                password != confirmedPassword -> "The passwords do not match."
                                else -> null
                            }
                            if (validationError != null) {
                                errorMessage = validationError
                            } else {
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
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting,
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Create account")
                        }
                    }

                    TextButton(
                        onClick = onBackToSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting
                    ) {
                        Text("Back to sign in")
                    }
                }
            }
        }
    }
}

private const val MIN_PASSWORD_LENGTH = 8

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
