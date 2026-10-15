@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.util.Output as JOutput
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD as BCD
import io.github.heapy.icu4k.util.Output
import kotlin.test.*

class PluralRulesQuantitySamplesOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Exception) { e.javaClass.simpleName }
    private fun jq(text: String): JQuantity = JBCD.fromExponentString(text)
    private fun kq(text: String): DecimalQuantity = BCD.fromExponentString(text)
    private fun jvalues(values: Collection<JQuantity>?): Any? = values?.let { listOf(it is Set<*>, it.map { q -> q.toString() }) }
    private fun kvalues(values: Collection<DecimalQuantity>?): Any? = values?.let { listOf(it is Set<*>, it.map { q -> q.toString() }) }

    @Test fun localeAndParsedSamplesMatchSource() {
        val descriptions = linkedSetOf("", "one: n = 1; few: n = 2..4; other:",
            "one: n = 1 @integer 1; few: n = 2..4 @integer 2~4 @decimal 2.0~2.3; other: @integer 0, 5, … @decimal 0.0, 1.0~1.2, …",
            "one: n = 1 @decimal 1.0; other:")
        for (locale in JRules.getAvailableULocales()) for (type in JRules.PluralType.entries) {
            descriptions.add(JRules.forLocale(locale, type).toString())
        }
        for (description in descriptions) {
            val j = JRules.parseDescription(description); val k = PluralRules.parseDescription(description)
            for (keyword in j.keywords + setOf("unknown", null)) {
                assertEquals(outcome { jvalues(j.getDecimalQuantitySamples(keyword)) }, outcome { kvalues(k.getDecimalQuantitySamples(keyword)) }, description)
                assertEquals(outcome { jvalues(j.getAllKeywordDecimalQuantityValues(keyword)) }, outcome { kvalues(k.getAllKeywordDecimalQuantityValues(keyword)) }, description)
                assertEquals(outcome { j.getUniqueKeywordDecimalQuantityValue(keyword).toString() }, outcome { k.getUniqueKeywordDecimalQuantityValue(keyword).toString() }, description)
                for (type in JRules.SampleType.entries + listOf(null)) {
                    val kt = type?.let { PluralRules.SampleType.valueOf(it.name) }
                    val js = j.getDecimalSamples(keyword, type); val ks = k.getDecimalSamples(keyword, kt)
                    assertEquals(js?.toString(), ks?.toString(), "$description/$keyword/$type")
                    if (js != null && ks != null) {
                        assertSame(ks, k.getDecimalSamples(keyword, kt))
                        assertSame(ks.samples, ks.getSamples())
                        assertEquals(js.bounded, ks.bounded)
                        assertEquals(js.sampleType.name, ks.sampleType.name)
                        assertEquals(js.samples.map { listOf(it.start.toString(), it.end.toString(), it.start === it.end) },
                            ks.samples.map { listOf(it.start.toString(), it.end.toString(), it.start === it.end) })
                        val jd = mutableListOf<Double>(); val kd = mutableListOf<Double>()
                        assertSame(jd, js.addSamples(jd)); assertSame(kd, ks.addSamples(kd)); assertEquals(jd, kd)
                        val jq = mutableListOf<JQuantity>(); val kq = mutableListOf<DecimalQuantity>()
                        assertSame(jq, js.addDecimalQuantitySamples(jq)); assertSame(kq, ks.addDecimalQuantitySamples(kq))
                        assertEquals(jq.map { it.toString() }, kq.map { it.toString() })
                        val je = linkedSetOf<JQuantity>(); val ke = linkedSetOf<DecimalQuantity>()
                        js.getStartEndSamples(je); ks.getStartEndSamples(ke)
                        assertEquals(je.map { it.toString() }, ke.map { it.toString() })
                        assertNull(ks.addSamples(null)); assertNull(ks.addDecimalQuantitySamples(null))
                        js.addSamples(jd, jq); ks.addSamples(kd, kq)
                        assertEquals(jd, kd); assertEquals(jq.map { it.toString() }, kq.map { it.toString() })
                    }
                    assertEquals(outcome { jvalues(j.getDecimalQuantitySamples(keyword, type)) }, outcome { kvalues(k.getDecimalQuantitySamples(keyword, kt)) }, "$description/$keyword/$type")
                    assertEquals(outcome { jvalues(j.getAllKeywordValues(keyword, type)) }, outcome { kvalues(k.getAllKeywordValues(keyword, kt)) }, "$description/$keyword/$type")
                }
            }
        }
    }
    @Test fun keywordStatusAndOutputTransitionsMatchSource() {
        for (description in listOf("", "one:n=1; few:n=2..4; other:", "one:n=1 @integer 1; few:n=2..3 @integer 2,3 @decimal 2.0,2.1; other: @integer 0,4,…", "one:n=1 @decimal 1.0; other:")) {
            val j = JRules.parseDescription(description); val k = PluralRules.parseDescription(description)
            for (keyword in j.keywords + setOf("unknown", null)) for (type in JRules.SampleType.entries + listOf(null)) {
                val kt = type?.let { PluralRules.SampleType.valueOf(it.name) }
                for (texts in listOf(null, emptyList(), listOf("1"), listOf("1", "2", "3", "4"), listOf("1.0", "2.0", "2.1"))) for (offset in listOf(-1, 0, 2)) {
                    val jo = JOutput<JQuantity>(jq("99")); val ko = Output<DecimalQuantity>(kq("99"))
                    val je = texts?.mapTo(linkedSetOf(), ::jq); val ke = texts?.mapTo(linkedSetOf(), ::kq)
                    assertEquals(outcome { j.getKeywordStatus(keyword, offset, je, jo, type).name },
                        outcome { k.getKeywordStatus(keyword, offset, ke, ko, kt).name }, "$description/$keyword/$type/$texts/$offset")
                    assertEquals(jo.value?.toString(), ko.value?.toString())
                    assertEquals(outcome { j.getKeywordStatus(keyword, offset, je, null, type).name },
                        outcome { k.getKeywordStatus(keyword, offset, ke, null, kt).name })
                    if (type == JRules.SampleType.INTEGER) {
                        assertEquals(outcome { j.getKeywordStatus(keyword, offset, je, jo).name }, outcome { k.getKeywordStatus(keyword, offset, ke, ko).name })
                        assertEquals(jo.value?.toString(), ko.value?.toString())
                    }
                }
            }
        }
    }
    @Test fun rangeIdentityMutabilityAndCollectionCallbacksMatchSource() {
        val j = JRules.parseDescription("one:n=1 @integer 1, 2~2; other: @decimal 1.0~1.4")
        val k = PluralRules.parseDescription("one:n=1 @integer 1, 2~2; other: @decimal 1.0~1.4")
        val js = j.getDecimalSamples("one", JRules.SampleType.INTEGER); val ks = k.getDecimalSamples("one", PluralRules.SampleType.INTEGER)!!
        assertEquals(js.toString(), ks.toString())
        js.samples.first().start.negate(); ks.samples.first().start.negate()
        assertEquals(j.toString(), k.toString())
        assertSame(ks.samples.first().start, ks.samples.first().end)
        assertNotSame(ks.samples.last().start, ks.samples.last().end)
        val jc = mutableListOf<String>(); val kc = mutableListOf<String>()
        val jtarget = object : AbstractMutableCollection<JQuantity>() {
            override val size get() = jc.size
            override fun iterator(): MutableIterator<JQuantity> = error("unused")
            override fun add(element: JQuantity): Boolean { jc.add(element.toExponentString()); element.adjustMagnitude(1); return true }
        }
        val ktarget = object : AbstractMutableCollection<DecimalQuantity>() {
            override val size get() = kc.size
            override fun iterator(): MutableIterator<DecimalQuantity> = error("unused")
            override fun add(element: DecimalQuantity): Boolean { kc.add(element.toExponentString()); element.adjustMagnitude(1); return true }
        }
        j.getDecimalSamples("other", JRules.SampleType.DECIMAL).addDecimalQuantitySamples(jtarget)
        k.getDecimalSamples("other", PluralRules.SampleType.DECIMAL)!!.addDecimalQuantitySamples(ktarget)
        assertEquals(jc, kc); assertEquals(listOf("1.0"), kc)
        val values = k.getDecimalQuantitySamples("one")!!
        assertSame(values.first(), values.first())
        assertFailsWith<UnsupportedOperationException> { (values as MutableCollection).clear() }
        assertFailsWith<UnsupportedOperationException> { (ks.samples as MutableSet).clear() }
        assertSame(PluralRules.NO_UNIQUE_VALUE_DECIMAL_QUANTITY, k.getUniqueKeywordDecimalQuantityValue("unknown"))
        val start = kq("1.0"); val end = kq("1.00")
        assertEquals(outcome { JRules.DecimalQuantitySamplesRange(jq("1.0"), jq("1.00")).toString() }, outcome { PluralRules.DecimalQuantitySamplesRange(start, end).toString() })
        assertEquals(outcome { JRules.DecimalQuantitySamplesRange(null, jq("1")).toString() }, outcome { PluralRules.DecimalQuantitySamplesRange(null, kq("1")).toString() })
    }
}
