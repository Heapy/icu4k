@file:Suppress("DEPRECATION")
package com.ibm.icu.impl.units

import com.ibm.icu.impl.units.ComplexUnitsConverter as JComplex
import com.ibm.icu.impl.units.UnitsRouter as JRouter
import com.ibm.icu.impl.units.ConversionRates as JRates
import com.ibm.icu.impl.units.MeasureUnitImpl as JUnit
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.impl.number.MicroProps as JMicro
import com.ibm.icu.impl.number.MicroPropsGenerator as JGenerator
import com.ibm.icu.impl.number.UnitConversionHandler as JHandler
import com.ibm.icu.impl.number.UsagePrefsHandler as JUsage
import com.ibm.icu.util.MeasureUnit as JMeasureUnit
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.units.ComplexUnitsConverter
import io.github.heapy.icu4k.impl.units.UnitsRouter
import io.github.heapy.icu4k.impl.units.ConversionRates
import io.github.heapy.icu4k.impl.units.MeasureUnitImpl
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.ULocale
import java.math.BigDecimal
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class ComplexUnitsBoundaryOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        var cause = e
        while (cause is InvocationTargetException) cause = cause.targetException
        when (cause) {
            is NullPointerException -> "null"
            is ClassCastException -> "cast"
            is IndexOutOfBoundsException -> "bounds"
            is AssertionError -> "assert:${cause.message}"
            else -> cause.javaClass.simpleName + ":" + cause.message
        }
    }
    private fun snapshot(result: JComplex.ComplexConverterResult): Any = listOf(result.indexOfQuantity,
        result.measures.map { listOf(it?.number?.toString(), it?.number?.javaClass?.simpleName, it?.unit?.identifier) })
    private fun snapshot(result: ComplexUnitsConverter.ComplexConverterResult): Any = listOf(result.indexOfQuantity,
        result.measures?.map { listOf(it?.getNumber()?.toString(), it?.getNumber()?.javaClass?.simpleName, it?.getUnit()?.getIdentifier()) })
    private fun rounders(mode: Int): Pair<JPrecision?, Precision?> = when (mode) {
        0 -> null to null
        1 -> JPrecision.unlimited() to Precision.unlimited()
        2 -> JPrecision.integer() to Precision.integer()
        3 -> JPrecision.fixedFraction(2) to Precision.fixedFraction(2)
        4 -> JPrecision.increment(BigDecimal("0.5")) to Precision.increment(BigDecimal("0.5"))
        else -> JPrecision.BogusRounder() to Precision.BogusRounder()
    }
    @Test fun mixedUnitOrderCarryNegativeAndLargeIntegers() {
        assertEquals(JComplex.EPSILON, ComplexUnitsConverter.EPSILON)
        assertEquals(JComplex.EPSILON_MULTIPLIER, ComplexUnitsConverter.EPSILON_MULTIPLIER)
        for ((input, output) in listOf("meter" to "foot-and-inch", "foot" to "inch-and-foot", "yard" to "yard-and-foot-and-inch",
            "hour" to "minute-and-second", "celsius" to "fahrenheit", "meter-per-second" to "beaufort", "meter" to "kilometer-and-meter")) {
            val j = JComplex(input, output); val k = ComplexUnitsConverter(input, output)
            assertEquals(j.units_.map { it.index to it.unitImpl.identifier }, k.units_!!.map { it!!.index to it.unitImpl.getIdentifier() })
            for (text in listOf("0", "-0.0", "1", "1.9999999999999999", "-2.99999999", "12.3456789", "1000", "123456789012345678901234.5678")) {
                for (mode in 0..4) {
                    val (jr, kr) = rounders(mode)
                    assertEquals(outcome { snapshot(j.convert(BigDecimal(text), jr)) }, outcome { snapshot(k.convert(BigDecimal(text), kr)) }, "$input/$output/$text/$mode")
                }
                for (limit in listOf("0", "1", "2.999999999999999", "1000"))
                    assertEquals(j.greaterThanOrEqual(BigDecimal(text), BigDecimal(limit)), k.greaterThanOrEqual(BigDecimal(text), BigDecimal(limit)))
            }
        }
        for (target in listOf("inch-and-foot", "yard-and-foot-and-inch", "meter")) {
            val j = JComplex(JUnit.forIdentifier(target), JRates())
            val k = ComplexUnitsConverter(MeasureUnitImpl.forIdentifier(target), ConversionRates())
            assertEquals(snapshot(j.convert(BigDecimal("2.5"), null)), snapshot(k.convert(BigDecimal("2.5"), null)))
        }
    }
    @Test fun preferenceRoutingPrecisionAndLiveOutputList() {
        for ((unit, usage) in listOf("meter" to "default", "meter" to "person-height", "meter-per-second" to "wind", "celsius" to "default", "liter" to "vehicle-fuel", "kilogram" to "person")) {
            for (locale in listOf("en_US", "en_GB", "fr_FR", "en_001", "en_US@mu=celsius", "en_US@measure=metric")) {
                val j = JRouter(unit, JLocale(locale), usage); val k = UnitsRouter(unit, ULocale(locale), usage)
                assertEquals(j.outputUnits.map { it.identifier }, k.getOutputUnits().map { it.getIdentifier() })
                assertSame(k.getOutputUnits(), k.getOutputUnits())
                for (text in listOf("0", "0.0001", "1.75", "-12.345", "1500", "1000000")) for (mode in listOf(0, 2, 5)) {
                    val (jr, kr) = rounders(mode)
                    val jm = JMicro(false).apply { rounder = jr }; val km = MicroProps(false).apply { rounder = kr }
                    val ja = j.route(BigDecimal(text), jm); val ka = k.route(BigDecimal(text), km)
                    assertEquals(ja.outputUnit.build().identifier, ka.outputUnit!!.build().getIdentifier())
                    assertEquals(snapshot(ja.complexConverterResult), snapshot(ka.complexConverterResult), "$unit/$locale/$usage/$text/$mode")
                    assertEquals(jm.rounder?.javaClass?.simpleName, km.rounder?.javaClass?.simpleName)
                }
                j.outputUnits.clear(); k.getOutputUnits().clear()
                assertEquals(snapshot(j.route(BigDecimal("12"), null).complexConverterResult), snapshot(k.route(BigDecimal("12"), null).complexConverterResult))
            }
        }
    }
    @Test fun handlersExposeFractionalQuantityAndParentOrder() {
        for (preferences in listOf(false, true)) for (mode in listOf(0, 2, 5)) {
            var jm = JMicro(false); var km = MicroProps(false)
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            val jp = JGenerator { jt.add("parent"); jm }; val kp = MicroPropsGenerator { kt.add("parent"); km }
            val j: JGenerator = if (preferences) JUsage(JLocale.US, JMeasureUnit.METER, "person-height", jp) else JHandler(JMeasureUnit.forIdentifier("inch-and-foot"), jp)
            val k: MicroPropsGenerator = if (preferences) UsagePrefsHandler(ULocale("en_US"), MeasureUnit.METER, "person-height", kp) else UnitConversionHandler(MeasureUnit.forIdentifier("inch-and-foot"), kp)
            for (number in listOf(0.0, -0.0, 1.75, -2.99999999, 12.9999, Double.NaN, Double.POSITIVE_INFINITY)) {
                val (jr, kr) = rounders(mode)
                jm = JMicro(false).apply { rounder = jr }; km = MicroProps(false).apply { rounder = kr }
                val jq = JBCD(number); val kq = DecimalQuantity_DualStorageBCD(number)
                assertEquals(outcome { j.processQuantity(jq); "ok" }, outcome { k.processQuantity(kq); "ok" })
                assertEquals(outcome { jq.toPlainString() }, outcome { kq.toPlainString() })
                assertEquals(jm.indexOfQuantity, km.indexOfQuantity)
                assertEquals(jm.outputUnit?.identifier, km.outputUnit?.getIdentifier())
                assertEquals(jm.mixedMeasures?.map { it?.toString() }, km.mixedMeasures?.map { it?.toString() })
                assertEquals(jt, kt)
            }
        }
    }
    @Test fun helperRetainsAssignmentsBeforeCastOrIndexFailures() {
        val jm = JUsage::class.java.getDeclaredMethod("mixedMeasuresToMicros", JComplex.ComplexConverterResult::class.java,
            com.ibm.icu.impl.number.DecimalQuantity::class.java, JMicro::class.java).apply { isAccessible = true }
        val km = UsagePrefsHandler::class.java.getDeclaredMethod("mixedMeasuresToMicros", ComplexUnitsConverter.ComplexConverterResult::class.java,
            DecimalQuantity::class.java, MicroProps::class.java).apply { isAccessible = true }
        for (index in listOf(-1, 0, 1, 2)) for (integer in listOf(false, true)) {
            val jlist = arrayListOf<com.ibm.icu.util.Measure?>(null, com.ibm.icu.util.Measure(if (integer) java.math.BigInteger.ONE else BigDecimal.ONE, JMeasureUnit.METER))
            val klist = arrayListOf<io.github.heapy.icu4k.util.Measure?>(null, io.github.heapy.icu4k.util.Measure(if (integer) java.math.BigInteger.ONE else BigDecimal.ONE, MeasureUnit.METER))
            val j = JMicro(false); val k = MicroProps(false)
            val jr = JComplex.ComplexConverterResult(index, jlist); val kr = ComplexUnitsConverter.ComplexConverterResult(index, klist)
            assertEquals(outcome { jm.invoke(null, jr, JBCD(1), j) }, outcome { km.invoke(null, kr, DecimalQuantity_DualStorageBCD(1), k) })
            assertSame(jlist, j.mixedMeasures); assertSame(klist, k.mixedMeasures)
            assertEquals(j.indexOfQuantity, k.indexOfQuantity)
        }
    }
    @Test fun mutableConverterListsAndRounderCallbacksRemainLive() {
        val j = JComplex("foot", "foot-and-inch"); val k = ComplexUnitsConverter("foot", "foot-and-inch")
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        j.unitsConverters_[1] = object : com.ibm.icu.impl.units.UnitsConverter("foot", "inch") {
            override fun convert(value: BigDecimal?): BigDecimal? { jt.add("convert:$value"); return super.convert(value) }
            override fun convertInverse(value: BigDecimal?): BigDecimal? { jt.add("inverse:$value"); return super.convertInverse(value) }
        }
        k.unitsConverters_!![1] = object : io.github.heapy.icu4k.impl.units.UnitsConverter("foot", "inch") {
            override fun convert(inputValue: BigDecimal?): BigDecimal? { kt.add("convert:$inputValue"); return super.convert(inputValue) }
            override fun convertInverse(inputValue: BigDecimal?): BigDecimal? { kt.add("inverse:$inputValue"); return super.convertInverse(inputValue) }
        }
        val jr = object : JPrecision.BogusRounder() {
            override fun apply(value: com.ibm.icu.impl.number.DecimalQuantity?) {
                jt.add("round:${value!!.toBigDecimal()}"); value.roundToMagnitude(0, java.math.MathContext.DECIMAL128)
                j.units_.reverse()
            }
        }
        val kr = object : Precision.BogusRounder() {
            override fun apply(value: DecimalQuantity?) {
                kt.add("round:${value!!.toBigDecimal()}"); value.roundToMagnitude(0, java.math.MathContext.DECIMAL128)
                k.units_!!.reverse()
            }
        }
        assertEquals(snapshot(j.convert(BigDecimal("2.9999"), jr)), snapshot(k.convert(BigDecimal("2.9999"), kr)))
        assertEquals(jt, kt)
        j.unitsConverters_.clear(); k.unitsConverters_!!.clear()
        assertEquals(outcome { j.convert(BigDecimal.ONE, null) }, outcome { k.convert(BigDecimal.ONE, null) })
    }

    @Test fun nullParentResultStillRoundsQuantityBeforeFailing() {
        for (preferences in listOf(false, true)) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            val jparent = JGenerator { jt.add("parent"); null }
            val kparent = MicroPropsGenerator { kt.add("parent"); null }
            val j: JGenerator = if (preferences) JUsage(JLocale.US, JMeasureUnit.METER, "person-height", jparent) else JHandler(JMeasureUnit.forIdentifier("foot-and-inch"), jparent)
            val k: MicroPropsGenerator = if (preferences) UsagePrefsHandler(ULocale("en_US"), MeasureUnit.METER, "person-height", kparent) else UnitConversionHandler(MeasureUnit.forIdentifier("foot-and-inch"), kparent)
            val jb = JBCD(1.75); val kb = DecimalQuantity_DualStorageBCD(1.75)
            val jq = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(com.ibm.icu.impl.number.DecimalQuantity::class.java)) { _, method, args ->
                jt.add(method.name); method.invoke(jb, *(args ?: emptyArray()))
            } as com.ibm.icu.impl.number.DecimalQuantity
            val kq = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DecimalQuantity::class.java)) { _, method, args ->
                kt.add(method.name); method.invoke(kb, *(args ?: emptyArray()))
            } as DecimalQuantity
            assertEquals(outcome { j.processQuantity(jq) }, outcome { k.processQuantity(kq) })
            assertEquals(jt, kt)
            assertEquals(jb.toPlainString(), kb.toPlainString())
        }
    }

    @Test fun singletonCustomListStillReceivesSortCallback() {
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        val j = object : JUnit() {
            override fun extractIndividualUnitsWithIndices(): ArrayList<JUnit.MeasureUnitImplWithIndex> =
                object : ArrayList<JUnit.MeasureUnitImplWithIndex>(JUnit.forIdentifier("meter").extractIndividualUnitsWithIndices()) {
                    override fun sort(c: Comparator<in JUnit.MeasureUnitImplWithIndex>?) { jt.add("sort"); super.sort(c) }
                }
        }
        val k = object : MeasureUnitImpl() {
            override fun extractIndividualUnitsWithIndices(): ArrayList<MeasureUnitImpl.MeasureUnitImplWithIndex> =
                object : ArrayList<MeasureUnitImpl.MeasureUnitImplWithIndex>(MeasureUnitImpl.forIdentifier("meter")!!.extractIndividualUnitsWithIndices()) {
                    override fun sort(c: Comparator<in MeasureUnitImpl.MeasureUnitImplWithIndex>?) { kt.add("sort"); super.sort(c) }
                }
        }
        val jc = JComplex(JUnit.forIdentifier("meter"), j, JRates())
        val kc = ComplexUnitsConverter(MeasureUnitImpl.forIdentifier("meter"), k, ConversionRates())
        assertEquals(listOf("sort"), jt); assertEquals(jt, kt)
        assertEquals(snapshot(jc.convert(BigDecimal.ONE, null)), snapshot(kc.convert(BigDecimal.ONE, null)))
    }

}
