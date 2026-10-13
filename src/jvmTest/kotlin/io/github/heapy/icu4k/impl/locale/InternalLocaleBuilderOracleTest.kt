package io.github.heapy.icu4k.impl.locale

import com.ibm.icu.impl.locale.InternalLocaleBuilder as J
import com.ibm.icu.impl.locale.LanguageTag as JT
import com.ibm.icu.impl.locale.LocaleExtensions as JE
import kotlin.random.Random
import kotlin.test.*

class InternalLocaleBuilderOracleTest {
    private fun compareExtensions(j: JE, k: LocaleExtensions) {
        assertEquals(j.id, k.getID()); assertEquals(j.toString(), k.toString()); assertEquals(j.hashCode(), k.hashCode())
        assertEquals(j.isEmpty, k.isEmpty()); assertEquals(j.keys.toList(), k.getKeys().toList())
        assertEquals(j.unicodeLocaleAttributes.toList(), k.getUnicodeLocaleAttributes().toList())
        assertEquals(j.unicodeLocaleKeys.toList(), k.getUnicodeLocaleKeys().toList())
        for (c in "aAuUxXzZ09!") {
            assertEquals(j.getExtensionValue(c), k.getExtensionValue(c))
            assertEquals(j.getExtension(c)?.id, k.getExtension(c)?.getID())
        }
        for (key in listOf("ca", "CA", "nu", "co", "kf", "missing")) assertEquals(j.getUnicodeLocaleType(key), k.getUnicodeLocaleType(key))
    }
    @Test fun scalarAndExtensionMutationsErrorsAndLanguageTagImportMatchOriginal() {
        val j = J(); val k = InternalLocaleBuilder()
        fun state() {
            for (name in listOf("_language", "_script", "_region", "_variant")) {
                val expected = j.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(j)
                val actual = k.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(k)
                assertEquals(expected, actual, name)
            }
            compareExtensions(j.localeExtensions, k.getLocaleExtensions())
        }
        fun operation(name: String, args: List<Any?>) {
            val jm = j.javaClass.methods.single { it.name == name && it.parameterCount == args.size }
            val km = k.javaClass.methods.single { it.name == name && it.parameterCount == args.size }
            val je = runCatching { assertSame(j, jm.invoke(j, *args.toTypedArray())) }.exceptionOrNull()?.let { (it as java.lang.reflect.InvocationTargetException).cause!! }
            val ke = runCatching { assertSame(k, km.invoke(k, *args.toTypedArray())) }.exceptionOrNull()?.let { (it as java.lang.reflect.InvocationTargetException).cause!! }
            if (je is com.ibm.icu.impl.locale.LocaleSyntaxException) {
                assertIs<LocaleSyntaxException>(ke); assertEquals(je.message, ke.message); assertEquals(je.errorIndex, ke.getErrorIndex())
            } else if (je is IndexOutOfBoundsException) assertIs<IndexOutOfBoundsException>(ke)
            else { assertNull(je); assertNull(ke) }
            state()
        }
        val cases = listOf(
            "setLanguage" to listOf(null, "", "en", "EN", "a", "abcdefghi", "İ"),
            "setScript" to listOf(null, "", "Latn", "LATN", "abc", "1234"),
            "setRegion" to listOf(null, "", "US", "419", "abc", "1"),
            "setVariant" to listOf(null, "", "1901-posix", "POSIX_1901", "abc", "1901_", "1901__POSIX"),
            "addUnicodeLocaleAttribute" to listOf(null, "", "foo", "FOO", "ab", "abcdefghi"),
            "removeUnicodeLocaleAttribute" to listOf(null, "foo", "FOO", "ab"))
        for ((name, values) in cases) for (value in values) operation(name, listOf(value))
        for (key in listOf("ca", "CA", "nu", "a1", "1a", "")) for (type in listOf(null, "", "japanese", "foo_bar", "ab", "foo--bar"))
            operation("setUnicodeLocaleKeyword", listOf(key, type))
        for (key in "aAuUxX!") for (value in listOf(null, "", "ab", "a", "abc_def", "ca-japanese-nu-thai", "foo-ca-yes-ca-no-nu", "lvariant-POSIX", "abc-lvariant-DEF", "abc-"))
            operation("setExtension", listOf(key, value))
        for (value in listOf(null, "", "a-foo-b-bar-x-private", "a-foo-a-bar", "A-foo-a-bar", "u-foo-foo-ca-japanese-ca-thai-nu", "x-lvariant-POSIX", "x-abc-lvariant", "a", "a-b-foo", "x", "a-foo-b", "u-ca-japanese-", "abc", "_a_foo"))
            operation("setExtensions", listOf(value))
        val random = Random(788189)
        val parts = listOf("a", "b", "A", "u", "x", "ca", "nu", "foo", "FOO", "bar", "true", "yes", "lvariant", "", "!")
        repeat(500) { operation("setExtensions", listOf(List(random.nextInt(1, 9)) { parts.random(random) }.joinToString("-"))) }
        for (tag in listOf("und", "en-Latn-US-1996-1901-u-ca-japanese-x-abc-lvariant-POSIX", "zh-cmn-Hans-CN", "x-private", "en-a-foo-A-bar")) {
            assertSame(j, j.setLanguageTag(JT.parse(tag, null))); assertSame(k, k.setLanguageTag(LanguageTag.parse(tag))); state()
        }
        operation("clearExtensions", emptyList()); operation("clear", emptyList())
    }
    @Test fun constantsPredicatesAndCaseInsensitiveKeyQuirksMatchOriginal() {
        for ((j, k) in listOf(JE.EMPTY_EXTENSIONS to LocaleExtensions.EMPTY_EXTENSIONS, JE.CALENDAR_JAPANESE to LocaleExtensions.CALENDAR_JAPANESE, JE.NUMBER_THAI to LocaleExtensions.NUMBER_THAI)) compareExtensions(j, k)
        for (code in 0..0xffff) assertEquals(JE.isValidKey(code.toChar()), LocaleExtensions.isValidKey(code.toChar()))
        val type = Class.forName("com.ibm.icu.impl.locale.InternalLocaleBuilder\$CaseInsensitiveChar")
        val constructor = type.getDeclaredConstructor(Char::class.javaPrimitiveType).apply { isAccessible = true }
        for (a in "aAuUxX09") for (b in "aAuUxX09") {
            val ja = constructor.newInstance(a); val jb = constructor.newInstance(b)
            val ka = InternalLocaleBuilder.CaseInsensitiveChar(a); val kb = InternalLocaleBuilder.CaseInsensitiveChar(b)
            assertEquals(ja == jb, ka == kb, "$a/$b"); assertEquals(ja.hashCode(), ka.hashCode())
        }
        val remove = J::class.java.getDeclaredMethod("removePrivateuseVariant", String::class.java).apply { isAccessible = true }
        for (value in listOf("", "abc", "lvariant", "lvariant-POSIX", "abc-lvariant", "abc-LVARIANT-foo-bar", "abc-lvariant-"))
            assertEquals(remove.invoke(null, value), InternalLocaleBuilder.removePrivateuseVariant(value))
    }
}
