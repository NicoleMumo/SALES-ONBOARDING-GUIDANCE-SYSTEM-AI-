package com.example.salesonboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.random.Random

/** A greeting for the start of a form, with one fun fact about onboarding. */
data class GuideGreeting(val hello: String, val fact: String)

object GuideGreetings {
    private val lines = listOf(
        "Ready when you are.",
        "Let's make this one smooth.",
        "I'll point the way.",
        "Nice to see you again.",
        "One onboarding at a time."
    )

    /** Kept true and checkable: facts about this system, Kenya, and one cited survey. */
    val facts = listOf(
        "KYC means \"Know Your Customer\": checking who a client is before an account is opened.",
        "Kenya's Data Protection Act came into force in 2019. That is why consent comes first.",
        "M-Pesa launched in Kenya in 2007 and made mobile money part of everyday sign-ups.",
        "A Kenyan mobile number has 10 digits and starts with 07 or 01, which is what the form checks.",
        "Answers are checked when you leave a box, so a pause while typing never counts as a mistake.",
        "My suggestions come from a model that runs on this phone, so they work without internet.",
        "In one survey, customer-success leaders said 48% of clients abandon onboarding when value comes slowly (OnRamp, 2025)."
    )

    fun pick(hourOfDay: Int, random: Random = Random.Default): GuideGreeting {
        val part = when (hourOfDay) {
            in 5..11 -> "Good morning!"
            in 12..16 -> "Good afternoon!"
            else -> "Good evening!"
        }
        return GuideGreeting("$part ${lines.random(random)}", facts.random(random))
    }

    fun forNow(): GuideGreeting = pick(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
}

private data class IntroStep(val mood: GuideMood, val title: String, val body: String)

private val INTRO_STEPS = listOf(
    IntroStep(
        GuideMood.HAPPY, "Hi, I'm your guide",
        "I watch each answer and show you where to go next, so you never scroll back up to find me."
    ),
    IntroStep(
        GuideMood.CALM, "Tap the card to jump",
        "Lime means the suggested next question. Red means an answer to fix. Tap it and I open that question, in any section, with the cursor in the box."
    ),
    IntroStep(
        GuideMood.CALM, "Move me anywhere",
        "Drag the panel to any spot on the page. Tap my face to fold me to one line, and again to open me."
    ),
    IntroStep(
        GuideMood.CONCERNED, "My face shows how it's going",
        "Amber or red means the client may stop. That is a good moment to offer help."
    ),
    IntroStep(
        GuideMood.CELEBRATING, "Make me yours",
        "Open ⋮ for Auto-follow, where Enter takes you to my suggestion, and Moving guide, which you can turn off to save battery. You can replay this tour from ⋮ too."
    )
)

/**
 * The guide's first-time tour over the whole screen: five short steps with the guide large,
 * live examples of the panel and its moods, and Skip, Back and Next. [onFinish] runs on
 * "Let's start" or Skip.
 */
@Composable
fun GuideIntro(onFinish: () -> Unit, guideMoving: Boolean, modifier: Modifier = Modifier) {
    val c = Sm.colors
    var step by rememberSaveable { mutableIntStateOf(0) }
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Box(
        modifier
            .fillMaxSize()
            .background(c.ink.copy(alpha = 0.88f))
            // The tour covers the form, so taps do not reach it.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .alpha(entrance.value.coerceIn(0f, 1f))
                .scale(0.8f + 0.2f * entrance.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                label = "intro step"
            ) { index ->
                val current = INTRO_STEPS[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GuideMascot(current.mood, Modifier.size(140.dp), animated = guideMoving)
                    Surface(shape = RoundedCornerShape(24.dp), color = c.cream, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(current.title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
                            Text(current.body, style = MaterialTheme.typography.bodyLarge, color = c.mutedStrong)
                            when (index) {
                                1 -> ExampleCards()
                                3 -> MoodRow(guideMoving)
                            }
                        }
                    }
                }
            }
            // Where the agent is in the tour.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                INTRO_STEPS.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == step) 22.dp else 8.dp, 8.dp)
                            .background(if (i == step) c.lime else c.cream.copy(alpha = 0.4f), CircleShape)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onFinish) { Text("Skip", color = c.cream) }
                Box(Modifier.weight(1f))
                if (step > 0) TextButton(onClick = { step-- }) { Text("Back", color = c.cream) }
                val last = step == INTRO_STEPS.lastIndex
                Surface(
                    onClick = { if (last) onFinish() else step++ },
                    shape = RoundedCornerShape(16.dp),
                    color = c.lime,
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (last) "Let's start" else "Next",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = c.ink
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Guide tour", showBackground = true, widthDp = 400, heightDp = 820)
@Composable
private fun GuideIntroPreview() {
    SalesOnboardingTheme { GuideIntro(onFinish = {}, guideMoving = true) }
}

/** The two kinds of card the panel shows, as they look in the form. */
@Composable
private fun ExampleCards() {
    val c = Sm.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("Suggested next", "Date of birth", false), Triple("Fix this answer", "Mobile phone number", true))
            .forEach { (label, name, fix) ->
                Surface(shape = RoundedCornerShape(14.dp), color = if (fix) c.cherry else c.lime, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = if (fix) c.white else c.ink)
                        Text(name, style = MaterialTheme.typography.titleMedium, color = if (fix) c.white else c.ink)
                    }
                }
            }
    }
}

/** All five moods, small and live, with what each means. */
@Composable
private fun MoodRow(guideMoving: Boolean) {
    val c = Sm.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(
            GuideMood.CALM to "Calm",
            GuideMood.HAPPY to "Accepted",
            GuideMood.CONCERNED to "Check",
            GuideMood.WORRIED to "At risk",
            GuideMood.CELEBRATING to "All done"
        ).forEach { (mood, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).background(c.ink, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    GuideMascot(mood, Modifier.size(44.dp), animated = guideMoving)
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = c.mutedStrong, textAlign = TextAlign.Center)
            }
        }
    }
}
