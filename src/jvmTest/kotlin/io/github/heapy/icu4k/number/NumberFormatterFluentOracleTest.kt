@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.number.NumberFormatter as JFormatter
import com.ibm.icu.number.NumberFormatterSettings as JSettings
import com.ibm.icu.number.LocalizedNumberFormatter as JLocalized
import com.ibm.icu.number.UnlocalizedNumberFormatter as JUnlocalized
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.impl.number.MacroProps as JMacros
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.DisplayOptions as JOptions
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.Measure as JMeasure
import com.ibm.icu.util.MeasureUnit as JUnit
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class NumberFormatterFluentOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        val error = if (e is InvocationTargetException) e.cause!! else e
        when (error) { is NullPointerException -> "null"; is AssertionError -> "assert:${error.message}"; else -> error.javaClass.simpleName + ":" + error.message }
    }
    private fun resolve(j: JSettings<*>): JMacros = JSettings::class.java.getDeclaredMethod("resolve").also { it.isAccessible = true }.invoke(j) as JMacros
    private fun compare(j: JLocalized, k: LocalizedNumberFormatter, label: String) {
        for (value in listOf(-12345.6789, -1.0, -0.0, 0.0, 1.5, 2.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(outcome { j.format(value).toString() }, outcome { k.format(value).toString() }, "$label/$value")
        }
        for (prefix in listOf(false, true)) for (negative in listOf(false, true)) assertEquals(outcome { j.getAffixImpl(prefix, negative) }, outcome { k.getAffixImpl(prefix, negative) }, "$label/affix")
        assertEquals(outcome { j.toSkeleton() }, outcome { k.toSkeleton() }, "$label/skeleton")
    }
    @Test fun fluentModesThresholdsSkeletonsAndOverrideUnits() {
        for (skeleton in listOf("", ".00 group-off sign-always", "scientific/*ee/sign-always", "compact-short", "currency/USD unit-width-full-name", "unit/meter", "unit/foot-and-inch unit-width-full-name", "percent scale/100", "unit/meter usage/person-height")) {
            for (locale in listOf("en_US", "fr", "de", "ar@numbers=arab")) for (threshold in listOf(0L, 1L, 3L, -1L)) {
                val j = JFormatter.forSkeleton(skeleton).threshold(threshold).locale(JLocale(locale))
                val k = NumberFormatter.forSkeleton(skeleton).threshold(threshold).locale(ULocale(locale))
                compare(j, k, "$skeleton/$locale/$threshold")
                for (id in listOf("meter", "foot", "foot", "meter", "second")) {
                    assertEquals(outcome { j.format(JMeasure(java.math.BigDecimal("1.25"), JUnit.forIdentifier(id))).toString() },
                        outcome { k.format(Measure(BigDecimal("1.25"), MeasureUnit.forIdentifier(id))).toString() }, "$skeleton/$id measure")
                }
                assertEquals(j.withoutLocale().toSkeleton(), k.withoutLocale().toSkeleton())
            }
        }
        val j = JFormatter.withLocale(java.util.Locale.FRANCE); val k = NumberFormatter.withLocale(java.util.Locale.FRANCE)
        compare(j, k, "Java Locale")
    }
    @Test fun resolutionNullDefaultsMacrosAndEquality() {
        val jm = JMacros().also { it.unit = JUnit.METER; it.threshold = 1L }
        val km = MacroProps().also { it.unit = MeasureUnit.METER; it.threshold = 1L }
        val j = JFormatter.with().unit(JUnit.FOOT).macros(jm).unit(null)
        val k = NumberFormatter.with().unit(MeasureUnit.FOOT).macros(km).unit(null)
        assertEquals(j.toSkeleton(), k.toSkeleton())
        assertEquals(resolve(j).threshold, k.resolve().threshold)
        assertEquals(resolve(j).unit.identifier, k.resolve().unit!!.getIdentifier())
        jm.unit = JUnit.SECOND; km.unit = MeasureUnit.SECOND
        assertEquals(j.toSkeleton(), k.toSkeleton())
        val js = JSymbols(JLocale.ENGLISH); val ks = DecimalFormatSymbols(ULocale.ENGLISH)
        val jf = JFormatter.with().symbols(js); val kf = NumberFormatter.with().symbols(ks)
        assertNotSame(js, resolve(jf).symbols); assertNotSame(ks, kf.resolve().symbols)
        js.decimalSeparatorString = "!"; ks.setDecimalSeparatorString("!")
        compare(jf.locale(JLocale.ENGLISH), kf.locale(ULocale.ENGLISH), "symbol snapshot")
        val jOptions = JOptions.builder().build(); val kOptions = DisplayOptions.builder().build()
        assertEquals(JFormatter.with().unitDisplayCase("dative").displayOptions(jOptions).toSkeleton(), NumberFormatter.with().unitDisplayCase("dative").displayOptions(kOptions).toSkeleton())
        assertEquals(outcome { JFormatter.with().displayOptions(JOptions.builder().setGrammaticalCase(null).build()) }, outcome { NumberFormatter.with().displayOptions(DisplayOptions.builder().setGrammaticalCase(null).build()) })
        val ja = listOf(JFormatter.with(), JFormatter.with().threshold(0), JFormatter.with().grouping(JFormatter.GroupingStrategy.AUTO), JFormatter.with().usage(""), JFormatter.with().usage("default"), JFormatter.with().locale(JLocale.ENGLISH), JFormatter.with().locale(JLocale.ENGLISH).withoutLocale())
        val ka = listOf(NumberFormatter.with(), NumberFormatter.with().threshold(0), NumberFormatter.with().grouping(GroupingStrategy.AUTO), NumberFormatter.with().usage(""), NumberFormatter.with().usage("default"), NumberFormatter.with().locale(ULocale.ENGLISH), NumberFormatter.with().locale(ULocale.ENGLISH).withoutLocale())
        for (a in ja.indices) for (b in ja.indices) assertEquals(ja[a] == ja[b], ka[a] == ka[b], "$a/$b equality")
        assertEquals(ka[0].hashCode(), ka[1].hashCode())
        assertSame(NumberFormatter.with(), NumberFormatter.with())
        assertSame(NumberFormatter.forSkeleton("group-off"), NumberFormatter.forSkeleton("group-off"))
        assertNotSame(NumberFormatter.forSkeleton("group-off"), NumberFormatter.forSkeleton(" group-off "))
        assertEquals(outcome { JFormatter.forSkeleton(null) }, outcome { NumberFormatter.forSkeleton(null) })
        assertEquals(outcome { JFormatter.with().symbols(null as JSymbols?) }, outcome { NumberFormatter.with().symbols(null as DecimalFormatSymbols?) })
        assertEquals(outcome { JFormatter.with().displayOptions(null) }, outcome { NumberFormatter.with().displayOptions(null) })
    }
    @Test fun cacheCompilationCountAffixesAndFailureBehavior() {
        fun count(k: LocalizedNumberFormatter): Pair<Long, Boolean> {
            val execution = LocalizedNumberFormatter::class.java.getDeclaredField("execution").also { it.isAccessible = true }.get(k)
            val number = execution.javaClass.getDeclaredField("count").also { it.isAccessible = true }.getLong(execution)
            val compiled = execution.javaClass.getDeclaredField("compiled").also { it.isAccessible = true }.get(execution)
            return number to (compiled != null)
        }
        fun jcount(j: JLocalized): Pair<Long, Boolean> =
            (JLocalized::class.java.getDeclaredField("callCountInternal").also { it.isAccessible = true }.getLong(j)) to
                (JLocalized::class.java.getDeclaredField("compiled").also { it.isAccessible = true }.get(j) != null)
        for (threshold in listOf(null, 0L, 1L, 3L, -1L)) {
            val j = JFormatter.withLocale(JLocale.ENGLISH).threshold(threshold)
            val k = NumberFormatter.withLocale(ULocale.ENGLISH).threshold(threshold)
            for (i in 0..5) {
                if (i % 2 == 0) assertEquals(outcome { j.getAffixImpl(true, false) }, outcome { k.getAffixImpl(true, false) })
                else assertEquals(outcome { j.format(i.toLong()).toString() }, outcome { k.format(i.toLong()).toString() })
                assertEquals(jcount(j), count(k), "$threshold/$i")
            }
        }
        var fail = true
        val js = object : JSymbols(JLocale.ENGLISH) { override fun getCurrencyPattern(): String? { if (fail) throw IllegalStateException("build failure"); return super.getCurrencyPattern() } }
        val ks = object : DecimalFormatSymbols(ULocale.ENGLISH) { override fun getCurrencyPattern(): String? { if (fail) throw IllegalStateException("build failure"); return super.getCurrencyPattern() } }
        val jm = JMacros().also { it.symbols = js; it.unit = JCurrency.getInstance("USD") }
        val km = MacroProps().also { it.symbols = ks; it.unit = Currency.getInstance("USD") }
        val j = JFormatter.withLocale(JLocale.ENGLISH).macros(jm).threshold(1)
        val k = NumberFormatter.withLocale(ULocale.ENGLISH).macros(km).threshold(1)
        assertEquals(outcome { j.format(1L).toString() }, outcome { k.format(1L).toString() })
        fail = false
        assertEquals(j.format(1L).toString(), k.format(1L).toString())
        assertEquals(jcount(j), count(k)); assertFalse(count(k).second)
    }
    @Test fun legacyPropertyFactoryCompletesMapper() {
        for (pattern in listOf("#,##0.00;(#,##0.00)", "0.0E0", "0.00%", "¤0.00")) {
            val jp = com.ibm.icu.impl.number.PatternStringParser.parseToProperties(pattern)
            val kp = PatternStringParser.parseToProperties(pattern)
            val je = com.ibm.icu.impl.number.DecimalFormatProperties(); val ke = DecimalFormatProperties()
            compare(JFormatter.fromDecimalFormat(jp, JSymbols(JLocale.ENGLISH), je).locale(JLocale.ENGLISH),
                NumberFormatter.fromDecimalFormat(kp, DecimalFormatSymbols(ULocale.ENGLISH), ke).locale(ULocale.ENGLISH), pattern)
            assertEquals(je.minimumFractionDigits, ke.getMinimumFractionDigits()); assertEquals(je.maximumFractionDigits, ke.getMaximumFractionDigits())
        }
    }
}
