package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class NumberFormatterPipelineTest {
    private fun format(macros: MacroProps, value: Double): String {
        val output = FormattedStringBuilder()
        NumberFormatterImpl(macros).format(DecimalQuantity_DualStorageBCD(value), output)
        return output.toString()
    }
    @Test fun defaultCurrencyScientificAndMixedUnits() {
        val macros = MacroProps().also { it.loc = ULocale.ENGLISH }
        assertEquals("12,345.6789", format(macros, 12345.6789))
        macros.unit = Currency.getInstance("USD")
        assertEquals("$12,345.68", format(macros, 12345.6789))
        macros.unit = null; macros.notation = Notation.scientific()
        assertEquals("1.234568E4", format(macros, 12345.6789))
        macros.notation = null; macros.unit = MeasureUnit.forIdentifier("foot-and-inch"); macros.unitWidth = UnitWidth.FULL_NAME
        assertEquals("1 foot, 6 inches", format(macros, 1.5))
        assertEquals("-1 foot, 6 inches", format(macros, -1.5))
    }
    @Test fun compiledAndOneShotAgreeAndExecutionCrossesThreshold() {
        val macros = MacroProps().also { it.loc = ULocale("ar@numbers=arab"); it.threshold = 3L }
        val pipeline = NumberFormatterImpl(macros)
        val execution = NumberFormatterExecution(macros)
        for (i in 0..5) {
            val a = FormattedStringBuilder(); val b = FormattedStringBuilder(); val c = FormattedStringBuilder()
            pipeline.format(DecimalQuantity_DualStorageBCD(12.5), a)
            NumberFormatterImpl.formatStatic(macros, DecimalQuantity_DualStorageBCD(12.5), b)
            execution.formatImpl(DecimalQuantity_DualStorageBCD(12.5), c)
            assertEquals("١٢٫٥", a.toString()); assertEquals(a.toString(), b.toString()); assertEquals(a.toString(), c.toString())
        }
    }
    @Test fun integralMixedComponentRemainsExactBeyondLong() {
        val number = io.github.heapy.icu4k.impl.units.MixedUnitInteger(BigDecimal("18446744073709551617")).toNumber()
        val quantity = DecimalQuantity_DualStorageBCD(number)
        assertEquals("18446744073709551617", quantity.toPlainString())
        val macros = MacroProps().also { it.loc = ULocale.ENGLISH }
        val output = FormattedStringBuilder()
        NumberFormatterImpl(macros).format(quantity, output)
        assertEquals("18,446,744,073,709,551,617", output.toString())
    }

}
