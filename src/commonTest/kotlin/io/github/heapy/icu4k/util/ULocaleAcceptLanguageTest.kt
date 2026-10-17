// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.test.*

class ULocaleAcceptLanguageTest {
    @Test fun sourceNegotiationAndFallbackFlagSemantics() {
        val en = ULocale("en_US"); val fr = ULocale("fr_FR")
        val available = arrayOf(en, fr)
        val fallback = booleanArrayOf(true, false)
        assertSame(en, ULocale.acceptLanguage("en-US", available, fallback))
        assertFalse(fallback[0]); assertFalse(fallback[1])
        assertSame(en, ULocale.acceptLanguage("en-AU", available, fallback))
        assertTrue(fallback[0])
        assertSame(fr, ULocale.acceptLanguage(arrayOf(ULocale("ja"), ULocale("fr_CA")), available, fallback))
        assertTrue(fallback[0])
        assertNull(ULocale.acceptLanguage("ja", available, fallback))
        assertTrue(fallback[0])
        assertEquals(ULocale("en_US"), ULocale.acceptLanguage("en-US", null))
        assertEquals(ULocale("fr_FR"), ULocale.acceptLanguage(arrayOf(ULocale("fr_FR")), null))
    }
    @Test fun malformedHeadersAndArrayFailuresRetainMutationOrder() {
        val fallback = booleanArrayOf(false)
        assertNull(ULocale.acceptLanguage("en;q=bogus", null, fallback))
        assertTrue(fallback[0])
        assertFailsWith<IndexOutOfBoundsException> { ULocale.acceptLanguage("en;q=bogus", null, booleanArrayOf()) }
        fallback[0] = false
        assertFailsWith<NullPointerException> { ULocale.acceptLanguage(null as String?, emptyArray(), fallback) }
        assertTrue(fallback[0])
        assertNull(ULocale.acceptLanguage(emptyArray<ULocale>(), emptyArray(), null))
    }
}
