package com.example.salesonboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberFormatTest {
    @Test
    fun commonWaysOfTypingANumberBecomeOneFormat() {
        listOf("0712 345 678", "0712-345-678", "+254 712 345 678", "254712345678", "712345678", "(0712) 345678")
            .forEach { assertEquals(it, "0712345678", PhoneNumberFormat.tidy(it)) }
        assertEquals("0112345678", PhoneNumberFormat.tidy("+254 112 345 678"))
    }

    @Test
    fun somethingThatIsNotAKenyanMobileNumberIsLeftForTheCheck() {
        assertEquals("0812 345", PhoneNumberFormat.tidy(" 0812 345 "))
        assertEquals("abc", PhoneNumberFormat.tidy("abc"))
    }

    @Test
    fun theTidiedNumberPassesTheFormsPhoneRule() {
        assertNull(KenyanPhoneFormat.validate(PhoneNumberFormat.tidy("+254 712 345 678"), ValidationContext()))
    }
}

class IdCardTextTest {
    // Made-up card text in the layout ML Kit returns for a Kenyan ID (one line per text block).
    private val card = """
        JAMHURI YA KENYA
        REPUBLIC OF KENYA
        SERIAL NUMBER: 234567891
        ID NUMBER: 12345678
        FULL NAMES
        JANE MIKA
        DATE OF BIRTH
        14.03.1995
        SEX FEMALE
        DATE OF ISSUE
        02.06.2014
    """.trimIndent()

    @Test
    fun labelledValuesAreReadAndWrittenAsTheFormExpects() {
        assertEquals(IdCardDetails("Jane Mika", "12345678", "1995-03-14"), IdCardText.read(card))
    }

    @Test
    fun withoutLabelsTheMostLikelyValuesAreTaken() {
        val unlabelled = "REPUBLIC OF KENYA\n234567891\nJANE WANJIRU MIKA\n12345678\n14/03/1995\n02/06/2014"
        assertEquals(IdCardDetails("Jane Wanjiru Mika", "12345678", "1995-03-14"), IdCardText.read(unlabelled))
    }

    @Test
    fun nothingIsGuessedFromUnreadableText() {
        val details = IdCardText.read("blurry")
        assertNull(details.fullName)
        assertNull(details.idNumber)
        assertNull(details.dateOfBirth)
    }
}
