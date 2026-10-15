@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.Grouper as JGrouper
import com.ibm.icu.impl.number.PatternStringParser as JParser
import com.ibm.icu.impl.number.DecimalFormatProperties as JProperties
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JQuantity
import com.ibm.icu.number.NumberFormatter.GroupingStrategy as JStrategy
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class GrouperCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { e.javaClass.simpleName }
    private fun fields(value: Any): List<Short> = listOf("grouping1", "grouping2", "minGrouping").map { value.javaClass.getDeclaredField(it).apply { isAccessible = true }.getShort(value) }
    @Test fun strategiesAllLocaleMinimumsAndPatternWidthsMatchSource() {
        val jValues = listOf(0L, 999L, 1000L, 9999L, 10000L, 1000000L).map { JQuantity(it) } + listOf(JQuantity(1).apply { setMinInteger(8) })
        val kValues = listOf(0L, 999L, 1000L, 9999L, 10000L, 1000000L).map { DecimalQuantityCore(it) } + listOf(DecimalQuantityCore(1L).apply { setMinInteger(8) })
        val seen = mutableSetOf<List<Short>>()
        for (locale in JLocale.getAvailableLocales()) for (strategy in JStrategy.entries) for (pattern in listOf("0", "#,##0", "#,##,##0", "#,####0")) {
            val j = JGrouper.forStrategy(strategy).withLocaleData(locale, JParser.parseToPatternInfo(pattern))
            val k = GrouperCore.forStrategy(GrouperCore.GroupingStrategy.valueOf(strategy.name)).withLocaleData(ULocale.createCanonical(locale.name), PatternStringParserCore.parseToPatternInfo(pattern))
            assertEquals(fields(j), fields(k), "$locale/$strategy/$pattern")
            assertEquals(j.primary, k.getPrimary()); assertEquals(j.secondary, k.getSecondary())
            if (!seen.add(fields(j))) continue
            for (position in -2..12) for (i in jValues.indices) assertEquals(outcome { j.groupAtPosition(position, jValues[i]) }, outcome { k.groupAtPosition(position, kValues[i]) }, "$locale/$strategy/$pattern/$position/$i")
        }
        assertTrue(seen.size > 8)
    }
    @Test fun propertyNarrowingCachesNullsAndShortCircuitFailuresMatchSource() {
        val widths = listOf(Int.MIN_VALUE, -4, -3, -2, -1, 0, 1, 2, 3, 32767, 32768, 65535, 65536)
        for (first in widths) for (second in widths) for (minimum in listOf(-3, -2, -1, 0, 1, 2)) {
            val properties = JProperties().setGroupingUsed(true).setGroupingSize(first).setSecondaryGroupingSize(second).setMinimumGroupingDigits(minimum)
            val j = JGrouper.forProperties(properties); val k = GrouperCore.forProperties(true, first, second, minimum)
            assertEquals(fields(j), fields(k), "$first/$second/$minimum")
            assertEquals(JGrouper.getInstance(first.toShort(), second.toShort(), minimum.toShort()) === JGrouper.getInstance(first.toShort(), second.toShort(), minimum.toShort()),
                GrouperCore.getInstance(first.toShort(), second.toShort(), minimum.toShort()) === GrouperCore.getInstance(first.toShort(), second.toShort(), minimum.toShort()))
            for (position in listOf(Int.MIN_VALUE, -1, 0, 3, Int.MAX_VALUE)) assertEquals(outcome { j.groupAtPosition(position, null) }, outcome { k.groupAtPosition(position, null) }, "$first/$second/$minimum/$position/null")
        }
        for (strategy in JStrategy.entries) {
            val j = JGrouper.forStrategy(strategy); val k = GrouperCore.forStrategy(GrouperCore.GroupingStrategy.valueOf(strategy.name))
            assertEquals(outcome { fields(j.withLocaleData(JLocale.ENGLISH, null)) }, outcome { fields(k.withLocaleData(ULocale("en"), null)) }, "$strategy/null pattern")
        }
        assertEquals(outcome { JGrouper.forStrategy(null) }, outcome { GrouperCore.forStrategy(null) })
    }
}
