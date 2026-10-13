package io.github.heapy.icu4k.impl.locale

import kotlin.test.*

class InternalLocaleBuilderTest {
    @Test fun buildsCanonicalExtensionSnapshotsAndPreservesInputQuirks() {
        val builder = InternalLocaleBuilder().setExtension('a', "FOO_bar").setExtension('x', "private-lvariant-POSIX")
            .addUnicodeLocaleAttribute("FOO").setUnicodeLocaleKeyword("CA", "japanese")
        val snapshot = builder.getLocaleExtensions()
        assertEquals("a-foo-bar-u-foo-ca-japanese-x-private", snapshot.getID())
        builder.clearExtensions()
        assertSame(LocaleExtensions.EMPTY_EXTENSIONS, builder.getLocaleExtensions())
        assertEquals("a-foo-bar-u-foo-ca-japanese-x-private", snapshot.getID())
        assertFailsWith<UnsupportedOperationException> { (snapshot.getKeys() as MutableSet<Char>).clear() }
        assertEquals("a-bar", builder.setExtensions("a-foo-a-bar").getLocaleExtensions().getID())
        assertEquals("u-ca-foo_bar", builder.clearExtensions().setUnicodeLocaleKeyword("ca", "foo_bar").getLocaleExtensions().getID())
        val error = assertFailsWith<LocaleSyntaxException> { builder.setExtension('b', "abc-") }
        assertEquals(4, error.getErrorIndex())
        assertEquals("u-ca-foo_bar", builder.getLocaleExtensions().getID())
        assertFalse(InternalLocaleBuilder.CaseInsensitiveChar('A') == InternalLocaleBuilder.CaseInsensitiveChar('a'))
        assertTrue(InternalLocaleBuilder.CaseInsensitiveChar('a') == InternalLocaleBuilder.CaseInsensitiveChar('A'))
        assertEquals("u-ca-japanese", builder.setLanguageTag(LanguageTag.parse("en-u-ca-japanese")).getLocaleExtensions().getID())
    }
}
