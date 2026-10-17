// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

import com.ibm.icu.impl.locale.LocaleDistance as Source
import com.ibm.icu.impl.locale.LSR as SourceLSR
import com.ibm.icu.util.LocaleMatcher as SourceMatcher
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.LocaleMatcher
import io.github.heapy.icu4k.util.ULocale
import kotlin.random.Random
import kotlin.test.*

class LocaleDistanceOracleTest {
    @Test fun pinnedDataTablesDefaultsAndParadigmsMatchSource() {
        val s = Source.Data.load(); val p = LocaleDistance.Data.load()
        assertContentEquals(s.trie, p.trie)
        assertContentEquals(s.regionToPartitionsIndex, p.regionToPartitionsIndex)
        assertContentEquals(s.partitionArrays, p.partitionArrays)
        assertContentEquals(s.distances, p.distances)
        assertEquals(s.paradigmLSRs.map { listOf(it.language, it.script, it.region, it.flags) }, p.paradigmLSRs.map { listOf(it.language, it.script, it.region, it.flags) })
        assertEquals(Source.INSTANCE.testOnlyGetDistanceTable(), LocaleDistance.INSTANCE.testOnlyGetDistanceTable())
        assertEquals(Source.INSTANCE.toString(), LocaleDistance.INSTANCE.toString())
        assertEquals(Source.INSTANCE.defaultScriptDistance, LocaleDistance.INSTANCE.getDefaultScriptDistance())
        assertEquals(Source.INSTANCE.defaultDemotionPerDesiredLocale, LocaleDistance.INSTANCE.getDefaultDemotionPerDesiredLocale())
        assertEquals(1, p.hashCode())
        assertEquals(p, LocaleDistance.Data.load())
        val copy = LocaleDistance.Data(p.trie.copyOf(), p.regionToPartitionsIndex.copyOf(), p.partitionArrays.copyOf(), p.paradigmLSRs.toSet(), p.distances.copyOf())
        assertEquals(p, copy)
        copy.distances[0]++
        assertNotEquals(p, copy)
        for (loc in SourceLocale.getAvailableLocales()) {
            val lsr = com.ibm.icu.impl.locale.LikelySubtags.INSTANCE.makeMaximizedLsrFrom(loc, false)
            assertEquals(Source.INSTANCE.isParadigmLSR(lsr), LocaleDistance.INSTANCE.isParadigmLSR(LSR(lsr.language, lsr.script, lsr.region, lsr.flags)), loc.toString())
        }
    }
    @Test fun packedHelpersMatchAllIndexFractionAndNoMatchBits() {
        val values = listOf(Int.MIN_VALUE, -1024, -224, -1, 0, 1, 7, 8, 399, 800, 1023, 1024, 2047, Int.MAX_VALUE)
        for (value in values) {
            assertEquals(Source.shiftDistance(value), LocaleDistance.shiftDistance(value))
            assertEquals(Source.getShiftedDistance(value), LocaleDistance.getShiftedDistance(value))
            assertEquals(Source.getDistanceDouble(value), LocaleDistance.getDistanceDouble(value))
            assertEquals(Source.getDistanceFloor(value), LocaleDistance.getDistanceFloor(value))
            if (value >= 0) assertEquals(Source.getIndex(value), LocaleDistance.getIndex(value))
            else { assertFailsWith<AssertionError> { Source.getIndex(value) }; assertFailsWith<AssertionError> { LocaleDistance.getIndex(value) } }
        }
    }
    @Test fun trieDistancesThresholdsFlagsDirectionsAndTies() {
        val random = Random(78_304)
        val pool = SourceLocale.getAvailableLocales().map { com.ibm.icu.impl.locale.LikelySubtags.INSTANCE.makeMaximizedLsrFrom(it, false) } +
            listOf(SourceLSR("und", "", "", 7), SourceLSR("zz", "Zzzz", "ZZ", 7), SourceLSR("en", "Latn", "001", 7),
                SourceLSR("ar", "Arab", "019", 7), SourceLSR("", "", "", 0))
        fun lsr(): SourceLSR = pool.random(random).let { SourceLSR(it.language, it.script, it.region, random.nextInt(8)) }
        fun port(lsr: SourceLSR) = LSR(lsr.language, lsr.script, lsr.region, lsr.flags)
        repeat(30_000) { iteration ->
            val desired = lsr()
            val supported = Array(random.nextInt(0, 12)) { lsr() }
            val length = random.nextInt(supported.size + 1)
            val threshold = listOf(-8, 0, 1, 7, 8, 9, 16, 24, 39, 40, 41, 400, 800, 801).random(random)
            val favor = (SourceMatcher.FavorSubtag.values().toList() + null).random(random)
            val direction = (SourceMatcher.Direction.values().toList() + null).random(random)
            val actual = LocaleDistance.INSTANCE.getBestIndexAndDistance(port(desired), supported.map(::port).toTypedArray(), length, threshold,
                favor?.let { LocaleMatcher.FavorSubtag.valueOf(it.name) }, direction?.let { LocaleMatcher.Direction.valueOf(it.name) })
            val expected = Source.INSTANCE.getBestIndexAndDistance(desired, supported, length, threshold, favor, direction)
            assertEquals(expected, actual, "iteration $iteration desired $desired supported ${supported.toList()} length $length threshold $threshold $favor/$direction")
        }
        for (desired in listOf("en_US", "en_GB", "es_MX", "es_419", "pt_AO", "nb", "no", "sr_Latn", "zh_HK", "gsw", "af"))
            for (supported in listOf("en_US", "en_GB", "es_ES", "es_AR", "pt_PT", "pt_BR", "nn", "da", "sr_Cyrl", "zh_TW", "de", "nl"))
                for (threshold in listOf(1, 5, 50, 100)) for (favor in LocaleMatcher.FavorSubtag.entries) {
                    assertEquals(Source.INSTANCE.testOnlyDistance(SourceLocale(desired), SourceLocale(supported), threshold, SourceMatcher.FavorSubtag.valueOf(favor.name)),
                        LocaleDistance.INSTANCE.testOnlyDistance(ULocale(desired), ULocale(supported), threshold, favor), "$desired/$supported/$threshold/$favor")
                }
    }
}
