package com.example.salesonboarding

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressLookupTest {
    private val searchReply = """
        [
          {"lat": "-1.2864", "lon": "36.8172", "display_name": "Kenyatta Avenue, Nairobi Central, Nairobi, 00100, Kenya"},
          {"lat": "-1.2864", "lon": "36.8172", "display_name": "Kenyatta Avenue, Nairobi Central, Nairobi, 00100, Kenya"},
          {"lat": "-4.0435", "lon": "39.6682", "display_name": "Moi Avenue, Mombasa, Mombasa, Kenya"},
          {"lat": "", "lon": "39.6", "display_name": "No position"}
        ]
    """.trimIndent()

    @Test
    fun searchResultsAreShortenedAndDuplicatesDropped() {
        val matches = OsmAddressLookup.parseSearch(searchReply)

        assertEquals(
            listOf("Kenyatta Avenue, Nairobi Central, Nairobi, 00100", "Moi Avenue, Mombasa"),
            matches.map { it.label }
        )
        assertEquals(-1.2864, matches.first().latitude, 1e-9)
    }

    @Test
    fun reverseLookupReturnsNothingWhenNominatimFoundNothing() {
        assertNull(OsmAddressLookup.parseReverse("""{"error": "Unable to geocode"}"""))
        assertEquals(
            "Ngong Road, Kilimani",
            OsmAddressLookup.parseReverse("""{"lat": "-1.3", "lon": "36.78", "display_name": "Ngong Road, Kilimani, Kenya"}""")?.label
        )
    }

    @Test
    fun requestsAskForKenyanAddressesAndEncodeTheText() {
        val url = OsmAddressLookup.searchUrl("  Moi Ave & 2nd St ")
        assertTrue(url.startsWith("https://nominatim.openstreetmap.org/search?"))
        assertTrue("countrycodes=ke" in url)
        assertTrue(url.endsWith("q=Moi+Ave+%26+2nd+St"))
        assertTrue("lat=-1.286400&lon=36.817200" in OsmAddressLookup.reverseUrl(-1.2864, 36.8172))
    }

    @Test
    fun requestsAreAtLeastOneSecondApartAndRepeatsAreNotSentAgain() = runTest {
        var now = 0L
        val sent = mutableListOf<String>()
        val waits = mutableListOf<Long>()
        val service = OsmAddressService(
            get = { url -> sent += url; "[]" },
            clock = { now },
            wait = { waits += it; now += it }
        )

        service.search("Moi Avenue")
        now += 300
        service.search("Kenyatta Avenue")
        service.search("Moi Avenue")

        assertEquals(2, sent.size)
        assertEquals(listOf(700L), waits)
    }

    @Test
    fun onlyTextAddressQuestionsGetAddressSearch() {
        assertTrue(KnownFields.get("home_address").isStreetAddress())
        assertTrue(KnownFields.get("physical_address").isStreetAddress())
        assertFalse(KnownFields.get("email_address").isStreetAddress())
    }
}
