// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.util.LocaleMatcher
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleDistanceTest {
    @Test fun completeSourceDistanceTableFingerprint() {
        val table = LocaleDistance.INSTANCE.testOnlyGetDistanceTable()
        assertEquals(630, table.size)
        var hash = -3750763034362895579L
        fun byte(value: Int) { hash = (hash xor (value and 255).toLong()) * 1099511628211L }
        for ((key, value) in table) {
            key.forEach { byte(it.code) }
            byte(0)
            for (shift in 0 until 32 step 8) byte(value ushr shift)
        }
        assertEquals(398970901289705364L, hash)
        assertEquals(50, LocaleDistance.INSTANCE.getDefaultScriptDistance())
        assertEquals(5, LocaleDistance.INSTANCE.getDefaultDemotionPerDesiredLocale())
    }
    @Test fun sourceDistancePackingAndResourceDataEquality() {
        assertEquals(5, LocaleDistance.INSTANCE.testOnlyDistance(ULocale("en_US"), ULocale("en_GB"), 50, LocaleMatcher.FavorSubtag.LANGUAGE))
        assertEquals(100, LocaleDistance.INSTANCE.testOnlyDistance(ULocale("en_US"), ULocale("en_GB"), 5, LocaleMatcher.FavorSubtag.LANGUAGE))
        val packed = (7 shl 10) or LocaleDistance.shiftDistance(5) or 3
        assertEquals(7, LocaleDistance.getIndex(packed))
        assertEquals(43, LocaleDistance.getShiftedDistance(packed))
        assertEquals(5.375, LocaleDistance.getDistanceDouble(packed))
        assertEquals(5, LocaleDistance.getDistanceFloor(packed))
        assertEquals(LocaleDistance.Data.load(), LocaleDistance.Data.load())
        assertTrue(LocaleDistance.INSTANCE.isParadigmLSR(LSR("en", "Latn", "US", 0)))
    }
}
