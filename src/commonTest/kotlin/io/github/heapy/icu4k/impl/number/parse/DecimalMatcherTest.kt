package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StringSegment
import io.github.heapy.icu4k.impl.number.DecimalFormatProperties
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.impl.number.Grouper
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DecimalMatcherTest {
    private val symbols = DecimalFormatSymbols(ULocale.ENGLISH)
    private val grouper = Grouper.forProperties(DecimalFormatProperties().setGroupingSize(3).setSecondaryGroupingSize(3))
    @Test fun groupingRollbackAndFractions() {
        for ((input, offset, value) in listOf(Triple("1,234.50x", 8, "1234.5"), Triple("1,1", 1, "1"), Triple("1,,234", 6, "1234"), Triple(".5", 2, "0.5"), Triple("𝟙𝟚.𝟛", 7, "12.3"))) {
            val segment = StringSegment(input, false)
            val result = ParsedNumber()
            DecimalMatcher.getInstance(symbols, grouper, 0).match(segment, result)
            assertEquals(offset, segment.getOffset(), input)
            assertEquals(offset, result.charEnd, input)
            assertEquals(value, result.quantity!!.toBigDecimal().stripTrailingZeros().toPlainString(), input)
        }
        val segment = StringSegment("12,34", false)
        val result = ParsedNumber()
        DecimalMatcher.getInstance(symbols, grouper, ParsingUtils.PARSE_FLAG_STRICT_GROUPING_SIZE).match(segment, result)
        assertEquals(0, segment.getOffset()); assertNull(result.quantity)
    }
    @Test fun exponentRollbackAndOverflow() {
        val matcher = ScientificMatcher.getInstance(symbols, grouper)
        for (input in listOf("E", "E+", "E-", "Ex")) {
            val result = ParsedNumber().also { it.quantity = DecimalQuantity_DualStorageBCD(12); it.charEnd = 2 }
            val segment = StringSegment(input, false)
            matcher.match(segment, result)
            assertEquals(0, segment.getOffset()); assertEquals(2, result.charEnd)
            assertEquals("12", result.getNumber().toString())
        }
        for ((input, expected) in listOf("E-2" to "0.12", "E3" to "12000", "E-2147483648" to "0", "E2147483648" to "Infinity")) {
            val result = ParsedNumber().also { it.quantity = DecimalQuantity_DualStorageBCD(12) }
            matcher.match(StringSegment(input, false), result)
            assertEquals(input.length, result.charEnd)
            assertTrue(result.flags and ParsedNumber.FLAG_HAS_EXPONENT != 0)
            assertEquals(expected, result.getNumber().toString())
        }
    }
}
