package io.github.heapy.icu4k.util

import kotlin.test.*

class ULocaleTest {
    @Test fun publicLocaleAndBuilderBridgeLegacyAndUnicodeRepresentations() {
        val locale = ULocale("en_US@calendar=gregorian;timezone=America/Los_Angeles")
        assertEquals("en-US-u-ca-gregory-tz-uslax", locale.toLanguageTag())
        assertEquals(locale, ULocale.forLanguageTag(locale.toLanguageTag()))
        val built = ULocale.Builder().setLanguage("en").setRegion("US").setUnicodeLocaleKeyword("ca", "japanese").build()
        assertEquals("en_US@calendar=japanese", built.getName())
        assertEquals("en-US-u-ca-japanese", built.toLanguageTag())
        assertEquals("en@calendar=japanese", built.getFallback()!!.getName())
        assertEquals("und", ULocale.ROOT.toLanguageTag()); assertSame(ULocale.US, ULocale.US.clone())
        assertEquals("en-US-u-va-posix", ULocale("en_US_POSIX").toLanguageTag())
        assertEquals("en-u-va-posix", ULocale("en__POSİX").toLanguageTag())
        val builder = ULocale.Builder().setLanguage("en")
        val error = assertFailsWith<IllformedLocaleException> { builder.setLanguageTag("en--US") }
        assertEquals(3, error.getErrorIndex()); assertEquals("en", builder.build().getName())
    }
}
