@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class LikelySubtagsTest {
    @Test fun maximizationMinimizationAndTrailingIdentity() {
        assertEquals("en_Latn_US", ULocale.addLikelySubtags(ULocale("en")).getName())
        assertEquals("zh_Hant_TW", ULocale.addLikelySubtags(ULocale("zh_TW")).getName())
        assertEquals("sr_Cyrl_RS", ULocale.addLikelySubtags(ULocale("sr")).getName())
        assertEquals("en", ULocale.minimizeSubtags(ULocale("en_Latn_US")).getName())
        assertEquals("zh_TW", ULocale.minimizeSubtags(ULocale("zh_Hant_TW"), ULocale.Minimize.FAVOR_REGION).getName())
        assertEquals("zh_Hant", ULocale.minimizeSubtags(ULocale("zh_Hant_TW"), ULocale.Minimize.FAVOR_SCRIPT).getName())
        assertEquals("en_Latn_US_POSIX@calendar=japanese", ULocale.addLikelySubtags(ULocale("en__POSIX@calendar=japanese")).getName())
        assertEquals("en__POSIX@calendar=japanese", ULocale.minimizeSubtags(ULocale("en_Latn_US_POSIX@calendar=japanese")).getName())
        assertFailsWith<NullPointerException> { ULocale.addLikelySubtags(null) }
        assertFailsWith<NullPointerException> { ULocale.minimizeSubtags(null) }
    }
    @Test fun matcherIdentityAliasesAndPseudolocales() {
        val engine = LikelySubtags.INSTANCE
        val untouched = ULocale("en_US_POSIX@calendar=japanese")
        assertSame(untouched, engine.canonicalize(untouched))
        assertEquals("he-Hebr-IL", engine.makeMaximizedLsrFrom(ULocale("iw"), true).toString())
        assertEquals("'en-'-XA", engine.makeMaximizedLsrFrom(ULocale("en_XA"), false).toString())
        assertEquals("+ar-+-XB", engine.makeMaximizedLsrFrom(ULocale("ar__PSBIDI"), false).toString())
        val privateUse = engine.makeMaximizedLsrFrom(ULocale("@x=test"), false)
        assertEquals("und-x-test", privateUse.language)
        assertEquals(LSR.EXPLICIT_LSR, privateUse.flags)
        assertEquals("zzzz", engine.makeMaximizedLsrFrom(ULocale("zzzz"), true).language)
    }
    @Test fun fullResourceDataEqualityAndRoundTrip() {
        val a = LikelySubtags.Data.load()
        val b = LikelySubtags.Data.load()
        assertEquals(a, b)
        assertEquals(1, a.hashCode())
        assertNotEquals(a, LikelySubtags.Data(a.languageAliases, b.regionAliases, byteArrayOf(), b.lsrs))
        for (lsr in a.lsrs.drop(2)) {
            val locale = ULocale(lsr.language, lsr.script, lsr.region)
            val max = ULocale.addLikelySubtags(locale)
            assertEquals(LikelySubtags.INSTANCE.canonicalize(locale), max)
            for (favor in ULocale.Minimize.entries) {
                val min = ULocale.minimizeSubtags(locale, favor)
                assertEquals(max, ULocale.addLikelySubtags(min), "$locale/$favor")
            }
        }
    }
}
