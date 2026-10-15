// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import kotlin.test.*

class PatternStringParserCoreTest {
    @Test fun affixesGroupingRoundingAndScientificProperties() {
        val info = PatternStringParserCore.parseToPatternInfo("¤#,##0.050;('x'0)")
        assertEquals("¤", info.getString(AffixPatternProvider.FLAG_POS_PREFIX))
        assertEquals("('x'", info.getString(AffixPatternProvider.FLAG_NEG_PREFIX))
        assertTrue(info.hasCurrencySign())
        assertTrue(info.hasNegativeSubpattern())
        val p = PatternStringParserCore.toProperties(info, 0)
        assertEquals(3, p.groupingSize)
        assertTrue(p.groupingUsed)
        assertEquals(3, p.minimumFractionDigits)
        assertEquals("0.050", p.roundingIncrement.toString())
        val currency = PatternStringParserCore.toProperties(info, 1)
        assertEquals(-1, currency.minimumFractionDigits)
        assertNull(currency.roundingIncrement)
        val scientific = PatternStringParserCore.parseToProperties("##0.0E+00")
        assertEquals(3, scientific.maximumIntegerDigits)
        assertEquals(2, scientific.minimumExponentDigits)
        assertTrue(scientific.exponentSignAlwaysShown)
        assertFalse(PatternStringParserCore.parseToPatternInfo(".##").hasBody())
        assertTrue(PatternStringParserCore.parseToProperties("0¤00").currencyAsDecimal)
    }
    @Test fun paddingAndMalformedPatternLocations() {
        val p = PatternStringParserCore.parseToProperties("*'ab'P00S")
        assertEquals("ab", p.padString)
        assertEquals(4, p.formatWidth)
        assertEquals(Padder.PadPosition.BEFORE_PREFIX, p.padPosition)
        val error = assertFailsWith<IllegalArgumentException> { PatternStringParserCore.parseToPatternInfo("0#") }
        assertEquals("Malformed pattern for ICU DecimalFormat: \"0#\": # cannot follow 0 before decimal point at position 1", error.message)
        assertEquals(-1, PatternStringParserCore.parseToProperties(null).minimumIntegerDigits)
        assertTrue(PatternStringParserCore.parseToProperties("").groupingUsed)
    }
    @Test fun paddingPositionsAndSupplementaryPadding() {
        val expected = listOf("___P12S", "P___12S", "P12___S", "P12S___")
        for ((i, position) in Padder.PadPosition.entries.withIndex()) {
            val output = FormattedStringBuilder().apply { append("12", null) }
            val mod = ConstantAffixModifier("P", "S", null, false)
            assertEquals(5, Padder("_", 7, position).padAndApply(mod, ConstantAffixModifier.EMPTY, output, 0, 2))
            assertEquals(expected[i], output.toString())
            assertEquals(position, Padder.PadPosition.fromOld(position.toOld()))
        }
        val output = FormattedStringBuilder().apply { append("1", null) }
        assertEquals(4, Padder.codePoints(0x1f600, 3, null).padAndApply(ConstantAffixModifier.EMPTY, ConstantAffixModifier.EMPTY, output, 0, 1))
        assertEquals("😀😀1", output.toString())
        assertSame(Padder.NONE, Padder.none())
    }
}
