package com.example.salesonboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// region Colours
//
// Green is the app's own colour (trust, completion). Indigo is kept for the AI's
// suggestions only, amber for risk and coaching, red for errors, so each colour
// always means the same thing. Every text pair is at least 4.5:1 (WCAG AA), because
// agents often work outdoors on mid-range phones.

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3F1E4),
    onPrimaryContainer = Color(0xFF053A2A),
    secondary = Color(0xFF4338CA),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7E5FB),
    onSecondaryContainer = Color(0xFF211B6B),
    tertiary = Color(0xFFA14C06),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCEBD2),
    onTertiaryContainer = Color(0xFF552600),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE8E6),
    onErrorContainer = Color(0xFF7A1209),
    background = Color(0xFFF5F7F5),
    onBackground = Color(0xFF15201B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15201B),
    surfaceVariant = Color(0xFFEBEFEC),
    onSurfaceVariant = Color(0xFF4A5651),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FBF9),
    surfaceContainer = Color(0xFFF1F4F2),
    surfaceContainerHigh = Color(0xFFEBEFEC),
    surfaceContainerHighest = Color(0xFFE4E9E6),
    outline = Color(0xFFB8C2BD),
    outlineVariant = Color(0xFFDDE3E0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF63D3A8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0F5139),
    onPrimaryContainer = Color(0xFFC4F0DC),
    secondary = Color(0xFFABA6FF),
    onSecondary = Color(0xFF1E1873),
    secondaryContainer = Color(0xFF2F2A85),
    onSecondaryContainer = Color(0xFFE3E1FF),
    tertiary = Color(0xFFF7B267),
    onTertiary = Color(0xFF4A2100),
    tertiaryContainer = Color(0xFF6B3405),
    onTertiaryContainer = Color(0xFFFDDDB6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFE2E9E5),
    surface = Color(0xFF161D1A),
    onSurface = Color(0xFFE2E9E5),
    surfaceVariant = Color(0xFF232C28),
    onSurfaceVariant = Color(0xFFA9B4AF),
    surfaceContainerLowest = Color(0xFF0B100E),
    surfaceContainerLow = Color(0xFF131A17),
    surfaceContainer = Color(0xFF1A221E),
    surfaceContainerHigh = Color(0xFF232C28),
    surfaceContainerHighest = Color(0xFF2D3732),
    outline = Color(0xFF5E6A65),
    outlineVariant = Color(0xFF333D39)
)

// Chart marks (bars, lines, meter fill) use their own green, one step off the app green:
// as a chart mark the button green reads slightly grey in light mode (OKLCH chroma 0.094,
// floor 0.10) and too pale in dark mode (lightness 0.79, band 0.48-0.67). Both of these
// pass the dataviz checks (lightness band, chroma floor, contrast >= 3:1) on the card surface.
private val ChartGreenLight = Color(0xFF0E7C57)
private val ChartGreenDark = Color(0xFF2FA97C)

/** The colour for chart marks in the current mode. Text never uses it (it uses text colours). */
@Composable
fun chartMarkColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) ChartGreenDark else ChartGreenLight

// endregion

// region Type, shapes and spacing

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** The spacing steps used across the app, so screens line up with each other. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Buttons and tappable rows are at least this tall, above the 48 dp touch minimum. */
private val ActionHeight = 52.dp

// endregion

// region Theme

@Composable
fun SalesOnboardingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

/** What a message or label means. Each tone always uses the same colours. */
enum class Tone { Neutral, Positive, Ai, Caution, Negative }

private data class ToneColors(val container: Color, val content: Color, val icon: ImageVector)

@Composable
private fun Tone.colors(): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        Tone.Neutral -> ToneColors(scheme.surfaceVariant, scheme.onSurfaceVariant, Icons.Filled.Info)
        Tone.Positive -> ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Filled.CheckCircle)
        Tone.Ai -> ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Filled.Star)
        Tone.Caution -> ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Filled.Warning)
        Tone.Negative -> ToneColors(scheme.errorContainer, scheme.onErrorContainer, Icons.Filled.Warning)
    }
}

// endregion

// region Screen layout

/**
 * The frame every screen uses: a header, the content, and an optional action area pinned
 * to the bottom so the main button is always in reach. Set [scrollable] to false when the
 * content scrolls by itself (a LazyColumn).
 */
@Composable
fun AppScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // The phone's Back button does what the header's back arrow does.
    if (onBack != null) BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Keep the content and the bottom action above the keyboard. The navigation bar
            // is already padded by the activity's Scaffold, so it is not counted twice.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
    ) {
        ScreenHeader(title = title, subtitle = subtitle, onBack = onBack, actions = actions)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
                .padding(horizontal = Space.l, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.l),
            content = content
        )
        if (bottomBar != null) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.l, vertical = Space.m),
                        verticalArrangement = Arrangement.spacedBy(Space.s),
                        content = bottomBar
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (onBack == null) Space.l else Space.xs, end = Space.s, top = Space.m, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

// endregion

// region Building blocks

/** The SalesMesh mark (the launcher icon's artwork) on the app green. */
@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary)
        )
    }
}

/** The main action on a screen. Shows a spinner instead of an icon while [isLoading]. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ActionHeight),
        shape = MaterialTheme.shapes.medium
    ) {
        ButtonContent(text, icon, isLoading, MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ActionHeight),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        ButtonContent(text, icon, isLoading = false, spinnerColor = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, isLoading: Boolean, spinnerColor: Color) {
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = spinnerColor, strokeWidth = 2.dp)
        Spacer(Modifier.width(Space.s))
    } else if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.s))
    }
    Text(text, style = MaterialTheme.typography.labelLarge)
}

/** A titled group of content on a white card. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.m)
        ) {
            if (title != null || action != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (title != null) {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    action?.invoke()
                }
            }
            content()
        }
    }
}

/** A short label such as "Required", "Suggested next" or "High risk". */
@Composable
fun StatusPill(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    showIcon: Boolean = false
) {
    val colors = tone.colors()
    Row(
        modifier = modifier
            .background(colors.container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        if (showIcon) {
            Icon(colors.icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(14.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.content)
    }
}

/** A message across the width of the screen: a warning, a tip, an AI suggestion, a success. */
@Composable
fun Banner(
    message: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    val colors = tone.colors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.container,
        contentColor = colors.content
    ) {
        Row(
            modifier = Modifier.padding(Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.m)
        ) {
            Icon(colors.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                Text(message, style = MaterialTheme.typography.bodyMedium)
                action?.invoke()
            }
        }
    }
}

/** Shown when a list has nothing in it yet, with what to do next. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxl, horizontal = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        action?.invoke()
    }
}

/** A centred spinner with a short explanation, for waits of more than a moment. */
@Composable
fun LoadingState(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.m, Alignment.CenterVertically)
    ) {
        CircularProgressIndicator()
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// endregion

// region Previews

@Composable
private fun UiGallery() {
    AppScreen(
        title = "Building blocks",
        subtitle = "Every shared part, in one place",
        onBack = {},
        bottomBar = {
            PrimaryButton("Continue", onClick = {})
            SecondaryButton("Save for later", onClick = {})
        }
    ) {
        SectionCard(title = "Customer details", subtitle = "4 of 6 answered") {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                StatusPill("Required", Tone.Neutral)
                StatusPill("Suggested next", Tone.Ai, showIcon = true)
                StatusPill("Done", Tone.Positive, showIcon = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                StatusPill("Medium risk", Tone.Caution)
                StatusPill("Needs a fix", Tone.Negative)
            }
        }
        Banner("The AI suggests asking for the ID number next.", Tone.Ai, title = "Suggested next")
        Banner("This customer may stop before finishing. Keep the next steps short.", Tone.Caution)
        Banner("Enter a valid Kenyan phone number.", Tone.Negative)
        Banner("Session saved.", Tone.Positive)
        PrimaryButton("Submitting", onClick = {}, isLoading = true)
        EmptyState(title = "No sessions yet", message = "Sessions you start will appear here.")
    }
}

@Preview(name = "Building blocks, light", showBackground = true, heightDp = 1100)
@Composable
private fun UiGalleryLightPreview() {
    SalesOnboardingTheme(darkTheme = false) { UiGallery() }
}

@Preview(name = "Building blocks, dark", showBackground = true, heightDp = 1100)
@Composable
private fun UiGalleryDarkPreview() {
    SalesOnboardingTheme(darkTheme = true) { UiGallery() }
}

// endregion
