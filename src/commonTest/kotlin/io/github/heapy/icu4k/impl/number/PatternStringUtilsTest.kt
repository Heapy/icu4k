package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.SignDisplay
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class PatternStringUtilsTest {
    @Test fun publicPropertiesRoundTrip() {
        for (pattern in listOf("#,##0.00", "0.050", "@@##", "0%;(0%)", "*xP00S")) {
            assertEquals(pattern, PatternStringUtils.propertiesToPatternString(PatternStringParser.parseToProperties(pattern)))
        }
        assertTrue(PatternStringUtils.ignoreRoundingIncrement(BigDecimal("0.005"), 2))
        assertFalse(PatternStringUtils.ignoreRoundingIncrement(BigDecimal("0.006"), 2))
    }
    @Test fun publicLocalizedSymbolsAndSignPolicies() {
        val symbols = DecimalFormatSymbols(ULocale("en"))
        symbols.setDecimalSeparatorString(",")
        symbols.setGroupingSeparatorString("\u202f")
        assertEquals("#\u202f##0,00", PatternStringUtils.convertLocalized("#,##0.00", symbols, true))
        assertEquals("#,##0.00", PatternStringUtils.convertLocalized("#\u202f##0,00", symbols, false))
        assertEquals(PatternStringUtils.PatternSignType.POS, PatternStringUtils.resolveSignDisplay(SignDisplay.NEGATIVE, Modifier.Signum.NEG_ZERO))
        val output = StringBuilder("seed")
        PatternStringUtils.patternInfoToStringBuilder(PatternStringParser.parseToPatternInfo("0%"), true,
            PatternStringUtils.PatternSignType.POS_SIGN, true, null, false, output)
        assertEquals("~+", output.toString())
        assertEquals(PatternStringUtils.PatternSignType.entries.toList(), PatternStringUtils.PatternSignType.VALUES.toList())
    }
}
