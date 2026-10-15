// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.VersionInfo
import kotlin.test.*

internal fun collatorMetadata(t: CollationTailoring, valid: ULocale? = t.actualLocale): RuleBasedCollatorMetadata =
    RuleBasedCollatorMetadata({ t }, { t.data }, { t.settings!!.readOnly() }, valid)

class RuleBasedCollatorMetadataTest {
    @Test fun rulesSetsAndBehaviorComparison() {
        val a=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("&a<b<c&d<ch/e")
        val b=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("&a < b < c &d < ch/e")
        val am=collatorMetadata(a); val bm=collatorMetadata(b)
        assertTrue(am.hasSameBehavior(bm))
        assertEquals(am.behaviorHashCode(),bm.behaviorHashCode())
        assertEquals("&a<b<c&d<ch/e",am.getRules())
        assertTrue(am.getRules(true).endsWith(am.getRules()))
        assertTrue(am.getTailoredSet().contains("ch"))
        val contractions=UnicodeSet("[z]"); val expansions=UnicodeSet("[z]")
        am.getContractionsAndExpansions(contractions,expansions,true)
        assertTrue(contractions.contains("ch")); assertTrue(expansions.contains("ch"))
        val extra=UnicodeSet("[z]"); am.internalAddContractions('c'.code,extra)
        assertTrue(extra.contains("ch")); assertTrue(extra.contains('z'.code))
        assertFalse(am.hasSameBehavior(collatorMetadata(CollationRoot.getRoot())))
    }
    @Test fun localeOverlayCopyAndSharedExpansionCache() {
        val t=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("&a<b")
        t.actualLocale=ULocale("de")
        val a=collatorMetadata(t,ULocale("de_DE"))
        assertEquals("de",a.getLocale(ULocale.ACTUAL_LOCALE)!!.getName())
        a.setLocale(ULocale("fr"),ULocale("fr"))
        assertEquals("fr",a.getLocale(ULocale.ACTUAL_LOCALE)!!.getName())
        assertEquals("de",t.actualLocale!!.getName())
        val b=collatorMetadata(t); b.copyLocaleFrom(a)
        assertEquals(a.getLocale(ULocale.VALID_LOCALE),b.getLocale(ULocale.VALID_LOCALE))
        assertEquals(a.getLocale(ULocale.ACTUAL_LOCALE),b.getLocale(ULocale.ACTUAL_LOCALE))
        a.initMaxExpansions(); val cached=t.maxExpansions
        assertNotNull(cached); b.initMaxExpansions(); assertSame(cached,t.maxExpansions)
        assertTrue(a.isUnsafe(0x301)); assertFalse(a.isUnsafe('a'.code))
        assertFailsWith<IllegalArgumentException> { a.getLocale(null) }
        a.setLocale(ULocale("de_AT"),ULocale("de"))
        assertEquals("de",a.getLocale(ULocale.ACTUAL_LOCALE)!!.getName())
    }
    @Test fun runtimeAndUcaVersionDecoding() {
        val m=collatorMetadata(CollationRoot.getRoot())
        assertEquals(VersionInfo.getInstance(9),VersionInfo.UCOL_RUNTIME_VERSION)
        assertEquals(153,m.getVersion().major)
        assertEquals(VersionInfo.getInstance(17),m.getUCAVersion())
    }
}
