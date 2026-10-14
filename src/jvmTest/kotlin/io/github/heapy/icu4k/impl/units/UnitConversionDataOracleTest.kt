package io.github.heapy.icu4k.impl.units

import kotlin.test.*
import com.ibm.icu.impl.units.MeasureUnitImpl as JUnit

class UnitConversionDataOracleTest {
    private fun snapshot(unit: SingleUnitImpl): List<Any?> = listOf(unit.getSimpleUnitID(), unit.getDimensionality(), unit.getPrefix()?.name)
    private fun snapshot(unit: com.ibm.icu.impl.units.SingleUnitImpl): List<Any?> = listOf(unit.simpleUnitID, unit.dimensionality, unit.prefix?.name)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { listOf(e.javaClass.simpleName, e.message?.replace("com.ibm.icu", "icu")?.replace("io.github.heapy.icu4k", "icu")) }
    @Test fun allResourceTargetsSystemsAndCategoriesMatchSource() {
        val original = com.ibm.icu.impl.units.ConversionRates()
        val units = com.ibm.icu.impl.units.UnitsData()
        val data = UnitConversionData()
        for (id in data.identifiers()) for (prefix in listOf("", "kilo", "square-", "per-")) {
            val identifier = prefix + id
            val j = try { JUnit.forIdentifier(identifier) } catch (_: IllegalArgumentException) { continue }
            val k = MeasureUnitImpl.forIdentifier(identifier)!!
            assertEquals(original.extractBaseUnits(j).map(::snapshot), data.extractBaseUnits(k).map(::snapshot), identifier)
            val jb = original.extractCompoundBaseUnit(j); val kb = data.extractCompoundBaseUnit(k)
            jb.serialize(); kb.serialize()
            assertEquals(jb.identifier, kb.getIdentifier(), identifier)
            assertEquals(outcome { units.getCategory(j) }, outcome { data.getCategory(k) }, identifier)
            for ((js, ks) in j.singleUnits.zip(k.getSingleUnits())) assertEquals(original.extractSystems(js), data.extractSystems(ks), identifier)
        }
        for (id in listOf("meter-per-second", "newton-meter", "joule-per-meter", "liter-per-100-kilometer", "meter-and-centimeter", "meter-per-meter", "milligram-per-deciliter", "per-meter", "square-second-per-pow4-foot")) {
            assertEquals(outcome { units.getCategory(JUnit.forIdentifier(id)) }, outcome { data.getCategory(MeasureUnitImpl.forIdentifier(id)!!) }, id)
        }
    }
    @Test fun zeroPowerSerializationRetainsSourceAssertion() {
        val j = JUnit.forIdentifier("meter").singleUnitImpl
        val k = MeasureUnitImpl.forIdentifier("meter")!!.getSingleUnitImpl()
        j.dimensionality = 0; k.setDimensionality(0)
        assertEquals(outcome { j.neutralIdentifier }, outcome { k.getNeutralIdentifier() })
    }
    @Test fun resourceRateMetadataMatchesSource() {
        val source = com.ibm.icu.util.UResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_BASE_NAME, "units") as com.ibm.icu.impl.ICUResourceBundle
        val sink = com.ibm.icu.impl.units.ConversionRates.ConversionRatesSink()
        source.getAllItemsWithFallback("convertUnits", sink)
        val data = UnitConversionData()
        assertEquals(sink.mapToConversionRate.keys, data.identifiers())
        for ((id, rate) in sink.mapToConversionRate) {
            val actual = data.rate(id)!!
            assertEquals(rate.target, actual.target, id)
            assertEquals(rate.systems, actual.systems, id)
            assertEquals(rate.specialMappingName, actual.special, id)
            assertEquals(outcome { rate.conversionRate }, outcome { actual.conversionRate() }, id)
            val parts = actual.offsetExpression.split('/')
            val expectedOffset = if (parts.size == 1) java.math.BigDecimal(parts[0]) else java.math.BigDecimal(parts[0]).divide(java.math.BigDecimal(parts[1]), java.math.MathContext.DECIMAL128)
            assertEquals(rate.offset, expectedOffset, id)
        }
    }
}
