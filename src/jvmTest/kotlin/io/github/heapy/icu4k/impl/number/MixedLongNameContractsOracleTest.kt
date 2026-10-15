@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.MixedUnitLongNameHandler as JMixed
import com.ibm.icu.impl.number.LongNameMultiplexer as JMultiplexer
import com.ibm.icu.impl.number.MicroProps as JMicro
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.impl.FormattedStringBuilder as JBuffer
import com.ibm.icu.number.NumberFormatter.UnitWidth as JWidth
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.MeasureUnit as JUnit
import com.ibm.icu.util.Measure as JMeasure
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.*
import java.math.BigInteger
import kotlin.test.*

class MixedLongNameContractsOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            else -> "${e.javaClass.simpleName}:${e.message}"
        }
    }
    private fun compare(j: com.ibm.icu.impl.number.Modifier?, k: Modifier?, label: String) {
        assertEquals(j == null, k == null, label)
        if (j == null || k == null) return
        val jb = JBuffer(); val kb = FormattedStringBuilder(); jb.append("2.6", null); kb.append("2.6", null)
        assertEquals(j.apply(jb, 0, 3), k.apply(kb, 0, 3), label)
        assertEquals(jb.toString(), kb.toString(), label)
        assertEquals(jb.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, kb.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, label)
        assertEquals(j.parameters?.signum?.name, k.getParameters()?.signum?.name, label)
        assertEquals(j.parameters?.plural?.name, k.getParameters()?.plural?.name, label)
    }
    @Test fun mixedComponentsQuantityIndicesAndFailures() {
        for (locale in listOf("en", "de", "fr", "ar@numbers=arab")) for (width in UnitWidth.entries) {
            for (index in listOf(-1, 0, 1, 2)) for (negative in listOf(false, true)) {
                val jm = JMicro(true); jm.rounder = JPrecision.fixedFraction(1); jm.gender = "keep"
                val km = MicroProps(true); km.rounder = Precision.fixedFraction(1); km.gender = "keep"
                jm.indexOfQuantity = index; km.indexOfQuantity = index
                val first = BigInteger(if (negative) "-18446744073709551616" else "18446744073709551616")
                jm.mixedMeasures = listOf(JMeasure(first, JUnit.FOOT), JMeasure(java.math.BigDecimal("2.6"), JUnit.INCH))
                km.mixedMeasures = listOf(Measure(first, MeasureUnit.FOOT), Measure(BigDecimal("2.6"), MeasureUnit.INCH))
                val j = JMixed.forMeasureUnit(JLocale(locale), JUnit.forIdentifier("foot-and-inch"), JWidth.valueOf(width.name), "", JRules.forLocale(JLocale(locale)), jm)
                val k = MixedUnitLongNameHandler.forMeasureUnit(ULocale(locale), MeasureUnit.forIdentifier("foot-and-inch"), width, "", PluralRules.forLocale(ULocale(locale)), km)
                for (parentless in listOf(false, true)) {
                    val jq = JBCD(java.math.BigDecimal(if (negative) "-2.6" else "2.6")); val kq = DecimalQuantity_DualStorageBCD(BigDecimal(if (negative) "-2.6" else "2.6"))
                    var a: JMicro? = null; var b: MicroProps? = null
                    val label = "$locale/$width/$index/$negative/$parentless"
                    assertEquals(outcome { a = if (parentless) j.processQuantityWithMicros(jq, jm) else j.processQuantity(jq); "ok" },
                        outcome { b = if (parentless) k.processQuantityWithMicros(kq, km) else k.processQuantity(kq); "ok" }, label)
                    if (a != null && b != null) { compare(a!!.modOuter, b!!.modOuter, label); assertEquals(a!!.gender, b!!.gender) }
                    assertEquals(jq.toPlainString(), kq.toPlainString(), label)
                }
                assertEquals(outcome { j.getModifier(null, null) }, outcome { k.getModifier(null, null) })
            }
        }
        val j = JMixed.forMeasureUnit(JLocale.ENGLISH, JUnit.forIdentifier("foot-and-inch"), JWidth.FULL_NAME, "", JRules.forLocale(JLocale.ENGLISH), null)
        val k = MixedUnitLongNameHandler.forMeasureUnit(ULocale.ENGLISH, MeasureUnit.forIdentifier("foot-and-inch"), UnitWidth.FULL_NAME, "", PluralRules.forLocale(ULocale.ENGLISH), null)
        val jm = JMicro(true); jm.mixedMeasures = emptyList()
        val km = MicroProps(true); km.mixedMeasures = emptyList()
        assertEquals(outcome { j.processQuantityWithMicros(JBCD(1), jm) }, outcome { k.processQuantityWithMicros(DecimalQuantity_DualStorageBCD(1), km) })
        assertEquals(outcome { j.processQuantityWithMicros(null, null) }, outcome { k.processQuantityWithMicros(null, null) })
        assertEquals(outcome { j.processQuantity(null) }, outcome { k.processQuantity(null) })
    }
    @Test fun multiplexerCopiesListSelectsUnitsAndPreservesGender() {
        val jm = JMicro(true); jm.rounder = JPrecision.unlimited(); jm.gender = "keep"
        val km = MicroProps(true); km.rounder = Precision.unlimited(); km.gender = "keep"
        val ju = mutableListOf(JUnit.METER, JUnit.FOOT)
        val ku = mutableListOf(MeasureUnit.METER, MeasureUnit.FOOT)
        val j = JMultiplexer.forMeasureUnits(JLocale.ENGLISH, ju, JWidth.FULL_NAME, "", JRules.forLocale(JLocale.ENGLISH), jm)
        val k = LongNameMultiplexer.forMeasureUnits(ULocale.ENGLISH, ku, UnitWidth.FULL_NAME, "", PluralRules.forLocale(ULocale.ENGLISH), km)
        ju.clear(); ku.clear()
        for (id in listOf("meter", "foot", "second")) {
            jm.outputUnit = JUnit.forIdentifier(id); km.outputUnit = MeasureUnit.forIdentifier(id)
            var a: JMicro? = null; var b: MicroProps? = null
            assertEquals(outcome { a = j.processQuantity(JBCD(2)); "ok" }, outcome { b = k.processQuantity(DecimalQuantity_DualStorageBCD(2)); "ok" }, id)
            if (a != null && b != null) { compare(a!!.modOuter, b!!.modOuter, id); assertEquals(a!!.gender, b!!.gender); assertEquals("keep", b!!.gender) }
        }
        assertEquals(outcome { JMultiplexer(jm).processQuantity(JBCD(1)) }, outcome { LongNameMultiplexer(km).processQuantity(DecimalQuantity_DualStorageBCD(1)) })
        assertEquals(outcome { JMultiplexer.forMeasureUnits(JLocale.ENGLISH, emptyList(), JWidth.SHORT, "", null, null) },
            outcome { LongNameMultiplexer.forMeasureUnits(ULocale.ENGLISH, emptyList(), UnitWidth.SHORT, "", null, null) })
    }
}
