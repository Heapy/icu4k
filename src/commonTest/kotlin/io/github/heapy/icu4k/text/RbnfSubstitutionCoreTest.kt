// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class RbnfSubstitutionCoreTest {
    @Test fun commonRuleGraphFormatsAndParsesWithoutIcu4j() {
        val engine = RbnfTestEngine(arrayOf(RbnfTestEngine.ENGLISH))
        for ((number, expected) in listOf(0L to "zero", 21L to "twenty-one", 101L to "one hundred one", -1234L to "minus one thousand two hundred thirty-four")) {
            assertEquals(expected, engine.format(number))
            val position = ParsePosition(0)
            assertEquals(number, engine.getDefaultRuleSet()!!.parse(expected + " trailing", position, Double.MAX_VALUE, 0, 0))
            assertEquals(expected.length, position.index)
        }
        assertEquals("twelve point zero three four", engine.format(12.034))
        val pp = ParsePosition(0)
        assertEquals(12.034, engine.getDefaultRuleSet()!!.parse("twelve point zero three four!", pp, Double.MAX_VALUE, 0, 0))
        assertEquals("twelve point zero three four".length, pp.index)
    }
    @Test fun fractionalRuleSetsNumeratorsAndZeroPadding() {
        val engine = RbnfTestEngine(arrayOf(
            "%main: x.x: << and >%fractions>; ${RbnfTestEngine.DIGITS} 10: ten; 20: twenty[ >>]; 100: << hundred[ >>];",
            "%fractions: 2: half; 3: <0< thirds; 100: <%main<< hundredths;"
        ))
        assertTrue(engine.findRuleSet("%fractions").isFractionSet())
        assertEquals("zero and half", engine.format(0.5))
        assertEquals("two and 2 thirds", engine.format(2.0 + 2.0 / 3.0))
        assertEquals("zero and zero five hundredths", engine.format(0.05))
        // The source zero-padding parser returns only the final numerator's consumed length.
        val sub = RbnfNumeratorSubstitution(0, 100.0, engine.getDefaultRuleSet(), "<%main<<")
        val pp = ParsePosition(0)
        assertEquals(0.05, sub.doParse("zero five", pp, 100.0, 1000.0, true, 0, 0))
        assertEquals(4, pp.index)
    }
    @Test fun numericPatternsPredecessorAndDigitSpacing() {
        val engine = RbnfTestEngine(arrayOf(
            "%main: -x: minus >0>; x.x: <0< point >>>; ${RbnfTestEngine.DIGITS} 10: ten[->>]; 20: twenty[->>>]; 100: <0< hundred[ >00>];"
        ))
        assertEquals("2 hundred 03", engine.format(203L))
        assertEquals("minus 12", engine.format(-12L))
        assertEquals("2 point zerothree", engine.format(2.03))
        assertEquals("twenty-ten-one", engine.format(21L)) // >>> forces the preceding rule, including its literal text.
    }
    @Test fun numericTransformBoundariesAndFactories() {
        val engine = RbnfTestEngine(arrayOf(RbnfTestEngine.ENGLISH))
        val set = engine.getDefaultRuleSet()!!
        val rule = RbnfRuleCore(engine, "1: literal")
        val multiplier = RbnfMultiplierSubstitution(0, rule, set, "<<")
        assertEquals(Long.MAX_VALUE, multiplier.transformNumber(Long.MAX_VALUE - 1)) // Source long -> double -> long roundtrip.
        val absolute = RbnfAbsoluteValueSubstitution(0, set, ">>")
        assertEquals(Long.MIN_VALUE, absolute.transformNumber(Long.MIN_VALUE))
        assertEquals(0.0.toRawBits(), absolute.transformNumber(-0.0).toRawBits())
        assertNull(RbnfSubstitutionCore.makeSubstitution(0, null, null, null, null, ""))
        assertFailsWith<IllegalArgumentException> { RbnfSameValueSubstitution(0, set, "==") }
        assertFailsWith<IllegalArgumentException> { RbnfSubstitutionCore.makeSubstitution(0, RbnfRuleCore(engine, "-x: negative"), null, set, engine, "<<") }
        assertFailsWith<IllegalArgumentException> { RbnfFractionalPartSubstitution(0, set, ">0>") }
        val modulus = RbnfModulusSubstitutionCore(0, rule, null, set, ">>")
        assertTrue(modulus.isModulusSubstitution())
        assertFailsWith<IllegalStateException> { modulus.setDivisor(2, 64) }
        assertFailsWith<ArithmeticException> { modulus.transformNumber(1L) } // Failed setter retains divisor zero.
    }
    @Test fun numberConversionsPreserveVirtualOverridesAndPrimitiveCastRules() {
        val custom = object : Number() {
            override fun toInt() = -123
            override fun toLong() = 456L
            override fun toDouble() = 789.0
            override fun toFloat() = 0f
            override fun toByte() = 0.toByte()
            override fun toShort() = 0.toShort()
            override fun toString() = "not a number"
        }
        assertEquals(-123, rbnfIntValue(custom))
        assertEquals(456L, rbnfLongValue(custom))
        assertEquals(Int.MAX_VALUE, rbnfIntValue(Double.POSITIVE_INFINITY))
        assertEquals(Int.MIN_VALUE, rbnfIntValue(Double.NEGATIVE_INFINITY))
        assertEquals(0, rbnfIntValue(Double.NaN))
        assertEquals(-1, rbnfIntValue(Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, rbnfLongValue(Double.POSITIVE_INFINITY))
    }
    @Test fun sourceAsymmetricEqualityAndNaNDenominatorsAreRetained() {
        val engine = RbnfTestEngine(arrayOf(RbnfTestEngine.ENGLISH))
        val set = engine.getDefaultRuleSet()
        val withSet = RbnfSameValueSubstitution(0, set, "")
        val withoutSet = RbnfSameValueSubstitution(0, null, "")
        assertTrue(withSet.equals(withoutSet))
        assertFalse(withoutSet.equals(withSet))
        val numerator = RbnfNumeratorSubstitution(0, Double.NaN, set, "<%main<")
        assertFalse(numerator.equals(numerator))
    }
}
