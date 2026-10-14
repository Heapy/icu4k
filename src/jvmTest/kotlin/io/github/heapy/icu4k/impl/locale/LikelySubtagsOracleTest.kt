@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.util.*
import java.util.Locale
import kotlin.test.*
import com.ibm.icu.impl.locale.LikelySubtags as Original
import com.ibm.icu.impl.locale.LSR as OriginalLSR
import com.ibm.icu.util.ULocale as OriginalLocale

class LikelySubtagsOracleTest {
    private fun fields(lsr: LSR): List<Any> = listOf(lsr.language, lsr.script, lsr.region, lsr.flags)
    private fun fields(lsr: OriginalLSR): List<Any> = listOf(lsr.language, lsr.script, lsr.region, lsr.flags)
    @Test fun completeDataAndTrieTableMatchSource() {
        val expected = Original.Data.load()
        val actual = LikelySubtags.Data.load()
        assertEquals(expected.languageAliases, actual.languageAliases)
        assertEquals(expected.regionAliases, actual.regionAliases)
        assertContentEquals(expected.trie, actual.trie)
        assertEquals(expected.lsrs.map(::fields), actual.lsrs.map(::fields))
        assertEquals(Original.INSTANCE.toString(), LikelySubtags.INSTANCE.toString())
    }
    @Test fun everyFullResourceTupleMatchesSource() {
        for (lsr in LikelySubtags.Data.load().lsrs.drop(2)) {
            val source = OriginalLocale(lsr.language, lsr.script, lsr.region)
            val actual = ULocale(lsr.language, lsr.script, lsr.region)
            assertEquals(OriginalLocale.addLikelySubtags(source).name, ULocale.addLikelySubtags(actual).getName(), "$actual maximize")
            for (favor in ULocale.Minimize.entries) assertEquals(
                OriginalLocale.minimizeSubtags(source, OriginalLocale.Minimize.valueOf(favor.name)).name,
                ULocale.minimizeSubtags(actual, favor).getName(), "$actual/$favor")
        }
    }
    @Test fun maximizationMinimizationCanonicalizationAndFlagsMatchMatrix() {
        for (language in listOf("", "und", "en", "zh", "sr", "ru", "iw", "in", "ji", "no", "nn", "sh", "mo", "tl", "fil", "az", "pa", "ks", "yue", "xxx", "zzzz", "art"))
            for (script in listOf("", "Zzzz", "Latn", "Cyrl", "Hans", "Hant", "Arab", "Deva", "Qaaa"))
                for (region in listOf("", "ZZ", "US", "CN", "TW", "HK", "RS", "RU", "001", "150", "419", "SU", "BU", "AN", "EU", "QO", "XA", "QQ")) {
                    val source = OriginalLocale(language, script, region)
                    val actual = ULocale(language, script, region)
                    for (keepUnknown in listOf(false, true)) {
                        assertEquals(fields(Original.INSTANCE.makeMaximizedLsrFrom(source, keepUnknown)),
                            fields(LikelySubtags.INSTANCE.makeMaximizedLsrFrom(actual, keepUnknown)), "$actual/$keepUnknown")
                    }
                    assertEquals(OriginalLocale.addLikelySubtags(source).name, ULocale.addLikelySubtags(actual).getName(), "$actual maximize")
                    for (favor in listOf(ULocale.Minimize.FAVOR_REGION, ULocale.Minimize.FAVOR_SCRIPT, null)) {
                        val jfavor = favor?.let { OriginalLocale.Minimize.valueOf(it.name) }
                        assertEquals(OriginalLocale.minimizeSubtags(source, jfavor).name, ULocale.minimizeSubtags(actual, favor).getName(), "$actual/$favor")
                    }
                    assertEquals(Original.INSTANCE.canonicalize(source).name, LikelySubtags.INSTANCE.canonicalize(actual).getName(), "$actual canonicalize")
                }
    }
    @Test fun variantsKeywordsPrivateUseAndJavaLocaleBoundariesMatch() {
        val names = listOf("en_XA", "ar_XB", "fr_XC", "en__PSACCENT", "ar__PSBIDI", "fr__PSCRACK", "fr_FR_PSCRACK", "en_US_POSIX", "en__POSIX", "sr_Latn__REVISED", "@x=test", "en@calendar=japanese;collation=phonebook", "und_Zzzz_ZZ@x=foo", "zzzz__POSIX@calendar=japanese", "en_US_EN", "en__EN", "iw_BU_POSIX@x=foo")
        for (name in names) {
            val source = OriginalLocale(name)
            val actual = ULocale(name)
            for (keep in listOf(false, true)) assertEquals(fields(Original.INSTANCE.makeMaximizedLsrFrom(source, keep)), fields(LikelySubtags.INSTANCE.makeMaximizedLsrFrom(actual, keep)), "$name/$keep")
            assertEquals(OriginalLocale.addLikelySubtags(source).name, ULocale.addLikelySubtags(actual).getName(), name)
            for (favor in ULocale.Minimize.entries) assertEquals(OriginalLocale.minimizeSubtags(source, OriginalLocale.Minimize.valueOf(favor.name)).name, ULocale.minimizeSubtags(actual, favor).getName(), "$name/$favor")
            assertEquals(Original.INSTANCE.canonicalize(source).name, LikelySubtags.INSTANCE.canonicalize(actual).getName(), name)
        }
        @Suppress("DEPRECATION")
        val javaLocales = listOf(Locale("no", "NO", "NY"), Locale("ja", "JP", "JP"), Locale("en", "", "PSACCENT")) +
            listOf("x-test", "en-XA", "ar-XB", "fr-XC", "zh-Hant-TW", "und", "und-x-test", "en-US-u-va-posix").map(Locale::forLanguageTag)
        for (locale in javaLocales) assertEquals(fields(Original.INSTANCE.makeMaximizedLsrFrom(locale)), fields(LikelySubtags.INSTANCE.makeMaximizedLsrFrom(locale)), locale.toString())
    }
    @Test fun likelyComparisonCacheBitsMatchSource() {
        val method = Original::class.java.getDeclaredMethod("compareLikely", OriginalLSR::class.java, OriginalLSR::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val names = listOf("fr_FR", "fr_CH", "zh_Hans_CN", "zh_Hant_TW", "sr_Latn_RS", "sr_Cyrl_RS", "en_US", "en_GB", "xx_Zzzz_ZZ")
        for (name in names) {
            val j = Original.INSTANCE.makeMaximizedLsrFrom(OriginalLocale(name), false)
            val k = LikelySubtags.INSTANCE.makeMaximizedLsrFrom(ULocale(name), false)
            var sourceInfo = -1
            var actualInfo = -1
            for (other in names) {
                val jo = Original.INSTANCE.makeMaximizedLsrFrom(OriginalLocale(other), false)
                val ko = LikelySubtags.INSTANCE.makeMaximizedLsrFrom(ULocale(other), false)
                sourceInfo = method.invoke(Original.INSTANCE, j, jo, sourceInfo) as Int
                actualInfo = LikelySubtags.INSTANCE.compareLikely(k, ko, actualInfo)
                assertEquals(sourceInfo, actualInfo, "$name/$other")
            }
        }
    }
}
