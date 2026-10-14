package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class UnitRouterTest {
    @Test fun magnitudeAndContextSelectUnitAndRounder() {
        val router = UnitRouter("meter", ULocale("en_001"), "default")
        assertTrue(router.getOutputUnits().isNotEmpty())
        val large = router.route(UnitDecimal("1500"))
        assertEquals("kilometer", large.outputUnit.getIdentifier())
        val context = UnitRouter.Context(UnitRouter.Rounder.automatic())
        val rounded = router.route(UnitDecimal("1500"), context)
        assertTrue(context.rounder is UnitRouter.Rounder.Resolved)
        assertEquals("1.5", rounded.conversion.measures.single().quantity.toString())
        assertEquals(large.outputUnit.getIdentifier(), router.route(UnitDecimal("-1500")).outputUnit.getIdentifier())
    }
    @Test fun incrementAndDefaultPrecisionUseExactDecimalRounding() {
        val rounder = UnitRouter.Rounder.automatic() as UnitRouter.Rounder.Unresolved
        val increment = rounder.resolve(UnitRouter.parseSkeleton("precision-increment/0.5"))
        assertEquals("2", increment.apply(UnitDecimal("2.25")).toString())
        assertEquals("3", increment.apply(UnitDecimal("2.75")).toString())
        val significant = rounder.resolve(UnitRouter.Precision.IntegerMinTwo)
        assertEquals("0.0012", significant.apply(UnitDecimal("0.0012345")).toString())
        assertEquals("12", significant.apply(UnitDecimal("12.345")).toString())
        assertFailsWith<IllegalArgumentException> { UnitRouter.parseSkeleton("precision-increment/0") }
    }
}
