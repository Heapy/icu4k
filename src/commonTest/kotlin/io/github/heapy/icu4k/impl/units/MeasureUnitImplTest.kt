package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.util.UnitComplexity
import kotlin.test.*

class MeasureUnitImplTest {
    private fun normalized(value: String): String? = MeasureUnitImpl.forIdentifier(value)!!.also { it.serialize() }.getIdentifier()
    @Test fun compoundMixedAliasAndConstantParsing() {
        assertNull(MeasureUnitImpl.forIdentifier(null)); assertNull(MeasureUnitImpl.forIdentifier(""))
        assertEquals("square-meter", normalized("meter-meter"))
        assertEquals("meter-per-second", normalized("meter-per-second"))
        assertEquals(UnitComplexity.MIXED, MeasureUnitImpl.forIdentifier("foot-and-inch")!!.getComplexity())
        assertEquals("meter-per-1e6", normalized("meter-per-1000000"))
        assertEquals("-per-1000-meter", normalized("per-1000-meter"))
        fun aliasResult(value: String): String? = try { normalized(value) } catch (failure: IllegalArgumentException) { "error: ${failure.message}" }
        for (alias in UnitIdentifierData.getAliases()) assertEquals(aliasResult(alias.replacement!!), aliasResult(alias.alias!!))
        assertFailsWith<IllegalArgumentException> { normalized("meter-and-meter") }
        assertFailsWith<IllegalArgumentException> { normalized("meter-per-second-and-hour") }
        assertFailsWith<IllegalArgumentException> { normalized("meter-per-1.0") }
    }
    @Test fun copyingMutableStateAndCacheInvalidation() {
        val unit = MeasureUnitImpl.forIdentifier("meter-per-second")!!
        unit.serialize(); val copy = unit.copy()
        copy.applyDimensionality(2)
        assertEquals("meter-per-second", copy.getIdentifier())
        copy.serialize(); assertEquals("square-meter-per-square-second", copy.getIdentifier())
        assertEquals("meter-per-second", unit.getIdentifier())
        copy.takeReciprocal(); assertNull(copy.getIdentifier())
        copy.serialize(); assertEquals("square-second-per-square-meter", copy.getIdentifier())
        val simplified = MeasureUnitImpl.forIdentifier("square-meter-per-meter")!!.copyAndSimplify()
        simplified.serialize(); assertEquals("meter", simplified.getIdentifier())
    }
    @Test fun portableConstantsUseExactScaleAndRange() {
        assertEquals(Long.MAX_VALUE, parseUnitConstantPortable("9223372036854775807"))
        assertEquals(1000L, parseUnitConstantPortable("1.000e3"))
        assertEquals(10L, parseUnitConstantPortable("٠١e١"))
        assertEquals(0L, parseUnitConstantPortable("0e2147483647"))
        assertFailsWith<IllegalArgumentException> { parseUnitConstantPortable("1.00e1") }
        assertFailsWith<IllegalArgumentException> { parseUnitConstantPortable("9223372036854775808") }
        assertFailsWith<NumberFormatException> { parseUnitConstantPortable("0e-2147483648") }
    }
}
