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

    private val COMPANY_ID_PATTERN = Regex("^[A-Za-z0-9_-]{3,64}$")
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
