package com.example.salesonboarding

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
    val colors = LocalSignInColors.current
    val fonts = rememberSignInFonts()

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

    DarkSystemBarIcons()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.window)
    ) {
        // On short screens the photo gives up height first, so the form keeps its room.
        val photoHeight = (maxHeight * 0.5f).coerceIn(200.dp, 420.dp)
        val sheetTop = photoHeight - 40.dp
        val screenHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                // The sheet pads the navigation bar itself, so only the rest of the keyboard is added.
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                .verticalScroll(rememberScrollState())
        ) {
            Box(Modifier.fillMaxWidth()) {
                SignInPhoto(photoHeight, colors, fonts)
                Column(
                    modifier = Modifier
                        .padding(top = sheetTop)
                        .fillMaxWidth()
                        .heightIn(min = screenHeight - sheetTop)
                        .shadow(24.dp, SheetShape, clip = false)
                        .background(colors.sheet, SheetShape)
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    RoleSwitch(state.role, enabled, colors, fonts) { role ->
                        state = state.copy(role = role, errorMessage = null)
                        onRoleSelected(role)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Agents sign in to a company, so the login logic needs its ID.
                        if (isAgent) {
                            SignInField(
                                label = "Company ID",
                                value = state.companyId,
                                onValueChange = { state = state.copy(companyId = it.trim(), errorMessage = null) },
                                icon = Icons.Outlined.Home,
                                enabled = enabled,
                                colors = colors,
                                fonts = fonts
                            )
                        }
                        SignInField(
                            label = "Email",
                            value = state.email,
                            onValueChange = { state = state.copy(email = it, errorMessage = null) },
                            icon = Icons.Outlined.Email,
                            enabled = enabled,
                            colors = colors,
                            fonts = fonts,
                            keyboardType = KeyboardType.Email
                        )
                        SignInField(
                            label = "Password",
                            value = state.password,
                            onValueChange = { state = state.copy(password = it, errorMessage = null) },
                            icon = Icons.Outlined.Lock,
                            enabled = enabled,
                            colors = colors,
                            fonts = fonts,
                            isPassword = true,
                            onDone = ::submit
                        )
                    }
                    state.errorMessage?.let { message ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = colors.action, modifier = Modifier.size(18.dp))
                            Text(message, color = colors.action, style = TextStyle(fontFamily = fonts.text, fontWeight = FontWeight.Medium, fontSize = 14.sp))
                        }
                    }
                    SignInButton(state.isSubmitting, colors, fonts, onClick = ::submit)
                    if (!isAgent) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = enabled, role = Role.Button, onClick = onRegisterAdmin)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Register",
                                    color = colors.action,
                                    style = TextStyle(fontFamily = fonts.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SheetShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)

/** The photo, its tint, and the logo with the wordmark in the top-left corner. */
@Composable
private fun SignInPhoto(height: Dp, colors: SignInColors, fonts: SignInFonts) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Image(
            painter = painterResource(R.drawable.sign_in_photo),
            contentDescription = null, // decorative
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(horizontalBias = 0f, verticalBias = 0.32f), // 50% across, 66% down
            modifier = Modifier.matchParentSize()
        )
        Box(
            Modifier
                .matchParentSize()
                .background(colors.photoTint)
        )
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Row(
            modifier = Modifier.padding(top = max(56.dp, statusBar + 12.dp), start = 28.dp, end = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.ink, RoundedCornerShape(14.dp))
                    .clearAndSetSemantics { }, // the wordmark next to it is read instead
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "SM",
                    color = colors.accent,
                    style = TextStyle(
                        fontFamily = fonts.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.04.em
                    )
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "SalesMesh",
                color = colors.ink,
                style = TextStyle(
                    fontFamily = fonts.wordmark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = 0.01.em
                ),
                modifier = Modifier.semantics { heading() }
            )
        }
    }
}

/** Sales agent or administrator: two equal buttons, 46 dp tall with a 48 dp touch area. */
@Composable
private fun RoleSwitch(
    selected: UserRole,
    enabled: Boolean,
    colors: SignInColors,
    fonts: SignInFonts,
    onSelect: (UserRole) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(UserRole.SALES_AGENT, UserRole.ADMIN).forEach { role ->
            val isSelected = role == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .heightIn(min = 46.dp)
                    .clip(shape)
                    .background(if (isSelected) colors.ink else Color.Transparent)
                    .border(1.5.dp, colors.ink, shape)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(role) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    role.label,
                    color = if (isSelected) colors.onInk else colors.ink,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = fonts.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                )
            }
        }
    }
}

/**
 * A white field with an icon badge and a small label above the input. Tapping anywhere
 * on it starts typing. The password field has a Show/Hide button.
 */
@Composable
private fun SignInField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    enabled: Boolean,
    colors: SignInColors,
    fonts: SignInFonts,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onDone: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clip(shape)
            .background(colors.field)
            .border(if (focused) 2.dp else 1.5.dp, if (focused) colors.ink else colors.fieldBorder, shape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusRequester.requestFocus() }
            .padding(start = 12.dp, end = if (isPassword) 10.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label.uppercase(Locale.ROOT),
                color = colors.fieldLabel,
                style = TextStyle(
                    fontFamily = fonts.text,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.08.em
                ),
                modifier = Modifier.clearAndSetSemantics { } // the input below carries the label
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = fonts.text,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = colors.ink
                ),
                cursorBrush = SolidColor(colors.ink),
                visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                    imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = {
                        focusManager.clearFocus()
                        onDone?.invoke()
                    }
                )
            )
        }
        if (isPassword) {
            val action = if (visible) "Hide password" else "Show password"
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .sizeIn(minWidth = 60.dp, minHeight = 44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.subtleFill)
                    .clickable(enabled = enabled, role = Role.Button) { visible = !visible }
                    .clearAndSetSemantics { contentDescription = action }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (visible) "Hide" else "Show",
                    color = colors.ink,
                    style = TextStyle(fontFamily = fonts.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                )
            }
        }
    }
}

/** "Sign in" on the left; a lime square with an arrow (a spinner while signing in) on the right. */
@Composable
private fun SignInButton(isSubmitting: Boolean, colors: SignInColors, fonts: SignInFonts, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(colors.action)
            .clickable(enabled = !isSubmitting, role = Role.Button, onClick = onClick)
            .semantics { if (isSubmitting) stateDescription = "Signing in" }
            .padding(start = 28.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Sign in",
            color = colors.onAction,
            style = TextStyle(
                fontFamily = fonts.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                letterSpacing = 0.03.em
            ),
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .size(44.dp)
                .background(colors.accent, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(color = colors.ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * The photo and the cream sheet are light in both modes, so the status and navigation
 * bar icons stay dark while this screen is shown, and go back to normal after.
 */
@Composable
private fun DarkSystemBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatusBefore = controller?.isAppearanceLightStatusBars
        val lightNavigationBefore = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            lightStatusBefore?.let { controller.isAppearanceLightStatusBars = it }
            lightNavigationBefore?.let { controller.isAppearanceLightNavigationBars = it }
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
