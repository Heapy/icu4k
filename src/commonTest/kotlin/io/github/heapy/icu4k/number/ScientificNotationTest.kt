package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class ScientificNotationTest {
    @Test fun scientificEngineeringCarryAndScalingStage() {
        val parent = MicroProps(true).apply { rounder = Precision.maxSignificantDigits(3) }
        val symbols = DecimalFormatSymbols(ULocale("en"))
        val stage = Notation.scientific().withMinExponentDigits(2).withExponentSignDisplay(SignDisplay.ALWAYS)
            .withLocaleData(symbols, true, MultiplierFormatHandler(Scale.powerOfTen(2), parent))
        val quantity = DecimalQuantity_DualStorageBCD(9.999)
        val result = stage.processQuantity(quantity)!!
        assertEquals(3, quantity.getExponent())
        assertNull(result.rounder)
        val output = FormattedStringBuilder().apply { append("1", NumberFormatField.INTEGER) }
        assertEquals(4, result.modInner!!.apply(output, 0, 1))
        assertEquals("1E+03", output.toString())
        val engineering = Notation.engineering().withLocaleData(symbols, false, parent)
        val value = DecimalQuantity_DualStorageBCD(12345)
        assertEquals(3, engineering.processQuantity(value)!!.let { value.getExponent() })
        assertSame(Notation.scientific(), Notation.scientific())
        assertNotSame(Notation.scientific(), Notation.scientific().withMinExponentDigits(1))
    }
    @Test fun supplementaryExponentDigitsRetainSourceInsertionOrder() {
        val symbols = DecimalFormatSymbols(ULocale("en"))
        symbols.setDigitStrings(Array(10) { i -> charArrayOf(0xd835.toChar(), (0xdfd8 + i).toChar()).concatToString() })
        val stage = Notation.scientific().withLocaleData(symbols, true, MicroProps(true).apply { rounder = Precision.integer() })
        val micros = stage.processQuantity(DecimalQuantity_DualStorageBCD(1e12))!!
        val output = FormattedStringBuilder().apply { append("1", NumberFormatField.INTEGER) }
        micros.modInner!!.apply(output, 0, 1)
        assertEquals(listOf(0x31, 0x45, 0xd835, 0xd835, 0xdfd9, 0xdfda), output.toString().map { it.code })
    }
}
