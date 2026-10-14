package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CurrencyNamesTest {
    @Test fun newCurrencyResourcesAreDiscoverable() {
        for (locale in listOf("de", "zh", "ru", "hi", "it", "pt", "ko", "nl")) {
            val path = "${ICUData.ICU_BASE_NAME}/curr/$locale.res"
            assertTrue(ICUData.exists(path)); assertNotNull(ICUData.getBytes(path))
            val names = mutableListOf<String>()
            ICUData.visitResources("${ICUData.ICU_BASE_NAME}/curr/", names::add)
            assertTrue("$locale.res" in names)
        }
    }
    @Test fun stylesPluralFallbackAndChoiceMutation() {
        val en = ULocale("en")
        val choice = booleanArrayOf(true, true)
        assertEquals("$", CurrencyNames.name("USD", en, 0, choice))
        assertContentEquals(booleanArrayOf(false, true), choice)
        assertEquals("US dollars", CurrencyNames.name("USD", en, 2, "other", null))
        assertEquals("ZZZ", CurrencyNames.name("ZZZ", en, 1, null))
        choice[0] = true
        assertFailsWith<IllegalArgumentException> { CurrencyNames.name("USD", en, 2, choice) }
        assertFalse(choice[0])
        assertFailsWith<IndexOutOfBoundsException> { CurrencyNames.name("USD", en, -1, booleanArrayOf()) }
        assertEquals("US-Dollar", CurrencyNames.name("USD", ULocale("de"), 1, null))
    }
}
