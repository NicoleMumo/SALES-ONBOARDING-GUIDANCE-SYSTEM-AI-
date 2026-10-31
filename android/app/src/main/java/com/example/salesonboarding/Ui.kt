package com.example.salesonboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat

// region Colours
//
// The whole app's colours live here and nowhere else. A second theme is another
// SmPalette instance, provided in SalesOnboardingTheme. Every text pair is at least 4.5:1.

@Immutable
data class SmPalette(
    /** Main text, dark buttons, the bottom-bar pill. */
    val ink: Color,
    /** Small grey-green text. */
    val muted: Color,
    /** Slightly stronger secondary text (subtitles under titles). */
    val mutedStrong: Color,
    /** Header and card background. */
    val cream: Color,
    /** Screen background. */
    val white: Color,
    /** Icons on dark buttons, progress. */
    val lime: Color,
    /** A softer lime for icon badges. */
    val limeSoft: Color,
    /** Header line, hairlines. */
    val line: Color,
    /** The empty part of a bar. */
    val track: Color,
    val teal: Color,
    val tealTrack: Color,
    /** The one attention colour: main action, unfinished, errors. */
    val cherry: Color,
    val cherryTrack: Color,
    /** Text on [cherryTrack]. */
    val cherryDeep: Color,
    /** Card and button outlines. */
    val border: Color,
    /** Thin dividers inside lists. */
    val divider: Color,
    /** The line above a footer. */
    val footerLine: Color,
    /** A disabled main button. */
    val disabledFill: Color,
    val disabledMark: Color,
    /** Switch track when off, check box outline. */
    val switchOff: Color,
    /** The dim behind a bottom sheet. */
    val scrim: Color,
    /** The window colour on the sign-in screen, around the photo. */
    val window: Color
) {
    companion object {
        val TealAndCherry = SmPalette(
            ink = Color(0xFF123636),
            muted = Color(0xFF4A6663),
            mutedStrong = Color(0xFF34524F),
            cream = Color(0xFFFEFFE0),
            white = Color(0xFFFFFFFF),
            lime = Color(0xFFD0E86E),
            limeSoft = Color(0xFFE4EDB8),
            line = Color(0xFFE3E8B5),
            track = Color(0xFFEEF3C9),
            teal = Color(0xFF20A898),
            tealTrack = Color(0xFFDDF0EC),
            cherry = Color(0xFFB5185F),
            cherryTrack = Color(0xFFF3DCE6),
            cherryDeep = Color(0xFF8A1248),
            border = Color(0xFFD3DAB0),
            divider = Color(0xFFEEF1D6),
            footerLine = Color(0xFFE8ECCB),
            disabledFill = Color(0xFFDCE3B5),
            disabledMark = Color(0xFFC4CC95),
            switchOff = Color(0xFFC9D2A0),
            scrim = Color(0xFF123636).copy(alpha = 0.45f),
            window = Color(0xFF1C4747)
        )
    }
}

val LocalSm = staticCompositionLocalOf { SmPalette.TealAndCherry }

/** `Sm.colors.ink` and so on, from inside a composable. */
object Sm {
    val colors: SmPalette
        @Composable @ReadOnlyComposable get() = LocalSm.current
}

/** Colours of the sign-in screen, which has its own layout (photo, cream sheet). */
@Immutable
data class SignInColors(
    val window: Color,
    val photoTint: Color,
    val ink: Color,
    val onInk: Color,
    val accent: Color,
    val sheet: Color,
    val field: Color,
    val fieldBorder: Color,
    val fieldLabel: Color,
    val action: Color,
    val onAction: Color,
    val subtleFill: Color
) {
    companion object {
        fun from(palette: SmPalette) = SignInColors(
            window = palette.window,
            photoTint = palette.window.copy(alpha = 0.14f),
            ink = palette.ink,
            onInk = palette.cream,
            accent = palette.lime,
            sheet = palette.cream,
            field = palette.white,
            fieldBorder = palette.ink.copy(alpha = 0.16f),
            fieldLabel = palette.muted,
            action = palette.cherry,
            onAction = palette.white,
            subtleFill = palette.ink.copy(alpha = 0.08f)
        )
    }
}

val LocalSignInColors = staticCompositionLocalOf { SignInColors.from(SmPalette.TealAndCherry) }

/** Chart marks use the bright teal (see [SmPalette.teal]). */
@Composable
fun chartMarkColor(): Color = Sm.colors.teal

// endregion

// region Fonts, type, shapes and spacing

/**
 * Jost is the app's only font, bundled in res/font (SIL Open Font License; the licence is
 * in assets/licenses): Regular 400, Medium 500, SemiBold 600. If a font file cannot be
 * read, a system sans-serif is used instead.
 */
val LocalJost = compositionLocalOf<FontFamily> { FontFamily.SansSerif }

@Composable
private fun rememberJost(): FontFamily {
    val context = LocalContext.current
    return remember(context) {
        fun loads(id: Int) = try {
            ResourcesCompat.getFont(context, id) != null
        } catch (_: RuntimeException) {
            false
        }
        val files = listOf(R.font.jost_regular, R.font.jost_medium, R.font.jost_semibold)
        if (files.all(::loads)) {
            FontFamily(
                Font(R.font.jost_regular, FontWeight.Normal),
                Font(R.font.jost_medium, FontWeight.Medium),
                Font(R.font.jost_semibold, FontWeight.SemiBold)
            )
        } else {
            FontFamily.SansSerif
        }
    }
}

/** The sign-in screen's two fonts; both are Jost now. */
@Immutable
data class SignInFonts(val wordmark: FontFamily, val text: FontFamily)

@Composable
fun rememberSignInFonts(): SignInFonts = SignInFonts(LocalJost.current, LocalJost.current)

private fun typography(jost: FontFamily): Typography {
    fun style(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
        fontFamily = jost,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = spacing.sp
    )
    return Typography(
        displaySmall = style(64, 66, FontWeight.SemiBold, -1.0),
        headlineLarge = style(32, 34, FontWeight.SemiBold, 0.16),
        headlineMedium = style(26, 32, FontWeight.SemiBold),
        headlineSmall = style(22, 28, FontWeight.SemiBold),
        titleLarge = style(20, 26, FontWeight.SemiBold),
        titleMedium = style(18, 24, FontWeight.Medium),
        titleSmall = style(16, 22, FontWeight.SemiBold),
        bodyLarge = style(16, 22, FontWeight.Normal),
        bodyMedium = style(15, 21, FontWeight.Normal),
        bodySmall = style(13, 18, FontWeight.Normal),
        labelLarge = style(17, 22, FontWeight.SemiBold, 0.34),
        labelMedium = style(14, 18, FontWeight.Medium),
        labelSmall = style(12, 16, FontWeight.Medium)
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** A small caps label such as "CUSTOMER TYPE". */
@Composable
fun smCapsStyle(): TextStyle = TextStyle(
    fontFamily = LocalJost.current,
    fontSize = 13.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.3.sp,
    color = Sm.colors.mutedStrong
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

/** The cream header is always this tall (status bar included on a normal phone). */
private val HeaderHeight = 132.dp

// endregion

// region Theme

@Composable
fun SalesOnboardingTheme(
    palette: SmPalette = SmPalette.TealAndCherry,
    content: @Composable () -> Unit
) {
    val jost = rememberJost()
    val scheme = lightColorScheme(
        primary = palette.cherry,
        onPrimary = palette.white,
        primaryContainer = palette.limeSoft,
        onPrimaryContainer = palette.ink,
        secondary = palette.teal,
        onSecondary = palette.white,
        secondaryContainer = palette.tealTrack,
        onSecondaryContainer = palette.ink,
        tertiary = palette.ink,
        onTertiary = palette.lime,
        tertiaryContainer = palette.track,
        onTertiaryContainer = palette.ink,
        error = palette.cherry,
        onError = palette.white,
        errorContainer = palette.cherryTrack,
        onErrorContainer = palette.cherryDeep,
        background = palette.white,
        onBackground = palette.ink,
        surface = palette.white,
        onSurface = palette.ink,
        surfaceVariant = palette.track,
        onSurfaceVariant = palette.muted,
        surfaceContainerLowest = palette.white,
        surfaceContainerLow = palette.white,
        surfaceContainer = palette.cream,
        surfaceContainerHigh = palette.track,
        surfaceContainerHighest = palette.track,
        outline = palette.border,
        outlineVariant = palette.line,
        scrim = palette.scrim
    )
    CompositionLocalProvider(
        LocalSm provides palette,
        LocalSignInColors provides SignInColors.from(palette),
        LocalJost provides jost
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(jost),
            shapes = AppShapes,
            content = content
        )
    }
}

/** What a message or label means. Each tone always uses the same colours. */
enum class Tone { Neutral, Positive, Ai, Caution, Negative }

private data class ToneColors(val container: Color, val content: Color, val mark: Color, @DrawableRes val icon: Int)

@Composable
private fun Tone.colors(): ToneColors {
    val c = Sm.colors
    return when (this) {
        Tone.Neutral -> ToneColors(c.track, c.ink, c.muted, R.drawable.ic_sm_info)
        Tone.Positive -> ToneColors(c.tealTrack, c.ink, c.teal, R.drawable.ic_sm_check)
        Tone.Ai -> ToneColors(c.cream, c.ink, c.cherry, R.drawable.ic_sm_plus)
        Tone.Caution -> ToneColors(c.limeSoft, c.ink, c.ink, R.drawable.ic_sm_alert)
        Tone.Negative -> ToneColors(c.cherryTrack, c.cherryDeep, c.cherry, R.drawable.ic_sm_alert)
    }
}

// endregion

// region Icons and the brand mark

/** One of the design's stroke icons (`R.drawable.ic_sm_*`), tinted. */
@Composable
fun SmIcon(
    @DrawableRes icon: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    description: String? = null
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/** The SalesMesh mark: "SM" in lime on an ink square. */
@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val c = Sm.colors
    Box(
        modifier = modifier
            .size(size)
            .background(c.ink, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "SM",
            color = c.lime,
            style = TextStyle(
                fontFamily = LocalJost.current,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.34f).sp,
                letterSpacing = 0.04.sp * (size.value * 0.34f / 15f)
            )
        )
    }
}

// endregion

// region Screen layout

/**
 * The frame every screen uses: the cream header, a white page, and an optional footer that
 * stays in reach. Set [scrollable] to false when the content scrolls by itself.
 */
@Composable
fun AppScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // The phone's Back button does what the header's back arrow does.
    if (onBack != null) BackHandler(onBack = onBack)
    val c = Sm.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.white)
            // Keep the content and the footer above the keyboard. The navigation bar is
            // already padded by the activity's Scaffold, so it is not counted twice.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
    ) {
        ScreenHeader(title = title, subtitle = subtitle, onBack = onBack, leading = leading, actions = actions)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
                .padding(horizontal = Space.xl, vertical = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.l),
            content = content
        )
        if (bottomBar != null) {
            Surface(
                color = c.white,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                border = BorderStroke(1.dp, c.footerLine)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.xl, end = Space.xl, top = 14.dp, bottom = Space.l),
                    verticalArrangement = Arrangement.spacedBy(Space.m),
                    content = bottomBar
                )
            }
        }
    }
}

/**
 * The cream bar at the top of every screen: 132 dp tall, rounded at the bottom, with a thin
 * line under it. A back arrow on the left (agent pages) or round buttons on the right.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    /** Something before the title, such as the brand mark on the agent's home screen. */
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = Sm.colors
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val top = max(52.dp, statusBar + 8.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = c.cream,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        border = BorderStroke(1.dp, c.line)
    ) {
        Row(
            modifier = Modifier
                .height(top + 80.dp)
                .padding(start = if (onBack == null) Space.xl else 20.dp, end = 20.dp, top = top),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                HeaderButton(R.drawable.ic_sm_back, "Back", onBack)
                Spacer(Modifier.width(14.dp))
            }
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = c.mutedStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), content = actions)
        }
    }
}

/** A round 48 dp button for the header: ink with a lime icon. */
@Composable
fun HeaderButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val c = Sm.colors
    Box(
        modifier = modifier
            .size(48.dp)
            .background(if (enabled) c.ink else c.ink.copy(alpha = 0.5f), CircleShape)
            .clickable(enabled = enabled && !isLoading, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = c.lime, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            SmIcon(icon, c.lime)
        }
    }
}

/** One tab of the admin's bottom bar. */
data class SmNavItem(val label: String, @DrawableRes val icon: Int)

/** Five icons with labels; the current one sits in an ink pill with a lime icon. */
@Composable
fun SmBottomBar(items: List<SmNavItem>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Sm.colors
    Column(Modifier.background(c.white)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.footerLine)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Space.s)
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(selected = isSelected, role = Role.Tab) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 30.dp)
                            .background(if (isSelected) c.ink else Color.Transparent, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        SmIcon(item.icon, if (isSelected) c.lime else c.muted)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (isSelected) c.ink else c.muted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// endregion

// region Building blocks

/**
 * The main action on a screen: cherry, 60 dp, the words on the left and a lime square with
 * an arrow on the right. Disabled it turns pale. A spinner replaces the arrow while [isLoading].
 * [iconRes] changes the arrow (for example to a tick or a plus).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    @DrawableRes iconRes: Int = R.drawable.ic_sm_arrow,
    icon: ImageVector? = null
) {
    val c = Sm.colors
    val active = enabled && !isLoading
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(if (enabled) c.cherry else c.disabledFill, RoundedCornerShape(16.dp))
            .clickable(enabled = active, role = Role.Button, onClick = onClick)
            .padding(start = 28.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) c.white else c.muted,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .size(44.dp)
                .background(if (enabled) c.lime else c.disabledMark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = c.ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                icon != null -> Icon(rememberVectorPainter(icon), null, tint = if (enabled) c.ink else c.muted, modifier = Modifier.size(20.dp))
                else -> SmIcon(iconRes, if (enabled) c.ink else c.muted, size = 20.dp)
            }
        }
    }
}

/** A quieter action: white, thin outline, 56 dp. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null
) {
    val c = Sm.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        color = c.white,
        border = BorderStroke(1.5.dp, c.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally)
        ) {
            if (icon != null) Icon(rememberVectorPainter(icon), null, tint = c.ink, modifier = Modifier.size(20.dp))
            if (iconRes != null) SmIcon(iconRes, c.ink, size = 20.dp)
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) c.ink else c.muted
            )
        }
    }
}

/** A round filter chip: ink when selected, outlined when not. */
@Composable
fun SmChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null
) {
    val c = Sm.colors
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .minimumInteractiveComponentSize()
            .semantics { this.selected = selected },
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) c.ink else c.white,
        contentColor = if (selected) c.cream else c.ink,
        border = BorderStroke(1.5.dp, if (selected) c.ink else c.border)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) SmIcon(icon, if (selected) c.lime else c.ink, size = 18.dp)
            Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** The design's switch: bright teal when on, pale olive when off. */
@Composable
fun SmSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = Sm.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.white,
            checkedTrackColor = c.teal,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = c.white,
            uncheckedTrackColor = c.switchOff,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedTrackColor = c.teal.copy(alpha = 0.5f),
            disabledUncheckedTrackColor = c.switchOff.copy(alpha = 0.5f)
        )
    )
}

/** A white bottom sheet with a grab handle, a title, a close button, and a dimmed page behind. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SmSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Sm.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.white,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(c.border, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.xl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center
                ) { SmIcon(R.drawable.ic_sm_close, c.ink) }
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.m),
                content = content
            )
        }
    }
}

/** A titled group of content on a white card with a thin outline. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Sm.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = c.white,
        border = BorderStroke(1.5.dp, c.border)
    ) {
        Column(
            modifier = Modifier.padding(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.m)
        ) {
            if (title != null || action != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (title != null) {
                            Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
                        }
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                    action?.invoke()
                }
            }
            content()
        }
    }
}

/** A short label such as "Required", "Suggested next" or "Paused". */
@Composable
fun StatusPill(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    showIcon: Boolean = false
) {
    val t = tone.colors()
    Row(
        modifier = modifier
            .background(t.container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        if (showIcon) SmIcon(t.icon, t.mark, size = 14.dp)
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = t.content)
    }
}

/** A message across the width of the screen: a warning, a tip, a suggestion, a success. */
@Composable
fun Banner(
    message: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    val t = tone.colors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = t.container,
        contentColor = t.content,
        border = BorderStroke(1.dp, Sm.colors.line)
    ) {
        Row(
            modifier = Modifier.padding(Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.m)
        ) {
            SmIcon(t.icon, t.mark, size = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
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
    @DrawableRes iconRes: Int = R.drawable.ic_sm_info,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null
) {
    val c = Sm.colors
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
                .background(c.track, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Icon(rememberVectorPainter(icon), null, tint = c.muted)
            else SmIcon(iconRes, c.muted, size = 26.dp)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
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
    val c = Sm.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.white)
            .padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.m, Alignment.CenterVertically)
    ) {
        CircularProgressIndicator(color = c.teal)
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
        }
    }
}

// endregion

// region Preview

@Composable
private fun UiGallery() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AppScreen(
                title = "Building blocks",
                subtitle = "Every shared part",
                onBack = {},
                actions = {
                    HeaderButton(R.drawable.ic_sm_refresh, "Refresh", {})
                    HeaderButton(R.drawable.ic_sm_sign_out, "Sign out", {})
                },
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
                        SmChip("All 7", selected = true, onClick = {})
                        SmChip("Paused", selected = false, onClick = {})
                        SmSwitch(checked = true, onCheckedChange = {})
                        SmSwitch(checked = false, onCheckedChange = {})
                    }
                }
                Banner("The AI suggests asking for the ID number next.", Tone.Ai, title = "Suggested next")
                Banner("This customer may stop before finishing.", Tone.Caution)
                Banner("Enter a valid Kenyan phone number.", Tone.Negative)
                Banner("Session saved.", Tone.Positive)
                PrimaryButton("Not ready yet", onClick = {}, enabled = false)
            }
        }
        SmBottomBar(
            listOf(
                SmNavItem("Overview", R.drawable.ic_sm_overview),
                SmNavItem("Agents", R.drawable.ic_sm_agents),
                SmNavItem("Sessions", R.drawable.ic_sm_sessions),
                SmNavItem("Questions", R.drawable.ic_sm_questions),
                SmNavItem("Company", R.drawable.ic_sm_company)
            ),
            selected = tab,
            onSelect = { tab = it }
        )
    }
}

@Preview(name = "Building blocks", showBackground = true, heightDp = 900)
@Composable
private fun UiGalleryPreview() {
    SalesOnboardingTheme { UiGallery() }
}

// endregion
