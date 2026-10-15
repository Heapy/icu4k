// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.math.BigDecimal as IcuDecimal
import kotlin.test.*

class PluralFormatCoreTest {
    private fun decimal(pattern: String = "0.0") = DecimalFormat(pattern, DecimalFormatSymbols(ULocale.ENGLISH))
    private class Probe : NumberFormat() {
        var doubles = 0
        var longs = 0
        var callback: () -> Unit = {}
        private fun emit(output: StringBuilder?): StringBuilder { callback(); return output!!.append("formatted") }
        override fun format(number: Double, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder { doubles++; return emit(output) }
        override fun format(number: Long, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder { longs++; return emit(output) }
        override fun format(number: BigInteger?, output: StringBuilder?, position: FormatFieldPosition?) = emit(output)
        override fun format(number: BigDecimal?, output: StringBuilder?, position: FormatFieldPosition?) = emit(output)
        override fun format(number: IcuDecimal?, output: StringBuilder?, position: FormatFieldPosition?) = emit(output)
        override fun parse(text: String?, position: ParsePosition?): Number? = throw UnsupportedOperationException()
    }
    @Test fun offsetsExplicitValuesAndRoundedPluralOperands() {
        val rules = PluralRules.forLocale(ULocale.ENGLISH)
        val f = PluralFormatCore(rules, decimal())
        f.applyPattern("=1{explicit} one{one #} other{other #}")
        assertEquals("explicit", f.format(1.0))
        assertEquals("other 1.0", f.format(1.04)) // Rounded visible fraction changes plural operands.
        f.applyPattern("offset:1 =1{explicit} one{one #} other{other #}")
        assertEquals("other 1.0", f.format(2.0))
        f.numberFormat = decimal("0")
        assertEquals("one 1", f.format(2.0))
        f.applyPattern("other{{n,plural,one{'#'}other{#}} '#': #}")
        assertEquals("{n,plural,one{#}other{#}} '#': 3", f.format(3.0))
    }
    @Test fun selectorsAreLazyButExplicitMatchesOverrideEarlierKeywords() {
        val p = MessagePattern().parsePluralStyle("offset:1 one{keyword} =2{explicit} other{fallback}")
        var calls = 0
        val selector = PluralFormatCore.PluralSelector { context, n ->
            assertEquals("context", context)
            assertEquals(1.0, n)
            calls++
            "one"
        }
        val start = PluralFormatCore.findSubMessage(p, 0, selector, "context", 2.0)
        assertEquals("explicit", p.getPatternString()!!.substring(p.getPart(start).getLimit(), p.getPatternIndex(p.getLimitPartIndex(start))))
        assertEquals(1, calls)
        val other = MessagePattern().parsePluralStyle("other{fallback}")
        assertEquals(1, PluralFormatCore.findSubMessage(other, 0, null, null, Double.NaN))
    }
    @Test fun customNumbersCallbacksAndCloneOwnerBinding() {
        val one = PluralRules.parseDescription("one: n = 1")
        val two = PluralRules.parseDescription("two: n = 1")
        val nf = Probe()
        val f = PluralFormatCore(one, nf)
        class CustomNumber : Number() {
            var reads = 0
            override fun toDouble(): Double { reads++; return 1.0 }
            override fun toFloat() = 1f
            override fun toInt() = 1
            override fun toLong() = 1L
            override fun toShort() = 1.toShort()
            override fun toByte() = 1.toByte()
            override fun toString() = "not numeric"
        }
        val value = CustomNumber()
        assertEquals("formatted", f.formatAny(value))
        assertEquals(2, value.reads)
        f.applyPattern("one{one #} two{two #} other{other #}")
        nf.callback = { f.rules = two }
        assertEquals("two formatted", f.format(1.0))
        nf.callback = {}
        f.rules = one
        val clone = f.copy()
        clone.rules = two
        assertEquals("one formatted", clone.format(1.0)) // Source clone retains original owner's selector.
        f.rules = two
        assertEquals("two formatted", clone.format(1.0))
        assertFailsWith<IllegalArgumentException> { clone.applyPattern("one{missing fallback}") }
        assertEquals(0.0, clone.offset)
        assertNull(clone.pattern)
        assertEquals("formatted", f.formatAny(1L)) // Shared parser cleared by clone failure.
    }
    @Test fun parseTypeKeepsSourceTextAndPositionPolicy() {
        val f = PluralFormatCore(null, null)
        val pos = FormatFieldPosition(0)
        assertNull(f.parseType(null, null, pos))
        assertEquals(-1, pos.getBeginIndex()); assertEquals(-1, pos.getEndIndex())
        f.applyPattern("one{a}two{long}other{other}")
        assertEquals("long", f.parseType("a long", null, pos))
        assertEquals(2, pos.getBeginIndex()); assertEquals(6, pos.getEndIndex())
        var scans = 0
        val scanner = object : RbnfLenientScanner {
            override fun allIgnorable(s: String?) = false
            override fun prefixLength(str: String?, prefix: String?) = 0
            override fun findText(str: String?, key: String?, startingAt: Int): IntArray { scans++; return intArrayOf(3, 100) }
        }
        pos.setBeginIndex(0)
        assertEquals("other", f.parseType("zzzzzz", scanner, pos))
        assertEquals(3, pos.getBeginIndex()); assertEquals(8, pos.getEndIndex()) // Uses pattern length, not scanner length.
        assertEquals(3, scans)
        f.applyPattern("one{}other{fallback}")
        pos.setBeginIndex(Int.MAX_VALUE)
        assertEquals("", f.parseType("ab", null, pos))
        assertEquals(2, pos.getBeginIndex()); assertEquals(2, pos.getEndIndex())
    }
}
