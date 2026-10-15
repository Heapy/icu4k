// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.number.PluralCondition.SampleType
import io.github.heapy.icu4k.impl.number.PluralRuleSamplesCore.KeywordStatus
import io.github.heapy.icu4k.util.Output
import kotlin.test.*

class PluralRuleSamplesCoreTest {
    private fun rules(text: String) = PluralRuleSamplesCore(PluralRuleListCore.parse(text))
    @Test fun sourceFallbackEnumerationAndMissingSamples() {
        val r = rules("other:")
        assertEquals(201, r.getSamples("other")!!.size)
        assertEquals(2001, r.getSamples("other", SampleType.DECIMAL)!!.size)
        assertNull(r.getAllKeywordValues("other"))
        assertNull(r.getSamples("missing"))
        assertEquals(PluralRuleSamplesCore.NO_UNIQUE_VALUE, r.getUniqueKeywordValue("other"))
        val absent = rules("one:n=500")
        assertNull(absent.getSamples("one"))
        assertNull(absent.getAllKeywordValues("one"))
        assertFailsWith<NullPointerException> { absent.getKeywordStatus("one", 0, null, null) }
    }
    @Test fun explicitSamplesUniquenessAndStatus() {
        val r = rules("one:n=1 @integer 1; few:n=2..3 @integer 2,3; other: @integer 0,4, …")
        val output = Output(DecimalQuantityCore(99L))
        assertEquals(KeywordStatus.INVALID, r.getKeywordStatus("missing", 0, null, output))
        assertNull(output.value)
        assertEquals(KeywordStatus.UNIQUE, r.getKeywordStatus("one", 0, null, output))
        assertEquals(1.0, output.value!!.toDouble())
        assertEquals(KeywordStatus.SUPPRESSED, r.getKeywordStatus("one", 1, setOf(DecimalQuantityCore(2L)), output))
        assertNull(output.value)
        // A bounded category stays BOUNDED even when explicit values leave one sample.
        assertEquals(KeywordStatus.BOUNDED, r.getKeywordStatus("few", 0, setOf(DecimalQuantityCore(2L), DecimalQuantityCore(9L)), output))
        assertEquals(3.0, output.value!!.toDouble())
        assertEquals(KeywordStatus.UNBOUNDED, r.getKeywordStatus("other", 0, null, output))
        assertNull(output.value)
        assertEquals(1.0, r.getUniqueKeywordValue("one"))
        assertEquals(emptyList(), r.getSamples("one", SampleType.DECIMAL)!!.toList())
    }
    @Test fun doubleSamplesIncludeIntegerValuesUnlikeLowLevelSampleExpansion() {
        val r = rules("one:n=1 @integer 1 @decimal 1.0,1.00; other:")
        assertEquals(listOf(1.0), r.getSamples("one", SampleType.DECIMAL)!!.toList())
        assertEquals(listOf("1.0", "1.00"), r.getDecimalQuantitySamples("one", SampleType.DECIMAL)!!.map { it.toExponentString() })
    }
}
