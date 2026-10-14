package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleServiceCoreTest {
    @Test fun fallbackTracksDefaultLocaleAndReportsActualLocale() {
        val saved = ULocale.getDefault()
        try {
            ULocale.setDefault(ULocale("en_US@currency=USD"))
            val service = LocaleServiceCore()
            service.registerObject("english", "en")
            service.registerObject("french", "fr")
            assertEquals("en_US", service.validateFallbackLocale())
            val actual = arrayOf<ULocale?>(ULocale("unchanged"))
            assertEquals("english", service.get(ULocale("zz_ZZ@calendar=gregorian"), 4, actual))
            assertEquals("en@calendar=gregorian", actual[0]!!.getName())
            ULocale.setDefault(ULocale("fr_FR"))
            assertEquals("french", service.get(ULocale("zz_ZZ"), actual))
            assertEquals("fr", actual[0]!!.getName())
            assertFailsWith<IndexOutOfBoundsException> { service.get(ULocale("fr"), emptyArray()) }
        } finally { ULocale.setDefault(saved) }
    }
    @Test fun absentResultsLeaveActualLocaleUntouched() {
        val service = LocaleServiceCore()
        val untouched = ULocale("en")
        val actual = arrayOf<ULocale?>(untouched)
        assertNull(service.get(null as ULocale?, actual)); assertSame(untouched, actual[0])
        assertNull(service.get(ULocale("zz"), actual)); assertSame(untouched, actual[0])
    }
}
