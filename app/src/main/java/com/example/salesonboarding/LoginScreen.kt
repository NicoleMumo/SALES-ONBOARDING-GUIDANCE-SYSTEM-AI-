package com.example.salesonboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

data class LoginFormState(
    val email: String = "",
    val password: String = "",
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false
)

object LoginFormValidator {
    fun validate(email: String, password: String): String? {
        if (email.isBlank()) return "Enter your email address."
        if (!EMAIL_PATTERN.matches(email.trim())) {
            return "Enter a valid email address."
        }
        if (password.isBlank()) return "Enter your password."
        if (password.length < 8) return "Password must be at least 8 characters."
        return null
    }

    private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
}

@Composable
fun SalesAgentLoginScreen(
    onLogin: (email: String, password: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(LoginFormState()) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "SalesMesh",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Sales-agent sign in",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Sign in to start a customer onboarding session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = { state = state.copy(email = it, errorMessage = null) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !state.isSubmitting
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = { state = state.copy(password = it, errorMessage = null) },
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
                val error = LoginFormValidator.validate(state.email, state.password)
                if (error == null) {
                    state = state.copy(isSubmitting = true)
                    onLogin(state.email.trim(), state.password)
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

        Text(
            text = "Admin access is managed separately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
