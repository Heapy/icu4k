@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.util.UnitComplexity
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.units.MeasureUnitImpl as Original
import com.ibm.icu.impl.units.SingleUnitImpl as OriginalSingle

class MeasureUnitImplOracleTest {
    private fun state(j: Original): List<Any?> = listOf(j.identifier, j.complexity?.name, j.constantDenominator, j.singleUnits.map { listOf(it.index, it.simpleUnitID, it.dimensionality, it.prefix?.name) })
    private fun state(k: MeasureUnitImpl): List<Any?> = listOf(k.getIdentifier(), k.getComplexity()?.name, k.getConstantDenominator(), k.getSingleUnits().map { listOf(it.getIndex(), it.getSimpleUnitID(), it.getDimensionality(), it.getPrefix()?.name) })
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) { listOf(e.javaClass.simpleName, if (e is NumberFormatException) null else e.message) }
    private fun compare(id: String?) {
        val j = outcome { Original.forIdentifier(id)?.also { it.serialize() }?.let { state(it) } }
        val k = outcome { MeasureUnitImpl.forIdentifier(id)?.also { it.serialize() }?.let { state(it) } }
        assertEquals(j, k, id)
    }
    @Test fun simplePrefixedPowerCompoundMixedAndMalformedIdentifiersMatch() {
        val units = com.ibm.icu.impl.units.UnitsData.getSimpleUnits()
        for (unit in units) { compare(unit); compare("per-$unit"); compare("square-$unit") }
        for (alias in com.ibm.icu.impl.units.UnitsData.getAliases()) { compare(alias.alias); compare("square-${alias.alias}"); compare("meter-per-${alias.alias}") }
        for (prefix in com.ibm.icu.util.MeasureUnit.MeasurePrefix.values()) for (base in listOf("meter", "gram", "byte")) compare(prefix.identifier + base)
        val random = Random(779)
        repeat(500) {
            val a = units.random(random); val b = units.random(random); val c = units.random(random)
            compare("$a-$b"); compare("$a-per-$b"); compare("$a-and-$b-and-$c"); compare("square-$a-per-cubic-$b")
        }
        for (id in listOf(null, "", "per-", "meter-", "meter-and-meter", "meter-and-second-per-hour", "meter-per-second-and-hour", "pow16-meter", "kilokilometer", "meterpersecond", "meter-per-0", "per-0", "per-1000-meter", "meter-per-1e3", "meter-per-1.0", "meter-per-0-per-2", "meter-per-2-per-3", "meter-per-9223372036854775808", "meter-per-1e-1")) compare(id)
    }
    @Test fun mutableCopiesSimplificationAndSerializationMatch() {
        for (id in listOf("meter", "meter-per-second", "square-meter-per-meter", "foot-and-inch", "meter-per-1000-kilometer")) {
            val j = Original.forIdentifier(id); val k = MeasureUnitImpl.forIdentifier(id)!!
            assertEquals(state(j), state(k)); j.serialize(); k.serialize(); assertEquals(state(j), state(k))
            val jc = j.copy(); val kc = k.copy(); jc.applyDimensionality(2); kc.applyDimensionality(2)
            assertEquals(state(jc), state(kc)); jc.serialize(); kc.serialize(); assertEquals(state(jc), state(kc))
            jc.takeReciprocal(); kc.takeReciprocal(); assertEquals(state(jc), state(kc))
            jc.serialize(); kc.serialize(); assertEquals(state(jc), state(kc))
            assertEquals(state(j.copyAndSimplify()), state(k.copyAndSimplify()))
            j.appendSingleUnit(null); k.appendSingleUnit(null); assertEquals(state(j), state(k))
        }
        val j = OriginalSingle(); val k = SingleUnitImpl()
        val units = com.ibm.icu.impl.units.UnitsData.getSimpleUnits(); val i = units.indexOf("meter")
        j.setSimpleUnit(i, units); k.setSimpleUnit(i, units)
        for (dim in listOf(-15, -3, -1, 1, 2, 3, 15, 16)) {
            j.dimensionality = dim; k.setDimensionality(dim)
            assertEquals(outcome { j.neutralIdentifier }, outcome { k.getNeutralIdentifier() })
        }
    }
    @Test fun decimalConstantPortableValidationMatchesBigDecimal() {
        val random = Random(11)
        val values = listOf("", "+", "-", ".", "1.0", "-0", "+10", "1.000e3", "١٢e٣", "9223372036854775807", "9223372036854775808", "0e2147483647", "1e2147483647", "0e-2147483648", "1e2147483648", "0e000000000000000000000000000", "2E+18") + List(2000) {
            val coefficient = random.nextLong().toString()
            coefficient + (if (random.nextBoolean()) ".${random.nextInt(100)}" else "") + (if (random.nextBoolean()) "e${random.nextInt(-25, 25)}" else "")
        }
        val boundaries = (2147483646L..2147483652L).flatMap { exponent -> (0..5).flatMap { fraction -> listOf("0", "1").map { coefficient -> coefficient + (if (fraction > 0) "." + "0".repeat(fraction) else "") + "e" + exponent } } }
        for (value in values + boundaries) assertEquals(outcome { parseUnitConstant(value) }, outcome { parseUnitConstantPortable(value) }, value)
    }
    @Test fun virtualListAndComplexityCallbacksMatch() {
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        val ju = Original.forIdentifier("meter-second").singleUnits; val ku = MeasureUnitImpl.forIdentifier("meter-second")!!.getSingleUnits()
        val j = object : Original() {
            override fun getSingleUnits(): java.util.ArrayList<OriginalSingle> { jt.add("units"); return ju }
            override fun getComplexity(): com.ibm.icu.util.MeasureUnit.Complexity { jt.add("complexity"); return com.ibm.icu.util.MeasureUnit.Complexity.MIXED }
        }
        val k = object : MeasureUnitImpl() {
            override fun getSingleUnits(): ArrayList<SingleUnitImpl> { kt.add("units"); return ku }
            override fun getComplexity(): UnitComplexity { kt.add("complexity"); return UnitComplexity.MIXED }
        }
        j.serialize(); k.serialize(); assertEquals(j.identifier, k.getIdentifier()); assertEquals(jt, kt)
    }
}
