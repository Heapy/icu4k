// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.number.PluralCondition.SampleType
import kotlin.test.*

class PluralRuleListCoreTest {
    @Test fun orderedRulesAndSampleBounding() {
        val rules = PluralRuleListCore.parse("other: @integer 0, 2, …; one: i = 1 and v = 0 @integer 1")
        assertEquals(listOf("one", "other"), rules.getKeywords().toList())
        fun select(n: Double, v: Int) = rules.select(FixedDecimalCore(n, v)::getPluralOperand)
        assertEquals("one", select(1.0, 0))
        assertEquals("other", select(1.0, 1))
        assertTrue(rules.select(FixedDecimalCore(1.0, 0)::getPluralOperand, "other"))
        assertTrue(rules.isLimited("one", SampleType.INTEGER))
        assertFalse(rules.isLimited("other", SampleType.INTEGER))
        assertTrue(rules.isLimited("missing", SampleType.INTEGER))
        assertFalse(rules.computeLimited("missing", SampleType.INTEGER))
        assertEquals("other", rules.select({ 1.0 }, nan = true))
    }
    @Test fun sampleExpansionRetainsVisibleFractionsAndExponent() {
        val samples = PluralSamplesCore.parse("decimal 1.0~1.2, 2.00, 1.0c3~1.2c3, …")
        val quantities = mutableListOf<DecimalQuantityCore>()
        samples.addSamples(null, quantities)
        assertEquals(listOf("1.0", "1.1", "1.2", "2.00", "1.0c3", "1.1c3", "1.2c3"), quantities.map { it.toExponentString() })
        val doubles = mutableListOf<Double>()
        samples.addSamples(doubles, null)
        assertEquals(listOf(1.1, 1.2), doubles)
        assertFalse(samples.bounded)
        assertEquals("@decimal 1.0~1.2, 2.00, 1.0c3~1.2c3, …", samples.toString())
        val integers = mutableListOf<Double>()
        PluralSamplesCore.parse("integer 0~3").addSamples(integers, null)
        assertTrue(integers.isEmpty()) // Source's double sample path excludes all integers.
    }
    @Test fun malformedRulesAndRanges() {
        assertFailsWith<IllegalArgumentException> { PluralRuleListCore.parse("one:n=1;one:n=2") }
        assertFailsWith<IllegalArgumentException> { PluralRuleListCore.parse("other:n=1") }
        assertFailsWith<IllegalArgumentException> { PluralSamplesCore.parse("decimal 1.0~1.00") }
        assertFailsWith<IllegalArgumentException> { PluralSamplesCore.parse("integer 1, …, 2") }
        assertEquals("@integer 1~1, 1, 1", PluralSamplesCore.parse("integer 1~1,1,1").toString())
    }
}
