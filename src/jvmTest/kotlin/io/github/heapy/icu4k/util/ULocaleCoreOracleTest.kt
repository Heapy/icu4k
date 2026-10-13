package io.github.heapy.icu4k.util

import com.ibm.icu.util.ULocale as J
import kotlin.random.Random
import kotlin.test.*

class ULocaleCoreOracleTest {
    private fun compare(j: J, k: ULocale) {
        assertEquals(j.name, k.getName()); assertEquals(j.toString(), k.toString()); assertEquals(j.hashCode(), k.hashCode())
        assertEquals(j.language, k.getLanguage()); assertEquals(j.script, k.getScript()); assertEquals(j.country, k.getCountry()); assertEquals(j.variant, k.getVariant())
        assertEquals(j.baseName, k.getBaseName()); assertEquals(j.toLanguageTag(), k.toLanguageTag(), j.name)
        assertEquals(j.keywords?.asSequence()?.toList(), k.getKeywords()?.asSequence()?.toList())
        assertEquals(j.extensionKeys.toList(), k.getExtensionKeys().toList())
        assertEquals(j.unicodeLocaleKeys.toList(), k.getUnicodeLocaleKeys().toList())
        assertEquals(j.unicodeLocaleAttributes.toList(), k.getUnicodeLocaleAttributes().toList())
        for (key in listOf("ca", "CA", "nu", "tz", "va", "kn", "zz")) assertEquals(j.getUnicodeLocaleType(key), k.getUnicodeLocaleType(key))
        for (key in "uUxXaA9") assertEquals(j.getExtension(key), k.getExtension(key))
        assertEquals(j.isO3Language, k.getISO3Language()); assertEquals(j.isO3Country, k.getISO3Country())
        var jf: J? = j; var kf: ULocale? = k
        repeat(8) {
            assertEquals(jf?.name, kf?.getName())
            jf = jf?.fallback; kf = kf?.getFallback()
        }
        assertSame(j, j.clone()); assertSame(k, k.clone())
    }
    @Test fun constructorsIdentityKeywordsTagsAndFallbackMatchOriginal() {
        val ids = listOf("", "root", "ROOT", "und", "und_US", "und@calendar=japanese", "EN-us", "ENG_USA", "zh_Hant_TW", "en_US_POSIX",
            "en__POSİX", "en__POſIX", "de_DE.UTF-8@euro", "en@calendar=gregorian;timezone=America/Los_Angeles", "@attribute=foo-bar;kn=yes;zz=yes",
            "en-u-ca-japanese", "und-u-va-posix", "x-private", "x_private", "i-klingon", "en-a-foo-x-legacy", "a_b", "_US", "en__ABC_DEF",
            "en@u=ca-japanese;a=foo_bar;unknown=value;calendar=unknown", "en@attribute=ab-foo__bar;nu=thai", "en@va=posix", "en_US_POSIX@va=other")
        for (id in ids) {
            compare(J(id), ULocale(id)); compare(J.forLanguageTag(id), ULocale.forLanguageTag(id))
            val j = J(id); val k = ULocale(id)
            for ((key, value) in listOf("currency" to "USD", "calendar" to null, null to null)) compare(j.setKeywordValue(key, value), k.setKeywordValue(key, value))
            assertEquals(J.getName(id), ULocale.getName(id)); assertEquals(J.getBaseName(id), ULocale.getBaseName(id)); assertEquals(J.getFallback(id), ULocale.getFallback(id))
            assertEquals(J.getLanguage(id), ULocale.getLanguage(id)); assertEquals(J.getScript(id), ULocale.getScript(id))
            assertEquals(J.getCountry(id), ULocale.getCountry(id)); assertEquals(J.getVariant(id), ULocale.getVariant(id))
        }
        for (fields in listOf(listOf("en", "US", "POSIX"), listOf("zh", "Hant", "TW"), listOf(null, null, null), listOf("", "", "POSIX"))) {
            compare(J(fields[0], fields[1], fields[2]), ULocale(fields[0], fields[1], fields[2]))
            compare(J(fields[0], fields[1]), ULocale(fields[0], fields[1]))
        }
        val random = Random(555813)
        val parts = listOf("en", "zh", "Latn", "Hant", "US", "419", "1901", "POSIX", "x", "u", "ca", "japanese", "", "bad", "foo")
        repeat(400) {
            val id = List(random.nextInt(1, 7)) { parts.random(random) }.joinToString(listOf("-", "_").random(random))
            compare(J(id), ULocale(id)); compare(J.forLanguageTag(id), ULocale.forLanguageTag(id))
        }
        val locales = ids.map { J(it) to ULocale(it) }
        for ((a, b) in locales) for ((c, d) in locales) { assertEquals(a.compareTo(c), b.compareTo(d)); assertEquals(a == c, b == d) }
    }
    @Test fun publicBuilderMutationsAndStrictErrorsMatchOriginal() {
        val j = J.Builder(); val k = ULocale.Builder()
        fun operation(name: String, args: List<Any?>) {
            val jm = j.javaClass.methods.single { it.name == name && it.parameterCount == args.size }
            val km = k.javaClass.methods.single { it.name == name && it.parameterCount == args.size }
            val je = runCatching { jm.invoke(j, *args.toTypedArray()) }.exceptionOrNull()?.let { (it as java.lang.reflect.InvocationTargetException).cause!! }
            val ke = runCatching { km.invoke(k, *args.toTypedArray()) }.exceptionOrNull()?.let { (it as java.lang.reflect.InvocationTargetException).cause!! }
            if (je != null) {
                assertIs<com.ibm.icu.util.IllformedLocaleException>(je); assertIs<IllformedLocaleException>(ke)
                assertEquals(je.message, ke.message); assertEquals(je.errorIndex, ke.getErrorIndex())
            } else assertNull(ke)
            compare(j.build(), k.build())
        }
        for ((name, values) in listOf("setLanguage" to listOf(null, "en", "a", "iw"), "setScript" to listOf(null, "Latn", "invalid"),
            "setRegion" to listOf(null, "US", "419", "bad"), "setVariant" to listOf(null, "POSIX", "1901-posix", "abc"),
            "addUnicodeLocaleAttribute" to listOf("foo", "FOO", "ab", null), "removeUnicodeLocaleAttribute" to listOf("foo", "ab"))) {
            for (value in values) operation(name, listOf(value))
        }
        for (key in "auX!") for (value in listOf(null, "foo", "ca-japanese-nu-thai", "abc-")) operation("setExtension", listOf(key, value))
        for (key in listOf("ca", "kn", "zz", "a1")) for (value in listOf(null, "", "foo_bar", "japanese", "ab")) operation("setUnicodeLocaleKeyword", listOf(key, value))
        for (tag in listOf("", "en-US-u-ca-japanese", "en-u-va-posix", "en--US", "en-a", "x-private")) operation("setLanguageTag", listOf(tag))
        for (id in listOf("en_US_POSIX", "en__ABC", "en@calendar=japanese", "x-private")) {
            val je = runCatching { j.setLocale(J(id)) }.exceptionOrNull()
            val ke = runCatching { k.setLocale(ULocale(id)) }.exceptionOrNull()
            assertEquals(je?.message, ke?.message)
            compare(j.build(), k.build())
        }
        operation("clearExtensions", emptyList()); operation("clear", emptyList())
    }
    @Test fun mappingFallbacksAndExceptionMetadataMatchOriginal() {
        for (key in listOf("calendar", "CA", "zz", "1a", "a1", "unknown", "", "a-b", "İ")) {
            assertEquals(J.toUnicodeLocaleKey(key), ULocale.toUnicodeLocaleKey(key)); assertEquals(J.toLegacyKey(key), ULocale.toLegacyKey(key))
            for (value in listOf("gregorian", "phonebook", "uslax", "foo", "foo_bar", "foo/bar", "", "a", "abc-", "00A4", "true")) {
                assertEquals(J.toUnicodeLocaleType(key, value), ULocale.toUnicodeLocaleType(key, value))
                assertEquals(J.toLegacyType(key, value), ULocale.toLegacyType(key, value))
            }
        }
        for (message in listOf(null, "error")) for (index in listOf(-9, -1, 0, 8)) {
            val j = com.ibm.icu.util.IllformedLocaleException(message, index); val k = IllformedLocaleException(message, index)
            assertEquals(j.message, k.message); assertEquals(j.errorIndex, k.getErrorIndex())
        }
        assertEquals(com.ibm.icu.util.IllformedLocaleException().message, IllformedLocaleException().message)
        assertEquals(com.ibm.icu.util.IllformedLocaleException(null).message, IllformedLocaleException(null).message)
    }
}
