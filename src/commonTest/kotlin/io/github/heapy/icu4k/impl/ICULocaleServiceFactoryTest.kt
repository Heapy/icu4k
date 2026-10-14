package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class ICULocaleServiceFactoryTest {
    @Test fun visibleFactoryOverrideAndFallback() {
        val service = ICULocaleService()
        service.registerObject("root", ULocale.ROOT)
        service.registerObject("English", ULocale.ENGLISH)
        val special = service.registerObject("special", ULocale.US, 2, false)
        assertEquals(setOf("", "en"), service.getVisibleIDs())
        val actual = arrayOfNulls<ULocale>(1)
        assertEquals("special", service.get(ULocale("en_US_POSIX"), 2, actual))
        assertEquals(ULocale.US, actual[0])
        assertEquals("English", service.get(ULocale.US, 3))
        assertTrue(service.unregisterFactory(special))
        assertEquals("English", service.get(ULocale.US, 2))
        assertEquals(setOf(ULocale.ROOT, ULocale.ENGLISH), service.getAvailableULocales().toSet())
        assertEquals("English (United States)", ICULocaleService.SimpleLocaleKeyFactory(null, ULocale.ROOT, -1, false).getDisplayName("en_US", ULocale.ENGLISH))
    }
}
