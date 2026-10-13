package io.github.heapy.icu4k.impl.locale

import kotlin.test.*

class UnicodeLocaleExtensionTest {
    @Test fun orderedLiveViewsAndCachedValueKeepOriginalOwnership() {
        val attributes = mutableSetOf("zzz", "abc")
        val keywords = mutableMapOf("nu" to "thai", "ca" to "")
        val extension = UnicodeLocaleExtension(attributes, keywords)
        assertEquals("u-abc-zzz-ca-nu-thai", extension.getID())
        val view = extension.getUnicodeLocaleKeys()
        keywords["co"] = "phonebk"
        assertEquals(listOf("ca", "co", "nu"), view.toList())
        assertEquals("phonebk", extension.getUnicodeLocaleType("co"))
        assertEquals("u-abc-zzz-ca-nu-thai", extension.getID())
        assertFailsWith<UnsupportedOperationException> { (view as MutableSet<String>).clear() }
        assertEquals("u-ca-japanese", UnicodeLocaleExtension.CA_JAPANESE.toString())
        assertTrue(UnicodeLocaleExtension.isType("foo-bar")); assertFalse(UnicodeLocaleExtension.isType("foo-"))
        assertTrue(UnicodeLocaleExtension.isKey("1a")); assertFalse(UnicodeLocaleExtension.isKey("a1"))
        assertNull(UnicodeLocaleExtension(null, null).getValue())
        assertEquals("u-null", UnicodeLocaleExtension(null, null).getID())
    }
}
