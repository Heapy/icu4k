package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.DayPeriodRules as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DayPeriodRulesOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: java.lang.reflect.InvocationTargetException) {
        failure(e.targetException)
    } catch (e: Exception) { failure(e) }
    private fun failure(e: Throwable): String {
        if (e is VirtualMachineError) throw e
        return when (e) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            else -> e.javaClass.simpleName + ":" + e.message
        }
    }
    private fun compare(a: Source?, b: DayPeriodRules?, label: String) {
        assertEquals(a == null, b == null, label)
        if (a == null || b == null) return
        assertEquals(a.hasMidnight(), b.hasMidnight(), label)
        assertEquals(a.hasNoon(), b.hasNoon(), label)
        for (hour in -1..24) assertEquals(outcome { a.getDayPeriodForHour(hour)?.name }, outcome { b.getDayPeriodForHour(hour)?.name }, "$label/$hour")
        for (period in Source.DayPeriod.entries) assertEquals(outcome { a.getMidPointForDayPeriod(period) }, outcome { b.getMidPointForDayPeriod(DayPeriodRules.DayPeriod.valueOf(period.name)) }, "$label/$period")
        assertEquals(outcome { a.getMidPointForDayPeriod(null) }, outcome { b.getMidPointForDayPeriod(null) }, label)
    }
    @Test fun everyResourceLocaleAndFallbackIncludingUnknownAndKeywords() {
        val sourceData = Source::class.java.getDeclaredField("DATA").apply { isAccessible = true }.get(null)
        @Suppress("UNCHECKED_CAST")
        val map = sourceData.javaClass.getDeclaredField("localesToRuleSetNumMap").apply { isAccessible = true }.get(sourceData) as Map<String, Int>
        assertTrue(map.size > 80)
        val ids = map.keys + SourceLocale.getAvailableLocales().map { it.baseName } + listOf("", "root", "zz", "zz_ZZ", "und", "en_US_POSIX", "zh_Hant_HK", "@numbers=arab")
        for (id in ids) for (suffix in listOf("", "_ZZ_VARIANT", "@calendar=chinese;numbers=arab")) {
            val locale = id + suffix
            compare(Source.getInstance(SourceLocale(locale)), DayPeriodRules.getInstance(ULocale(locale)), locale)
        }
        for ((_, group) in map.keys.groupBy { map[it] }) {
            val first = DayPeriodRules.getInstance(ULocale(group.first()))
            for (locale in group) assertSame(first, DayPeriodRules.getInstance(ULocale(locale)))
        }
        assertEquals(outcome { Source.getInstance(null) }, outcome { DayPeriodRules.getInstance(null) })
    }

    @Test fun hourAndSetNumberValidation() {
        val sourceSink = Class.forName("com.ibm.icu.impl.DayPeriodRules\$DayPeriodRulesDataSink")
        val sourceHour = sourceSink.getDeclaredMethod("parseHour", String::class.java).apply { isAccessible = true }
        val portCompanionClass = Class.forName("io.github.heapy.icu4k.impl.DayPeriodRules\$DayPeriodRulesDataSink\$Companion")
        val portSink = Class.forName("io.github.heapy.icu4k.impl.DayPeriodRules\$DayPeriodRulesDataSink")
        val companion = portSink.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
        val portHour = portCompanionClass.getDeclaredMethod("parseHour", String::class.java).apply { isAccessible = true }
        for (hour in listOf("", "0", "0:00", "00:00", "24:00", "25:00", "-1:00", "+1:00", "01:00", "001:00", "1:30", "1:000", "12:00:00", "a:00", "٢:00", "２:00", " 1:00")) {
            assertEquals(outcome { sourceHour.invoke(null, hour) }, outcome { portHour.invoke(companion, hour) }, hour)
        }
        val sourceSet = Source::class.java.getDeclaredMethod("parseSetNum", String::class.java).apply { isAccessible = true }
        val portSet = DayPeriodRules.Companion::class.java.getDeclaredMethod("parseSetNum", String::class.java).apply { isAccessible = true }
        for (set in listOf("set0", "set123", "set-1", "set+1", "set", "Set1", "set2147483648", "set-2147483648", "set٢", "set２", "set 1")) {
            assertEquals(outcome { sourceSet.invoke(null, set) }, outcome { portSet.invoke(DayPeriodRules.Companion, set) }, set)
        }
    }

    private fun synthetic(prefix: String, period: String, cutoffs: IntArray): Pair<Any, () -> Any?> {
        val owner = Class.forName(prefix + ".DayPeriodRules")
        val dataType = Class.forName(owner.name + "\$DayPeriodRulesData")
        val data = dataType.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val rule = owner.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val array = java.lang.reflect.Array.newInstance(owner, 1)
        java.lang.reflect.Array.set(array, 0, rule)
        dataType.getDeclaredField("rules").apply { isAccessible = true }.set(data, array)
        val sinkType = Class.forName(owner.name + "\$DayPeriodRulesDataSink")
        val sink = sinkType.getDeclaredConstructor(dataType).apply { isAccessible = true }.newInstance(data)
        val periodType = Class.forName(owner.name + "\$DayPeriod")
        val enumValue = periodType.enumConstants.first { (it as Enum<*>).name == period }
        sinkType.getDeclaredField("period").apply { isAccessible = true }.set(sink, enumValue)
        sinkType.getDeclaredField("cutoffs").apply { isAccessible = true }.set(sink, cutoffs.copyOf())
        val method = sinkType.getDeclaredMethod("setDayPeriodForHoursFromCutoffs").apply { isAccessible = true }
        return rule to { method.invoke(sink); "ok" }
    }
    @Test fun cutoffFlagsMidnightNoonWraparoundAndInvalidPairs() {
        val cases = listOf(
            "MIDNIGHT" to mapOf(0 to 8), "NOON" to mapOf(12 to 8),
            "AM" to mapOf(12 to 8), "MIDNIGHT" to mapOf(24 to 8),
            "MORNING1" to mapOf(6 to 4, 12 to 1), "MORNING1" to mapOf(6 to 2, 12 to 1),
            "NIGHT1" to mapOf(20 to 4, 6 to 1), "NIGHT1" to mapOf(24 to 4, 6 to 1),
            "PM" to mapOf(6 to 4, 24 to 1), "AM" to mapOf(1 to 4),
            "PM" to mapOf(24 to 4), "AM" to emptyMap()
        )
        for ((period, values) in cases) {
            val cutoffs = IntArray(25).also { a -> values.forEach { (hour, flags) -> a[hour] = flags } }
            val (a, runA) = synthetic("com.ibm.icu.impl", period, cutoffs)
            val (b, runB) = synthetic("io.github.heapy.icu4k.impl", period, cutoffs)
            assertEquals(outcome(runA), outcome(runB), "$period/$values")
            compare(a as Source, b as DayPeriodRules, "$period/$values")
        }
    }
}
