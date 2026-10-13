package io.github.heapy.icu4k.impl.locale

import kotlin.test.*

class LanguageTagTest {
    @Test fun parsesDeduplicatesAndPreservesErrorPositions() {
        val status = ParseStatus()
        val tag = LanguageTag.parse("en-Latn-US-1901-1901-a-foo-A-bar-x-private", status)
        assertFalse(status.isError())
        assertEquals("en-Latn-US-1901-a-foo-x-private", tag.toString())
        assertEquals(listOf("1901"), tag.getVariants())
        assertEquals("tlh-Latn", LanguageTag.parse("i-klingon-Latn", status).toString())
        assertEquals(14, status.getParseLength())
        assertEquals("en", LanguageTag.parse("en-a", status).toString())
        assertEquals(3, status.getErrorIndex()); assertEquals("Incomplete extension 'a'", status.getErrorMessage())
        assertEquals("u-ca-nu", LanguageTag.canonicalizeExtension("U-ca-TRUE-nu-yes"))
        assertEquals("x-a", LanguageTag.parse("x-a").toString())
    }
}
