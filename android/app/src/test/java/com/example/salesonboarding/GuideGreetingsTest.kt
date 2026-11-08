package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GuideGreetingsTest {
    @Test
    fun theGreetingFollowsTheTimeOfDay() {
        assertTrue(GuideGreetings.pick(8).hello.startsWith("Good morning!"))
        assertTrue(GuideGreetings.pick(14).hello.startsWith("Good afternoon!"))
        assertTrue(GuideGreetings.pick(21).hello.startsWith("Good evening!"))
        assertTrue(GuideGreetings.pick(2).hello.startsWith("Good evening!"))
    }

    @Test
    fun everyGreetingCarriesOneOfTheFacts() {
        repeat(20) { seed ->
            assertTrue(GuideGreetings.pick(10, Random(seed)).fact in GuideGreetings.facts)
        }
    }

    @Test
    fun theSameSeedGivesTheSameGreeting() {
        assertEquals(GuideGreetings.pick(10, Random(7)), GuideGreetings.pick(10, Random(7)))
    }
}
