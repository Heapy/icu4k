package io.github.heapy.icu4k.impl.number

import kotlin.test.*

class PatternStringParserTest {
    @Test fun liveParsedFieldsAndQuantity() {
        val info = PatternStringParser.parseToPatternInfo("+0.050x;-0y")
        val affix: AffixPatternProvider = info
        assertEquals("0.05", info.positive!!.rounding!!.toBigDecimal().toString())
        assertEquals("+", affix.getString(0x100))
        val positive = info.positive!!
        info.negative = positive
        assertSame(positive, info.negative)
        positive.hasMinusSign = true
        assertTrue(affix.negativeHasMinusSign())
        positive.suffixEndpoints = (1L shl 32)
        assertEquals("+", affix.getString(0x200))
        info.pattern = "!"
        assertEquals("!", affix.getString(0x200))
        positive.integerTotal = 0
        assertFalse(affix.hasBody())
        positive.hasCurrencyDecimal = true
        assertTrue(affix.currencyAsDecimal())
        positive.rounding!!.setToInt(7)
        assertEquals(7L, info.negative!!.rounding!!.toLong(false))
        info.positive = PatternStringParser.ParsedSubpatternInfo()
        assertEquals(0x0000ffffffff0000L, info.positive!!.groupingSizes)
        info.pattern = null
        assertEquals("", affix.getString(0)) // Empty endpoints never read the pattern.
        info.negative = null
        assertFalse(affix.hasNegativeSubpattern())
        assertFailsWith<NullPointerException> { affix.negativeHasMinusSign() }
    }
    @Test fun publicPropertyProjectionAndClearing() {
        val p = PatternStringParser.parseToProperties("¤0.050", PatternStringParser.IGNORE_ROUNDING_IF_CURRENCY)
        assertNull(p.getRoundingIncrement())
        p.setPositivePrefix("literal")
        PatternStringParser.parseToExistingProperties("0.050", p)
        assertEquals("0.050", p.getRoundingIncrement().toString())
        assertEquals("literal", p.getPositivePrefix())
        PatternStringParser.parseToExistingProperties(null, p)
        assertEquals(DecimalFormatProperties(), p)
        assertFailsWith<IllegalArgumentException> { PatternStringParser.parseToExistingProperties("0#", null) }
        assertFailsWith<NullPointerException> { PatternStringParser.parseToExistingProperties("0", null) }
    }
}
