package com.example.salesonboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** How the guide looks, from how the onboarding is going. */
enum class GuideMood(val description: String) {
    CALM("The guide is calm"),
    HAPPY("The guide is pleased: the answer was accepted"),
    CONCERNED("The guide is concerned: something needs attention"),
    WORRIED("The guide is worried: this customer may stop"),
    CELEBRATING("The guide is celebrating: every required answer is in")
}

/**
 * The guide's mood. A finished form and a high risk matter most; an answer to fix or a
 * medium risk come next; a just-accepted answer ([justPassed]) makes it briefly happy.
 */
internal fun guideMood(guidance: NextFieldGuidance, justPassed: Boolean): GuideMood = when {
    guidance.isComplete -> GuideMood.CELEBRATING
    guidance.riskGuidance?.level == CompletionRiskLevel.HIGH -> GuideMood.WORRIED
    guidance.correction != null || guidance.riskGuidance?.level == CompletionRiskLevel.MEDIUM -> GuideMood.CONCERNED
    justPassed -> GuideMood.HAPPY
    else -> GuideMood.CALM
}

/**
 * The SalesMesh guide: a round robot head drawn in code, so it has no background, scales to
 * any size and moves. It floats, tilts, blinks, glances about, pulses its antenna light and
 * changes its eyes with [mood]. With [animated] off the body keeps still but the eyes still
 * blink and glance. With the phone's "Remove animations" on, it is a still face that still
 * shows the mood, and nothing is redrawn between changes. A new mood needs only a new entry in
 * [GuideMood] and the eye shapes below.
 */
@Composable
fun GuideMascot(mood: GuideMood, modifier: Modifier = Modifier, animated: Boolean = true) {
    val c = Sm.colors
    val resolver = LocalContext.current.contentResolver
    val systemAnimationsOff = remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // [animated] stops only the body (float, tilt, antenna); the eyes stay alive. The phone's
    // "Remove animations" stops everything.
    val moving = animated && !systemAnimationsOff
    var blink = 1f
    var float = 0f
    var sway = 0f
    var pulse = 0f
    var glance = 0f
    if (!systemAnimationsOff) {
        val life = rememberInfiniteTransition(label = "guide")
        // A single blink, then a quick double blink, so it does not look mechanical.
        blink = life.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 6400
                    1f at 0
                    1f at 2600
                    0.1f at 2680
                    1f at 2800
                    1f at 5900
                    0.1f at 5980
                    1f at 6080
                    0.1f at 6180
                    1f at 6300
                }
            ),
            label = "blink"
        ).value
        float = life.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "float"
        ).value
        // A slower side-to-side tilt, out of step with the float.
        sway = life.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "sway"
        ).value
        pulse = life.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "antenna"
        ).value
        // Now and then the eyes glance to one side and back.
        glance = life.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 7000
                    0f at 0
                    0f at 1500
                    1f at 1700
                    1f at 2600
                    0f at 2800
                    0f at 4300
                    -1f at 4500
                    -1f at 5300
                    0f at 5500
                }
            ),
            label = "glance"
        ).value
        if (!moving) {
            float = 0f
            sway = 0f
            pulse = 0f
        }
    }
    val smile by animateFloatAsState(
        if (mood == GuideMood.HAPPY || mood == GuideMood.CELEBRATING) 1f else 0f,
        label = "smile"
    )
    // How far the eyelids come down: none when calm, a little when concerned, more when worried.
    val lid by animateFloatAsState(
        when (mood) {
            GuideMood.CONCERNED -> 0.35f
            GuideMood.WORRIED -> 0.7f
            else -> 0f
        },
        label = "lid"
    )
    val eyeColour by animateColorAsState(if (mood == GuideMood.WORRIED) c.cherry else c.lime, label = "eye")
    val bounce = if (mood == GuideMood.CELEBRATING) 1.8f else 1f

    Canvas(modifier.semantics { contentDescription = mood.description }) {
        val w = size.width
        val h = size.height
        // Enough lift to see at panel size (about 3 dp at 40 dp), but not too much when large.
        val lift = minOf(h * 0.07f, 6.dp.toPx())
        translate(top = float * lift * bounce) { rotate(degrees = sway * 6f, pivot = Offset(w / 2, h * 0.55f)) {
            // Ear pods, shell and the antenna light.
            listOf(0.02f, 0.86f).forEach { x ->
                drawRoundRect(c.teal, Offset(w * x, h * 0.40f), Size(w * 0.12f, h * 0.26f), CornerRadius(w * 0.05f))
            }
            drawRoundRect(c.white, Offset(w * 0.08f, h * 0.14f), Size(w * 0.84f, h * 0.78f), CornerRadius(w * 0.36f))
            drawRoundRect(
                c.line, Offset(w * 0.08f, h * 0.14f), Size(w * 0.84f, h * 0.78f), CornerRadius(w * 0.36f),
                style = Stroke(w * 0.025f)
            )
            // The antenna light glows and pulses.
            val antenna = Offset(w * 0.5f, h * 0.12f)
            drawCircle(c.lime.copy(alpha = 0.35f * pulse), radius = w * (0.08f + 0.04f * pulse), center = antenna)
            drawCircle(c.lime, radius = w * (0.05f + 0.012f * pulse), center = antenna)
            // The visor.
            drawRoundRect(c.ink, Offset(w * 0.16f, h * 0.25f), Size(w * 0.68f, h * 0.57f), CornerRadius(w * 0.27f))

            for (side in listOf(-1, 1)) {
                val cx = w * (0.5f + side * 0.15f + glance * 0.035f)
                val cy = h * 0.54f
                val eyeWidth = w * 0.16f
                val eyeHeight = h * 0.18f * blink
                // Open eyes fade into smiling arcs.
                drawRoundRect(
                    eyeColour.copy(alpha = 1f - smile),
                    Offset(cx - eyeWidth / 2, cy - eyeHeight / 2),
                    Size(eyeWidth, eyeHeight),
                    CornerRadius(eyeWidth * 0.45f)
                )
                if (smile > 0f) {
                    drawArc(
                        eyeColour.copy(alpha = smile),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - eyeWidth / 2, cy - eyeWidth * 0.3f),
                        size = Size(eyeWidth, eyeWidth * 0.7f),
                        style = Stroke(w * 0.045f, cap = StrokeCap.Round)
                    )
                }
                // Eyelids in the visor's colour, lower at the outer corner, for concern and worry.
                if (lid > 0f) {
                    val top = cy - eyeHeight / 2 - h * 0.02f
                    val outerX = cx + side * eyeWidth * 0.62f
                    val innerX = cx - side * eyeWidth * 0.62f
                    drawPath(
                        Path().apply {
                            moveTo(outerX, top)
                            lineTo(innerX, top)
                            lineTo(innerX, top + eyeHeight * lid * 0.35f)
                            lineTo(outerX, top + eyeHeight * lid)
                            close()
                        },
                        c.ink
                    )
                }
            }
            // A few sparkles when every required answer is in.
            if (mood == GuideMood.CELEBRATING) {
                val sparkle = 0.6f + 0.4f * float
                listOf(Offset(0.1f, 0.12f), Offset(0.9f, 0.18f), Offset(0.86f, 0.9f)).forEach { (x, y) ->
                    drawCircle(c.lime.copy(alpha = sparkle), radius = w * 0.035f, center = Offset(w * x, h * y))
                }
            }
        } }
    }
}

@Preview(name = "Guide moods", showBackground = true, backgroundColor = 0xFF1F2A24, widthDp = 420, heightDp = 110)
@Composable
private fun GuideMoodsPreview() {
    SalesOnboardingTheme {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideMood.entries.forEach { GuideMascot(it, Modifier.size(70.dp)) }
        }
    }
}
