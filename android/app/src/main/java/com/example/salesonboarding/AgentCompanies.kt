package com.example.salesonboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A sales agent can work for several companies. After sign-in they choose the company they
 * are onboarding a client for; that company's message from its admin is shown next, then
 * its home screen. Its sessions, questions and settings are the only ones in use until the
 * agent switches company.
 */

/** The company the agent opened, with what the form and the message screen need. */
data class OpenedCompany(
    val companyId: String,
    val name: String,
    val industry: String,
    val sharesForTraining: Boolean,
    val message: String
)

/** Loads what opening [membership] needs. Fails if the company has not chosen its industry yet. */
fun openAgentCompany(
    store: AdminDataStore,
    membership: CompanyMembership,
    onResult: (Result<OpenedCompany>) -> Unit
) {
    val companyId = membership.companyId
    store.loadCompanyIndustry(companyId) { industryResult ->
        val industry = industryResult.getOrElse {
            onResult(Result.failure(IllegalStateException("Could not open ${membership.companyName}. Check the connection and try again.", it)))
            return@loadCompanyIndustry
        }
        if (industry == null) {
            onResult(
                Result.failure(
                    IllegalStateException("${membership.companyName} has not chosen its onboarding industry yet. Ask its administrator.")
                )
            )
            return@loadCompanyIndustry
        }
        // Sharing and the message are optional: if they cannot be read, they are left out.
        store.loadSharesForTraining(companyId) { shares ->
            store.loadCompanyBrief(companyId) { brief ->
                val loaded = brief.getOrDefault(CompanyBrief())
                onResult(
                    Result.success(
                        OpenedCompany(
                            companyId = companyId,
                            name = loaded.name.ifBlank { membership.companyName },
                            industry = industry,
                            sharesForTraining = shares.getOrDefault(false),
                            message = loaded.message
                        )
                    )
                )
            }
        }
    }
}

/** Shown when the admin has not written a message yet. */
internal const val DEFAULT_COMPANY_MESSAGE =
    "Your administrator has not written a message yet. Follow the company's usual onboarding steps: " +
        "the guide will show you what to ask next and what to fix."

/** "Which company is this client for?": one card per company, and a box to join another. */
@Composable
fun CompanyPickerScreen(
    companies: List<CompanyMembership>,
    openingCompanyId: String?,
    errorMessage: String?,
    isJoining: Boolean,
    onPick: (CompanyMembership) -> Unit,
    onJoin: (companyId: String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val c = Sm.colors
    var newCompanyId by remember { mutableStateOf("") }
    val busy = openingCompanyId != null || isJoining
    fun join() {
        if (!busy && COMPANY_ID_PATTERN.matches(newCompanyId.trim())) onJoin(newCompanyId.trim())
    }
    AppScreen(
        title = "Your companies",
        subtitle = "Which company is this client for?",
        leading = if (onBack == null) ({ AppMark() }) else null,
        onBack = onBack,
        actions = { HeaderButton(R.drawable.ic_sm_sign_out, "Sign out", onSignOut) },
        modifier = modifier
    ) {
        errorMessage?.let { Banner(it, Tone.Negative) }
        companies.forEach { company ->
            CompanyCard(
                company = company,
                isOpening = openingCompanyId == company.companyId,
                enabled = !busy,
                onClick = { onPick(company) }
            )
        }
        SectionCard(
            title = "Join another company",
            subtitle = "Enter the company ID its administrator gave you. It must have invited your email."
        ) {
            OutlinedTextField(
                value = newCompanyId,
                onValueChange = { newCompanyId = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Company ID") },
                singleLine = true,
                enabled = !busy,
                isError = newCompanyId.isNotEmpty() && !COMPANY_ID_PATTERN.matches(newCompanyId),
                supportingText = { Text("3 to 64 letters, digits, _ or -") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { join() }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.ink,
                    unfocusedBorderColor = c.border,
                    errorBorderColor = c.cherry,
                    focusedTextColor = c.ink,
                    unfocusedTextColor = c.ink,
                    cursorColor = c.ink,
                    focusedLabelColor = c.ink,
                    unfocusedLabelColor = c.muted
                )
            )
            SecondaryButton(
                text = if (isJoining) "Joining…" else "Join company",
                onClick = ::join,
                enabled = !busy && COMPANY_ID_PATTERN.matches(newCompanyId)
            )
        }
    }
}

@Composable
private fun CompanyCard(company: CompanyMembership, isOpening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Sm.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = c.cream,
        border = BorderStroke(1.5.dp, c.line)
    ) {
        Row(
            Modifier.padding(horizontal = Space.l, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m)
        ) {
            Column(Modifier.weight(1f)) {
                Text(company.companyName, style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text("Company ID: ${company.companyId}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            if (isOpening) {
                CircularProgressIndicator(color = c.ink, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                SmIcon(R.drawable.ic_sm_arrow, c.ink)
            }
        }
    }
}

/** The company's message to its agents, shown each time the agent opens that company. */
@Composable
fun CompanyBriefScreen(
    company: OpenedCompany,
    onStart: () -> Unit,
    onSwitchCompany: (() -> Unit)?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Sm.colors
    AppScreen(
        title = company.name,
        subtitle = if (company.industry == "banking") "Banking onboarding" else "Telecom onboarding",
        onBack = onSwitchCompany,
        leading = if (onSwitchCompany == null) ({ AppMark() }) else null,
        actions = { HeaderButton(R.drawable.ic_sm_sign_out, "Sign out", onSignOut) },
        bottomBar = {
            PrimaryButton(text = "Start working for ${company.name}", onClick = onStart)
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            GuideMascot(GuideMood.HAPPY, Modifier.size(56.dp))
            Text(
                "A message from ${company.name}",
                style = MaterialTheme.typography.titleLarge,
                color = c.ink
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = c.cream,
            border = BorderStroke(1.5.dp, c.line)
        ) {
            Text(
                company.message.ifBlank { DEFAULT_COMPANY_MESSAGE },
                style = MaterialTheme.typography.bodyLarge,
                color = c.ink,
                modifier = Modifier.padding(Space.l)
            )
        }
        Text(
            "Sessions, questions and settings now come from ${company.name}. " +
                if (onSwitchCompany != null) "Use the back arrow to choose another company." else "",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted
        )
    }
}

@Preview(name = "Company message", showBackground = true, widthDp = 384, heightDp = 854)
@Composable
private fun CompanyBriefPreview() {
    SalesOnboardingTheme {
        CompanyBriefScreen(
            company = OpenedCompany(
                "acme_finance", "Acme Finance", "banking", false,
                "Welcome. We open savings accounts for walk-in clients. Check the national ID first, then the phone number."
            ),
            onStart = {},
            onSwitchCompany = {},
            onSignOut = {}
        )
    }
}

@Preview(name = "Company picker", showBackground = true, widthDp = 384, heightDp = 854)
@Composable
private fun CompanyPickerPreview() {
    SalesOnboardingTheme {
        CompanyPickerScreen(
            companies = listOf(
                CompanyMembership("acme_finance", "mary@example.com", "Mary", true, "Acme Finance"),
                CompanyMembership("beta_telecom", "mary@example.com", "Mary", true, "Beta Telecom")
            ),
            openingCompanyId = null,
            errorMessage = null,
            isJoining = false,
            onPick = {},
            onJoin = {},
            onSignOut = {}
        )
    }
}
