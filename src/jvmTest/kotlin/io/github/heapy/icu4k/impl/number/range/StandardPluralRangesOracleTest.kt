package io.github.heapy.icu4k.impl.number.range

import com.ibm.icu.impl.number.range.StandardPluralRanges as JRange
import com.ibm.icu.impl.StandardPlural as JPlural
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class StandardPluralRangesOracleTest {
    private fun check(j: JRange, k: StandardPluralRanges, label: String) {
        for (first in JPlural.entries + listOf(null)) for (second in JPlural.entries + listOf(null)) {
            assertEquals(j.resolve(first, second).name, k.resolve(first?.let { StandardPlural.valueOf(it.name) }, second?.let { StandardPlural.valueOf(it.name) }).name, "$label/$first/$second")
        }
    }
    @Test fun allSetsAndLocalesMatchSource() {
        val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "pluralRanges")
        val rules = bundle.get("rules")
        for (i in 0 until rules.getSize()) {
            val set = rules.get(i).getKey()!!
            check(JRange.forSet(set), StandardPluralRanges.forSet(set), set)
        }
        val locales = bundle.get("locales")
        val ids = (0 until locales.getSize()).map { locales.get(it).getKey()!! } + listOf("", "root", "zz_ZZ", "iw_IL", "en_US", "pt_PT", "zh_Hant_TW")
        for (id in ids) {
            val j = JLocale(id); val k = ULocale(id)
            assertEquals(JRange.getSetForLocale(j), StandardPluralRanges.getSetForLocale(k), id)
            check(JRange.forLocale(j), StandardPluralRanges.forLocale(k), id)
        }
        check(JRange.DEFAULT, StandardPluralRanges.DEFAULT, "default")
        assertFailsWith<io.github.heapy.icu4k.util.MissingResourceException> { StandardPluralRanges.forSet("unknown") }
    }
}
