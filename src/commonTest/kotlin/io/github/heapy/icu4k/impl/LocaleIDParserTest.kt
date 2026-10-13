package io.github.heapy.icu4k.impl

import kotlin.test.*

class LocaleIDParserTest {
    @Test fun legacyNormalizationPosixAndLiveSortedKeywords() {
        val parser = LocaleIDParser("ENG_usa.UTF-8@euro")
        assertEquals("en_US_EURO", parser.getName())
        val keywords = LocaleIDParser("en@ Z = Last ;a=First;a=Second")
        assertEquals("en@a=First;z=Last", keywords.getName())
        keywords.defaultKeywordValue("a", "ignored")
        keywords.setKeywordValue("B", " Middle ")
        assertEquals("en@a=First;b=Middle;z=Last", keywords.getName())
        val map = keywords.getKeywordMap()
        map.entries.first().setValue("changed")
        assertEquals("en@a=changed;b=Middle;z=Last", keywords.getName())
        val iterator = keywords.getKeywords()!!
        assertEquals("a", iterator.next()); iterator.remove()
        assertEquals("en@b=Middle;z=Last", keywords.getName())
        keywords.setKeywordValue(null, null)
        assertFailsWith<UnsupportedOperationException> { keywords.getKeywordMap()["x"] = "y" }
        assertEquals("en", keywords.getName())
        assertFailsWith<IllegalArgumentException> { LocaleIDParser("en_US_" + "v".repeat(180)).getName() }
        assertEquals("en@\u00a0a\u00a0=\u00a0v\u00a0", LocaleIDParser("en@\u00a0a\u00a0=\u00a0v\u00a0").getName())
    }
    @Test fun isoCodesKeepOriginalMappingsAndCopyReturnedArrays() {
        assertEquals("en", LocaleIDs.threeToTwoLetterLanguage("eng"))
        assertEquals("US", LocaleIDs.threeToTwoLetterRegion("USA"))
        assertEquals("MM", LocaleIDs.getCurrentCountryID("BU"))
        assertEquals("he", LocaleIDs.getCurrentLanguageID("iw"))
        val before = LocaleIDs.getISOCountries()[0]
        LocaleIDs.getISOCountries()[0] = "changed"
        assertEquals(before, LocaleIDs.getISOCountries()[0])
    }
}
