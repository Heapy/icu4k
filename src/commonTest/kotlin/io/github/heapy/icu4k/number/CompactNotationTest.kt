package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CompactNotationTest {
    @Test fun compactPipelineSelectsAffixesAfterRounding() {
        for (safe in listOf(false, true)) {
            val builder = MutablePatternModifier(true)
            builder.setPatternInfo(PatternStringParser.parseToPatternInfo("0"), null)
            builder.setPatternAttributes(SignDisplay.AUTO, false, false)
            builder.setSymbols(DecimalFormatSymbols(ULocale("en")), Currency.getInstance("USD"), UnitWidth.SHORT, null)
            val micros = MicroProps(false).apply { rounder = Precision.maxSignificantDigits(2) }
            val handler = Notation.compactShort().withLocaleData(ULocale("en"), "latn", CompactData.CompactType.DECIMAL,
                PluralRules.forLocale(ULocale("en")), builder, safe, MicroPropsGenerator { micros })
            val quantity = DecimalQuantity_DualStorageBCD(1200)
            assertSame(micros, handler.processQuantity(quantity))
            assertEquals(3, quantity.getExponent())
            assertNull(micros.rounder)
            val output = FormattedStringBuilder().apply { append("1.2", NumberFormatField.INTEGER) }
            micros.modMiddle!!.apply(output, 0, output.length)
            assertEquals("1.2K", output.toString())
        }
    }
}
