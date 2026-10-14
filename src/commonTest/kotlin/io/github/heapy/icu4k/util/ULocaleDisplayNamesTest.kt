package io.github.heapy.icu4k.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ULocaleDisplayNamesTest {
    @Test fun displayComponentsAndDialectNames() {
        val english = ULocale.ENGLISH
        val locale = ULocale("en_Latn_US_POSIX@calendar=gregorian;currency=usd")
        assertEquals("English", locale.getDisplayLanguage(english))
        assertEquals("Latin", locale.getDisplayScript(english))
        assertEquals("United States", locale.getDisplayCountry(english))
        assertEquals("Computer", locale.getDisplayVariant(english))
        assertEquals("Gregorian Calendar", locale.getDisplayKeywordValue(" CALENDAR\u0000", english))
        assertEquals("US Dollar", locale.getDisplayKeywordValue("currency", english))
        assertEquals("Calendar", ULocale.getDisplayKeyword("calendar", english))
        // ICU4J passes the base name to languageDisplayName, which rejects compound language codes.
        assertEquals("en_US", ULocale("en_US").getDisplayLanguageWithDialect(english))
        assertEquals("American English", ULocale.getDisplayNameWithDialect("en_US", "en"))
        assertEquals("English (United States)", ULocale.getDisplayName("en_US", english))
        assertEquals("English", ULocale.getDisplayLanguage("en_US", "en"))
    }
}
