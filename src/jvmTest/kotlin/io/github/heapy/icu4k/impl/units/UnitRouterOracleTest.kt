@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.units

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.test.*
import io.github.heapy.icu4k.util.ULocale
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.impl.units.UnitsRouter as JRouter
import com.ibm.icu.impl.number.MicroProps
import com.ibm.icu.number.Precision

class UnitRouterOracleTest {
    @Test fun routingPreferenceThresholdsAndAutomaticRoundingMatchSource() {
        val cases = listOf(Triple("meter", "en_US", "default"), Triple("meter", "fr_FR", "person-height"), Triple("meter", "en_US", "person-height"), Triple("meter-per-second", "en_US", "wind"), Triple("celsius", "en_US", "weather"), Triple("kilogram", "en_GB", "person"), Triple("liter-per-100-kilometer", "en_US", "default"), Triple("meter", "en_US@measure=metric", "default"))
        for ((unit, locale, usage) in cases) for (mode in listOf<RoundingMode?>(null, RoundingMode.HALF_EVEN, RoundingMode.DOWN, RoundingMode.CEILING)) {
            val j = JRouter(unit, JLocale(locale), usage); val k = UnitRouter(unit, ULocale(locale), usage)
            assertEquals(j.outputUnits.map { it.identifier }, k.getOutputUnits().map { it.getIdentifier() })
            for (value in listOf("0", "0.001234", "0.9999999999999999", "1", "1.5", "12.345", "1000", "1500", "-1500", "12345.6789")) {
                val jm = mode?.let { MicroProps(false).also { micros -> micros.rounder = Precision.BogusRounder().withMode(MathContext(0, it)) } }
                val km = mode?.let { UnitRouter.Context(UnitRouter.Rounder.automatic(UnitDecimal.Rounding.valueOf(it.name))) }
                val jr = j.route(BigDecimal(value), jm); val kr = k.route(UnitDecimal(value), km)
                assertEquals(jr.outputUnit.identifier, kr.outputUnit.getIdentifier(), "$unit/$locale/$usage/$value/$mode")
                assertEquals(jr.complexConverterResult.indexOfQuantity, kr.conversion.indexOfQuantity)
                assertEquals(jr.complexConverterResult.measures.map { it.number.toString() }, kr.conversion.measures.map { it.quantity.toString() }, "$unit/$locale/$usage/$value/$mode")
                assertEquals(jr.complexConverterResult.measures.map { it.unit.identifier }, kr.conversion.measures.map { it.unit.copy().also { u -> u.serialize() }.getIdentifier() })
                if (km != null) assertTrue(km.rounder is UnitRouter.Rounder.Resolved)
            }
        }
    }
    @Test fun exactScaledDivisionMatchesBigDecimal() {
        val random = kotlin.random.Random(78293)
        repeat(250) {
            val a = "${random.nextLong()}E${random.nextInt(-20, 21)}"
            val b = "${random.nextLong(1, Long.MAX_VALUE)}E${random.nextInt(-20, 21)}"
            for (mode in RoundingMode.entries) for (scale in listOf(-2, 0, 6)) {
                val expected = try { BigDecimal(a).divide(BigDecimal(b), scale, mode).toString() } catch (_: ArithmeticException) { "throws" }
                val actual = try { UnitDecimal(a).divideToScale(UnitDecimal(b), scale, UnitDecimal.Rounding.valueOf(mode.name)).toString() } catch (_: ArithmeticException) { "throws" }
                assertEquals(expected, actual, "$a/$b/$scale/$mode")
            }
        }
    }
}
