package io.github.heapy.icu4k.impl.locale

import kotlin.test.*

class BaseLocaleTest {
    @Test fun canonicalInstancesAndBuilderConversionsPreserveFields() {
        val locale = BaseLocale.getInstance("EN", "lATN", "us", "posix")
        assertEquals("language=en, script=Latn, region=US, variant=POSIX", locale.toString())
        assertSame(locale, BaseLocale.getInstance("en", "LATN", "US", "POSIX"))
        assertSame(BaseLocale.ROOT, BaseLocale.getInstance(null, null, null, null))
        val builder = InternalLocaleBuilder().setLanguageTag(LanguageTag.parse("en-x-private-lvariant-foo-bar"))
        assertEquals("FOO_BAR", builder.getBaseLocale().getVariant())
        assertEquals("x-private", builder.getLocaleExtensions().getID())
        val tag = LanguageTag.parseLocale(builder.getBaseLocale(), builder.getLocaleExtensions())
        assertEquals("private-lvariant-foo-bar", tag.getPrivateuse())
        assertEquals("en-private-lvariant-foo-bar", tag.toString()) // Original internal representation omits x here.
        assertEquals("und", LanguageTag.parseLocale(BaseLocale.ROOT, LocaleExtensions.EMPTY_EXTENSIONS).toString())
        val before = builder.getBaseLocale()
        assertFailsWith<LocaleSyntaxException> { builder.setLocale(BaseLocale.getInstance("a", "", "", ""), null) }
        assertSame(before, builder.getBaseLocale())
    }
    @Test fun internedFieldsShareCanonicalStringsAndHashMatchesOriginal() {
        val text = buildString { append("Intern-"); append(kotlin.random.Random(81325).nextInt()) }
        val first = text.toCharArray().concatToString()
        val a = internLocaleString(first)
        val b = internLocaleString(text.toCharArray().concatToString())
        assertSame(a, b)
        val one = BaseLocale.getInstance("en", "Latn", "US", "POSIX")
        val two = BaseLocale.getInstance("en", "Latn", "GB", "1901")
        assertSame(one.getLanguage(), two.getLanguage()); assertSame(one.getScript(), two.getScript())
        var expected = 0
        for (c in "enLatnUSPOSIX") expected = 31 * expected + c.code
        assertEquals(expected, one.hashCode())
        assertEquals(-25, BaseLocale.Key("a", "", "", "").compareTo(BaseLocale.Key("z", "", "", "")))
    }
}
