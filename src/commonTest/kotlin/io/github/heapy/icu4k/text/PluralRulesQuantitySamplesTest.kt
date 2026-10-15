package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD as BCD
import io.github.heapy.icu4k.util.Output
import kotlin.test.*

class PluralRulesQuantitySamplesTest {
    @Test fun samplesRetainRangeIdentityAndNumericFormatting() {
        val rules = PluralRules.parseDescription("one: n = 1 @integer 1; few: n = 2..3 @integer 2~3 @decimal 2.0~2.2; other:")
        val samples = rules.getDecimalSamples("few", PluralRules.SampleType.DECIMAL)!!
        assertSame(samples, rules.getDecimalSamples("few", PluralRules.SampleType.DECIMAL))
        assertEquals("@decimal 2.0~2.2", samples.toString())
        val quantities = mutableListOf<DecimalQuantity>()
        assertSame(quantities, samples.addDecimalQuantitySamples(quantities))
        assertEquals(listOf("2.0", "2.1", "2.2"), quantities.map { it.toExponentString() })
        val doubles = mutableListOf<Double>(); samples.addSamples(doubles)
        assertEquals(listOf(2.1, 2.2), doubles)
        assertEquals(listOf(2.0, 2.1, 2.2), rules.getSamples("few", PluralRules.SampleType.DECIMAL)?.toList())
        val one = rules.getDecimalSamples("one", PluralRules.SampleType.INTEGER)!!.samples.first()
        assertSame(one.start, one.end)
        one.start.negate()
        assertEquals("@integer -1", rules.getDecimalSamples("one", PluralRules.SampleType.INTEGER).toString())
    }
    @Test fun statusSupportsInterfaceInputsAndClearsOutput() {
        val rules = PluralRules.parseDescription("one:n=1; few:n=2..3; other:")
        val output = Output<DecimalQuantity>(BCD(99))
        val explicit: DecimalQuantity = object : DecimalQuantity by BCD(2) {}
        assertEquals(PluralRules.KeywordStatus.SUPPRESSED, rules.getKeywordStatus("one", 1, setOf(explicit), output))
        assertNull(output.value)
        assertEquals(PluralRules.KeywordStatus.UNIQUE, rules.getKeywordStatus("one", 0, null, output))
        assertEquals("1", output.value!!.toPlainString())
        assertEquals(PluralRules.KeywordStatus.INVALID, rules.getKeywordStatus(null, 0, null, output))
        assertNull(output.value)
        assertEquals(PluralRules.KeywordStatus.UNBOUNDED, rules.getKeywordStatus("other", 0, null, output))
        assertSame(PluralRules.NO_UNIQUE_VALUE_DECIMAL_QUANTITY, rules.getUniqueKeywordDecimalQuantityValue("other"))
        assertEquals("1", rules.getUniqueKeywordDecimalQuantityValue("one").toPlainString())
    }
}
