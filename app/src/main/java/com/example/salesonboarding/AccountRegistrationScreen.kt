package com.example.salesonboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp

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
                            val validationError = when {
                                requiresCompanyId && !Regex("^[A-Za-z0-9_-]{3,64}$")
                                    .matches(companyId.trim()) ->
                                    "Enter the company ID provided by your administrator."
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
