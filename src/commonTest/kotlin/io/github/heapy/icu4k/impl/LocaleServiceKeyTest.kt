package io.github.heapy.icu4k.impl

import kotlin.test.*

class LocaleServiceKeyTest {
    @Test fun keywordsAndKindSurviveFallback() {
        val key = LocaleServiceKey.createWithCanonicalFallback("de_DE_POSIX@currency=EUR", "en_US", 2)!!
        val descriptors = mutableListOf<String?>()
        do { descriptors.add(key.currentDescriptor()) } while (key.fallback())
        assertEquals(listOf<String?>("2/de_DE_POSIX@currency=EUR", "2/de_DE@currency=EUR", "2/de@currency=EUR", "2/en_US@currency=EUR", "2/en@currency=EUR", "2/@currency=EUR"), descriptors)
        assertNull(key.currentID()); assertNull(key.currentDescriptor())
        assertEquals("null@currency=EUR", key.currentLocale().getName())
        assertFailsWith<NullPointerException> { key.fallback() }
    }
    @Test fun rootAndStrictParentChecks() {
        val root = LocaleServiceKey.createWithCanonicalFallback("root", "en")!!
        assertEquals("/", root.currentDescriptor()); assertTrue(root.fallback())
        assertEquals("/en", root.currentDescriptor()); assertTrue(root.fallback())
        assertEquals("/", root.currentDescriptor()); assertFalse(root.fallback())
        assertFailsWith<NullPointerException> { root.currentLocale() }
        val literal = object : LocaleServiceKey("root", "root", "en", LocaleServiceKey.KIND_ANY) { }
        assertFalse(literal.fallback()); assertEquals("null", literal.currentLocale().getName())
        val key = LocaleServiceKey.createWithCanonicalFallback("en", null)!!
        assertTrue(key.isFallbackOf("en_US")); assertFalse(key.isFallbackOf("english"))
        assertNull(LocaleServiceKey.createWithCanonicalFallback(null, "en"))
    }
}
