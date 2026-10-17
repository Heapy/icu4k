package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class SimpleDateFormatNumericLeapMonthCoreTest {
    private fun parser(pattern: String, trace: MutableList<String> = ArrayList()): SimpleDateFormatNumericLeapMonthCore =
        SimpleDateFormatNumericLeapMonthCore(pattern) { type, style ->
            trace.add("$type:$style")
            val format = when (type) {
                "number" -> if (style.isEmpty()) NumberFormat.getInstance(ULocale.US) else DecimalFormat(style, DecimalFormatSymbols(ULocale.US))
                else -> throw IllegalArgumentException(type)
            }
            MessageFormatParseCore.Parser { source, position -> format.parseObject(source, position) }
        }

    @Test fun constructsNestedStockFormatsBeforeReplacingTopLevelArgumentZero() {
        val trace = ArrayList<String>()
        val parser = parser("{0,choice,0#zero|1#{1,number,000}} / {0}", trace)
        assertEquals(listOf("number:000"), trace)
        parser.setNumberFormat(NumberFormat.getInstance(ULocale.US))
        val pos = ParsePosition(0)
        val result = parser.parse("12 / 34", pos)!!
        assertEquals("34", result[0].toString())
        assertEquals(7, pos.index)
        assertFailsWith<IllegalArgumentException> { parser("{0,plural,other{{1,unknown}}}") }
    }

    @Test fun replacementIsLiveAndExplicitNullTurnsOffComplexParsing() {
        val parser = parser("leap {0,plural,other{#}}!")
        parser.setNumberFormat(null)
        var pos = ParsePosition(0)
        assertEquals("twelve", parser.parse("leap twelve!", pos)!![0])
        assertEquals(12, pos.index)
        val format = DecimalFormat("000", DecimalFormatSymbols(ULocale.US))
        parser.setNumberFormat(format)
        format.setPositivePrefix("+")
        pos = ParsePosition(0)
        assertEquals("12", parser.parse("leap +012!", pos)!![0].toString())
        assertEquals(10, pos.index)
        assertNull(parser.parse("leap nope!", ParsePosition(0)))
    }

    @Test fun namedArgumentsFailAtSetterAfterAllStockFormatsWereCreated() {
        val trace = ArrayList<String>()
        val parser = parser("{month} {n,number}", trace)
        assertEquals(listOf("number:"), trace)
        assertFailsWith<IllegalArgumentException> { parser.setNumberFormat(null) }
    }

    @Test fun keepsOtherArgumentFormatsAndFailurePositions() {
        val parser = parser("L{0} / {1,number,000}!")
        parser.setNumberFormat(NumberFormat.getInstance(ULocale.US))
        val pos = ParsePosition(1)
        val result = parser.parse("xL12 / 007!tail", pos)!!
        assertEquals(listOf("12", "7"), result.map { it.toString() })
        assertEquals(11, pos.index)
        val failed = ParsePosition(1)
        assertNull(parser.parse("xL12 / bad!", failed))
        assertEquals(1, failed.index)
        assertEquals(7, failed.errorIndex)
    }
}
