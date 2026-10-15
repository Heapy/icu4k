// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.units.UnitDecimal
import kotlin.test.*

class PatternStringUtilsCoreTest {
    @Test fun serializesDigitsAffixesPaddingAndRounding() {
        for (pattern in listOf("¤#,##0.00;[¤#,##0.00]", "*xP00S", "0.050", "@@##", "##0.0E+00")) {
            val info = PatternStringParserCore.parseToPatternInfo(pattern)
            val properties = PatternStringParserCore.toProperties(info, 0)
            assertEquals(pattern, PatternStringUtilsCore.propertiesToPatternString(properties, info))
        }
        assertTrue(PatternStringUtilsCore.ignoreRoundingIncrement(UnitDecimal("0.005"), 2))
        assertFalse(PatternStringUtilsCore.ignoreRoundingIncrement(UnitDecimal("0.006"), 2))
        assertFalse(PatternStringUtilsCore.ignoreRoundingIncrement(UnitDecimal("0.005"), 3))
    }
    @Test fun localizedNotationQuotesAndDigits() {
        val symbols = listOf("%", "‰", ",", "\u202f", "-", "+", ";", "@", "E", "*", "#") + (0..9).map { it.toString() }
        assertEquals("#\u202f##0,00", PatternStringUtilsCore.convertLocalized("#,##0.00", symbols::get, true))
        assertEquals("#,##0.00", PatternStringUtilsCore.convertLocalized("#\u202f##0,00", symbols::get, false))
        val arabic = symbols.toMutableList().apply { this[11] = "٠" }
        assertEquals("'0'٠", PatternStringUtilsCore.convertLocalized("'0'0", arabic::get, true))
        val collision = symbols.toMutableList().apply { this[2] = "x" }
        assertEquals("'x'0", PatternStringUtilsCore.convertLocalized("x0", collision::get, true))
        val reads = mutableListOf<Int>()
        assertFailsWith<IllegalArgumentException> { PatternStringUtilsCore.convertLocalized("'", { reads.add(it); symbols[it] }, true) }
        assertEquals((0..20).toList(), reads)
        assertNull(PatternStringUtilsCore.convertLocalized(null, null, true))
    }
    @Test fun signsApproximationAndPermille() {
        val info = PatternStringParserCore.parseToPatternInfo("0%")
        val output = StringBuilder("old")
        PatternStringUtilsCore.patternInfoToStringBuilder(info, true, PatternStringUtilsCore.PatternSignType.POS_SIGN, true, null, false, output)
        assertEquals("~+", output.toString())
        PatternStringUtilsCore.patternInfoToStringBuilder(info, false, PatternStringUtilsCore.PatternSignType.POS, false, null, true, output)
        assertEquals("‰", output.toString())
        assertEquals(PatternStringUtilsCore.PatternSignType.POS, PatternStringUtilsCore.resolveSignDisplay(PatternStringUtilsCore.SignDisplay.NEGATIVE, Modifier.Signum.NEG_ZERO))
        assertEquals(PatternStringUtilsCore.PatternSignType.POS, PatternStringUtilsCore.resolveSignDisplay(PatternStringUtilsCore.SignDisplay.NEVER, null))
    }
}
