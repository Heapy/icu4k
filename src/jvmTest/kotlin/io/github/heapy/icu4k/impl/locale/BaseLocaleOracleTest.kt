package io.github.heapy.icu4k.impl.locale

import com.ibm.icu.impl.locale.BaseLocale as J
import com.ibm.icu.impl.locale.InternalLocaleBuilder as JB
import com.ibm.icu.impl.locale.LanguageTag as JT
import kotlin.random.Random
import kotlin.test.*

class BaseLocaleOracleTest {
    private fun compare(j: J, k: BaseLocale) {
        assertEquals(j.language, k.getLanguage()); assertEquals(j.script, k.getScript())
        assertEquals(j.region, k.getRegion()); assertEquals(j.variant, k.getVariant())
        assertEquals(j.toString(), k.toString()); assertEquals(j.hashCode(), k.hashCode())
        assertSame(j.language, k.getLanguage()); assertSame(j.script, k.getScript())
        assertSame(j.region, k.getRegion()); assertSame(j.variant, k.getVariant())
    }
    @Test fun normalizationHashInterningAndCacheMatchOriginal() {
        compare(J.ROOT, BaseLocale.ROOT)
        assertSame(BaseLocale.ROOT, BaseLocale.getInstance(null, null, null, null))
        val jc = J::class.java.getDeclaredConstructor(*Array(4) { String::class.java }).apply { isAccessible = true }
        val kc = BaseLocale::class.java.getDeclaredConstructor(*Array(4) { String::class.java }).apply { isAccessible = true }
        val random = Random(818701)
        val fixtures = listOf(listOf("EN", "lATN", "us", "posix"), listOf("iw", "", "IL", ""), listOf("", "1AbC", "1a", "éAb"), listOf("", "", "", "")) +
            List(1000) { List(4) { buildString { repeat(random.nextInt(12)) { append("aAzZ09_İé😀".random(random)) } } } }
        for (fields in fixtures) compare(jc.newInstance(*fields.toTypedArray()), kc.newInstance(*fields.toTypedArray()))
        for (fields in listOf(listOf("EN", "Latn", "us", "posix"), listOf("TR", "Cyrl", "419", "1901"), listOf("in", "", "ID", ""))) {
            val j = J.getInstance(fields[0], fields[1], fields[2], fields[3]); val k = BaseLocale.getInstance(fields[0], fields[1], fields[2], fields[3])
            compare(j, k)
            val changed = fields.map { it.lowercase() }
            assertSame(j, J.getInstance(changed[0], changed[1], changed[2], changed[3]))
            assertSame(k, BaseLocale.getInstance(changed[0], changed[1], changed[2], changed[3]))
        }
    }
    @Test fun keyHashEqualityComparisonAndNormalizationMatchOriginal() {
        val type = Class.forName("com.ibm.icu.impl.locale.BaseLocale\$Key")
        val constructor = type.getDeclaredConstructor(*Array(4) { String::class.java }).apply { isAccessible = true }
        val normalize = type.getDeclaredMethod("normalize", type).apply { isAccessible = true }
        val compare = type.getDeclaredMethod("compareTo", type).apply { isAccessible = true }
        val random = Random(913811)
        repeat(500) {
            val fields = List<String?>(4) { if (random.nextInt(10) == 0) null else buildString { repeat(random.nextInt(8)) { append("abcXYZ_01İ😀".random(random)) } } }
            val j = constructor.newInstance(*fields.toTypedArray()); val k = BaseLocale.Key(fields[0], fields[1], fields[2], fields[3])
            val jn = normalize.invoke(null, j); val kn = BaseLocale.Key.normalize(k)
            assertEquals(j.hashCode(), k.hashCode()); assertEquals(jn.hashCode(), kn.hashCode())
            for ((name, value) in mapOf("_lang" to kn.language, "_scrt" to kn.script, "_regn" to kn.region, "_vart" to kn.variant))
                assertEquals(type.getDeclaredField(name).apply { isAccessible = true }.get(jn), value)
            for (other in listOf(fields.map { it?.lowercase() }, fields.map { it?.uppercase() }, listOf("en", "Latn", "US", "POSIX"))) {
                val jo = constructor.newInstance(*other.toTypedArray()); val ko = BaseLocale.Key(other[0], other[1], other[2], other[3])
                assertEquals(j == jo, k == ko); assertEquals(compare.invoke(j, jo), k.compareTo(ko))
            }
        }
    }
    @Test fun builderBaseImportExportAndTagConversionMatchOriginal() {
        val j = JB(); val k = InternalLocaleBuilder()
        fun state() {
            compare(j.baseLocale, k.getBaseLocale())
            assertEquals(j.localeExtensions.id, k.getLocaleExtensions().getID())
            val jt = JT.parseLocale(j.baseLocale, j.localeExtensions)
            val kt = LanguageTag.parseLocale(k.getBaseLocale(), k.getLocaleExtensions())
            assertEquals(jt.toString(), kt.toString()); assertEquals(jt.privateuse, kt.getPrivateuse())
            assertEquals(jt.variants, kt.getVariants()); assertEquals(jt.extensions, kt.getExtensions())
        }
        for (tag in listOf("und", "en-Latn-US-1901-u-ca-japanese-x-abc-lvariant-POSIX", "zh-cmn-Hans-CN", "x-private", "iw-IL", "en-x-lvariant-foo-bar", "en-x-abc-lvariant")) {
            j.setLanguageTag(JT.parse(tag, null)); k.setLanguageTag(LanguageTag.parse(tag)); state()
            val je = runCatching { j.setLocale(j.baseLocale, j.localeExtensions) }.exceptionOrNull()
            val ke = runCatching { k.setLocale(k.getBaseLocale(), k.getLocaleExtensions()) }.exceptionOrNull()
            if (je != null) {
                assertIs<com.ibm.icu.impl.locale.LocaleSyntaxException>(je); assertIs<LocaleSyntaxException>(ke)
                assertEquals(je.message, ke.message); assertEquals(je.errorIndex, ke.getErrorIndex())
            } else assertNull(ke)
            state()
        }
        val bases = listOf(listOf("en", "Latn", "US", "1901_POSIX"), listOf("iw", "", "IL", ""), listOf("ji", "", "", ""), listOf("in", "", "", ""),
            listOf("", "Latn", "", ""), listOf("", "", "", ""), listOf("en", "", "", "1901_ABC_DEF"), listOf("", "", "", "abc"),
            listOf("a", "", "", ""), listOf("en", "bad", "", ""), listOf("en", "", "bad", ""), listOf("en", "", "", "1901_"))
        for (fields in bases) {
            val jb = J.getInstance(fields[0], fields[1], fields[2], fields[3]); val kb = BaseLocale.getInstance(fields[0], fields[1], fields[2], fields[3])
            for (extension in listOf("", "a-foo-u-ca-japanese-x-private", "x-only")) {
                val je = JB().setExtensions(extension).localeExtensions; val ke = InternalLocaleBuilder().setExtensions(extension).getLocaleExtensions()
                val jt = JT.parseLocale(jb, je); val kt = LanguageTag.parseLocale(kb, ke)
                assertEquals(jt.toString(), kt.toString()); assertEquals(jt.privateuse, kt.getPrivateuse()); assertEquals(jt.language, kt.getLanguage())
                val ja = runCatching { j.setLocale(jb, je) }.exceptionOrNull()
                val ka = runCatching { k.setLocale(kb, ke) }.exceptionOrNull()
                if (ja != null) {
                    assertIs<com.ibm.icu.impl.locale.LocaleSyntaxException>(ja); assertIs<LocaleSyntaxException>(ka)
                    assertEquals(ja.message, ka.message); assertEquals(ja.errorIndex, ka.getErrorIndex())
                } else assertNull(ka)
                state()
            }
        }
        assertSame(j, j.setLocale(J.ROOT, null)); assertSame(k, k.setLocale(BaseLocale.ROOT, null)); state()
    }
}
