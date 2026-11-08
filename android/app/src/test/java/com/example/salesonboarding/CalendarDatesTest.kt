package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CalendarDatesTest {
    private val day = 24L * 60 * 60 * 1000
    private val today: Calendar = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 8) }
    private val dateOfBirth = KnownFields.get("date_of_birth")
    private val rule = dateOfBirth.validationRules.filterIsInstance<DateFormatAndMinimumAge>().single()

    @Test
    fun aPickedDayIsSavedInTheFormatTheCheckExpects() {
        val millis = CalendarDates.fromIso("1990-03-12")
        assertNotNull(millis)
        assertEquals("1990-03-12", CalendarDates.toIso(millis!!))
        assertEquals("12 March 1990", CalendarDates.readable("1990-03-12"))
        assertNull(CalendarDates.readable("12/03/1990"))
        assertNull(CalendarDates.fromIso("1990-02-30"))
    }

    @Test
    fun theCalendarOffersExactlyTheBirthDatesTheAgeCheckAccepts() {
        val limits = CalendarDates.limitsFor(dateOfBirth, today)
        val latest = limits.latestMillis!!
        val earliest = limits.earliestMillis!!

        assertNull(rule.errorFor(CalendarDates.toIso(latest), today))
        assertNotNull(rule.errorFor(CalendarDates.toIso(latest + day), today))
        assertNull(rule.errorFor(CalendarDates.toIso(earliest), today))
        assertNotNull(rule.errorFor(CalendarDates.toIso(earliest - day), today))
        assertTrue(limits.allows(latest))
        assertFalse(limits.allows(latest + day))
    }

    @Test
    fun aDateOfBirthCalendarOpensThirtyYearsBack() {
        val limits = CalendarDates.limitsFor(dateOfBirth, today)
        assertEquals("1996-11-08", CalendarDates.toIso(limits.startMillis))
        assertEquals(2026 - rule.minimumAge, limits.years.last)
    }

    @Test
    fun otherDateQuestionsAllowAnyDayAndOpenOnToday() {
        val other = dateOfBirth.copy(fieldId = "contract_start", validationRules = emptyList())
        val limits = CalendarDates.limitsFor(other, today)
        assertTrue(limits.allows(0L))
        assertEquals("2026-11-08", CalendarDates.toIso(limits.startMillis))
    }
}
