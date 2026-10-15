// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.math.BigDecimal as IcuDecimal
import kotlin.test.*

class NumberFormatTest {
    private class Probe : NumberFormat() {
        var events = arrayListOf<String>()
        var fail = false
        var different = false
        var parseAdvance = 1
        var parseResult: Number? = 12L
        override fun format(number: Long, output: StringBuilder?, position: FormatFieldPosition?) = emit("long:$number", output)
        override fun format(number: Double, output: StringBuilder?, position: FormatFieldPosition?) = emit("double:$number", output)
        override fun format(number: BigInteger?, output: StringBuilder?, position: FormatFieldPosition?) = emit("integer:$number", output)
        override fun format(number: BigDecimal?, output: StringBuilder?, position: FormatFieldPosition?) = emit("decimal:$number", output)
        override fun format(number: IcuDecimal?, output: StringBuilder?, position: FormatFieldPosition?) = emit("icu:$number", output)
        private fun emit(value: String, output: StringBuilder?): StringBuilder {
            events.add(value)
            if (fail) throw IllegalStateException("format failed")
            output!!.append(value)
            return if (different) StringBuilder("different") else output
        }
        override fun parse(text: String?, position: ParsePosition?): Number? {
            events.add("parse:$text")
            position!!.index += parseAdvance; position.errorIndex = 7
            return parseResult
        }
        override fun setCurrency(value: Currency?) = withNumberFormatMonitor {
            events.add("currency:${value?.getCurrencyCode()}"); super.setCurrency(value)
        }
        fun effective() = getEffectiveCurrency()
        override fun clone(): Probe = Probe().also {
            copyNumberFormatTo(it); it.events = events; it.fail = fail; it.different = different
            it.parseAdvance = parseAdvance; it.parseResult = parseResult
        }
    }
    @Test fun settingsAndEqualityMatchBasePolicy() {
        val a = Probe(); val b = Probe()
        assertEquals(a, b); assertEquals(1483, a.hashCode())
        a.setMaximumIntegerDigits(-1); assertEquals(0, a.getMaximumIntegerDigits()); assertEquals(0, a.getMinimumIntegerDigits())
        a.setMinimumIntegerDigits(99); assertEquals(99, a.getMaximumIntegerDigits())
        a.setMaximumFractionDigits(-10); a.setMinimumFractionDigits(12)
        assertEquals(12, a.getMaximumFractionDigits()); assertEquals(12, a.getMinimumFractionDigits())
        a.setGroupingUsed(false); a.setParseStrict(true); a.setParseIntegerOnly(true)
        a.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
        a.setContext(DisplayContext.LENGTH_SHORT)
        assertEquals(DisplayContext.CAPITALIZATION_FOR_STANDALONE, a.getContext(DisplayContext.Type.CAPITALIZATION))
        assertEquals(DisplayContext.CAPITALIZATION_NONE, a.getContext(null))
        assertFailsWith<NullPointerException> { a.setContext(null) }
        val copy = a.clone(); assertEquals(a, copy); assertNotSame(a, copy); assertSame(a.events, copy.events)
        b.setCurrency(Currency.getInstance("EUR")); assertEquals(Probe(), b)
        assertFailsWith<UnsupportedOperationException> { a.getRoundingMode() }
        assertFailsWith<UnsupportedOperationException> { a.setRoundingMode(1) }
    }
    @Test fun dispatchConvenienceResultsAndVirtualDoubleConversion() {
        val p = Probe()
        val objects: List<Any> = listOf(12L, BigInteger("12"), BigDecimal("12.5"), IcuDecimal("12.5"), 12, 12.5)
        val kinds = listOf("long:", "integer:", "decimal:", "icu:", "double:", "double:")
        for ((index, value) in objects.withIndex()) assertTrue(p.format(value).startsWith(kinds[index]))
        assertEquals("Cannot format given Object as a Number", assertFailsWith<IllegalArgumentException> { p.format("12") }.message)
        class CustomNumber : Number() {
            override fun toDouble() = 12.5
            override fun toFloat() = 12.5f
            override fun toLong() = 12L
            override fun toInt() = 12
            override fun toShort() = 12.toShort()
            override fun toByte() = 12.toByte()
            override fun toString() = "not numeric"
        }
        assertEquals("double:12.5", p.format(CustomNumber() as Any))
        p.different = true
        assertEquals("long:12", p.format(12L))
        assertEquals("different", p.format(12.5))
    }
    @Test fun currencyEqualityRemainsVirtualEvenForSameReference() {
        class CountingCurrency : Currency("USD") {
            var calls = 0
            override fun equals(other: Any?): Boolean { calls++; return super.equals(other) }
            override fun hashCode(): Int = super.hashCode()
        }
        val p = Probe(); val currency = CountingCurrency()
        p.setCurrency(currency)
        p.format(CurrencyAmount(12L, currency))
        assertEquals(1, currency.calls)
    }
    @Test fun currencyRestorationAndParseFailureOrder() {
        val p = Probe(); p.setCurrency(Currency.getInstance("USD")); p.events.clear()
        val amount = CurrencyAmount(12L, Currency.getInstance("EUR"))
        assertEquals("long:12", p.format(amount))
        assertEquals(listOf("currency:EUR", "long:12", "currency:USD"), p.events)
        p.fail = true
        assertFailsWith<IllegalStateException> { p.format(amount) }
        assertEquals("EUR", p.getCurrency()!!.getCurrencyCode())
        p.fail = false; p.parseAdvance = 0
        val error = assertFailsWith<ParseException> { p.parse("bad") }
        assertEquals("Unparseable number: \"bad\"", error.message)
        p.parseAdvance = 2; p.parseResult = null
        assertNull(p.parse("ok"))
        p.parseResult = 9L
        val parsed = p.parseCurrency("9EUR", ParsePosition(0))!!
        assertEquals(9L, parsed.getNumber()); assertEquals("EUR", parsed.getCurrency().getCurrencyCode())
    }
}
