@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.util.Output as JOutput
import io.github.heapy.icu4k.impl.number.PluralCondition.SampleType
import io.github.heapy.icu4k.util.Output
import kotlin.test.*

class PluralRuleSamplesCoreOracleTest {
    private val descriptions = listOf("other:", "one:n=1", "one:n=500", "few:n=2..4", "few:n%10=2..4", "few:n within 0..2", "one:v=0;few:v=1", "one:n=1 @integer 1 @decimal 1.0,1.00; other: @integer 0,2, …", "one:n=1 @integer 1; few:n=2..3 @integer 2,3;other:", "one:n=1 @decimal 1.0~1.2; other: @integer 0,2, …", "few:n=2 @integer 2,2;other:")
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { e.javaClass.simpleName }
    private fun js(value: JQuantity): String = if (value === JRules.NO_UNIQUE_VALUE_DECIMAL_QUANTITY) "sentinel" else value.toExponentString()
    private fun ks(value: DecimalQuantityCore): String = if (value === PluralRuleSamplesCore.NO_UNIQUE_VALUE_DECIMAL_QUANTITY) "sentinel" else value.toExponentString()
    @Test fun samplesAllValuesAndUniquenessMatchSource() {
        for (text in descriptions) {
            val j = JRules.parseDescription(text); val k = PluralRuleSamplesCore(PluralRuleListCore.parse(text))
            for (keyword in listOf("one", "few", "other", "unknown")) {
                assertEquals(j.getAllKeywordValues(keyword)?.toList(), k.getAllKeywordValues(keyword)?.toList(), "$text/$keyword/all")
                assertEquals(js(j.getUniqueKeywordDecimalQuantityValue(keyword)), ks(k.getUniqueKeywordDecimalQuantityValue(keyword)), "$text/$keyword/unique quantity")
                assertEquals(j.getUniqueKeywordValue(keyword), k.getUniqueKeywordValue(keyword), "$text/$keyword/unique")
                for (type in JRules.SampleType.entries) {
                    val kt = SampleType.valueOf(type.name)
                    assertEquals(j.getSamples(keyword, type)?.toList(), k.getSamples(keyword, kt)?.toList(), "$text/$keyword/$type/doubles")
                    val jq = j.getDecimalQuantitySamples(keyword, type); val kq = k.getDecimalQuantitySamples(keyword, kt)
                    assertEquals(jq?.map { it.toExponentString() }, kq?.map { it.toExponentString() }, "$text/$keyword/$type/quantities")
                    assertEquals(jq is Set<*>, kq is Set<*>, "$text/$keyword/$type/set")
                    assertEquals(j.getAllKeywordValues(keyword, type)?.map { it.toExponentString() }, k.getAllKeywordValues(keyword, kt)?.map { it.toExponentString() }, "$text/$keyword/$type/all quantities")
                }
            }
        }
    }
    @Test fun statusOffsetSuppressionAndOutputMatchSource() {
        for (text in descriptions) {
            val j = JRules.parseDescription(text); val k = PluralRuleSamplesCore(PluralRuleListCore.parse(text))
            for (keyword in listOf("one", "few", "other", "unknown")) for (type in JRules.SampleType.entries) for (offset in listOf(-1, 0, 1, Int.MIN_VALUE)) for (explicit in listOf(null, emptyList(), listOf("1"), listOf("2", "3"), listOf("1.0", "2.00", "3.0"), listOf("0", "1", "2", "3", "4", "9"))) {
                val je = explicit?.mapTo(linkedSetOf<JQuantity>()) { JBCD.fromExponentString(it) }
                val ke = explicit?.mapTo(linkedSetOf()) { DecimalQuantityCore.fromExponentString(it) }
                val jo = JOutput<JQuantity>(JBCD(99)); val ko = Output(DecimalQuantityCore(99L))
                val label = "$text/$keyword/$type/$offset/$explicit"
                assertEquals(outcome { j.getKeywordStatus(keyword, offset, je, jo, type).name }, outcome { k.getKeywordStatus(keyword, offset, ke, ko, SampleType.valueOf(type.name)).name }, label)
                assertEquals(jo.value?.toExponentString(), ko.value?.toExponentString(), "$label/output")
            }
        }
    }
    @Test fun returnedQuantitiesAreReadOnlyButDoubleSetsRemainMutable() {
        for (text in listOf("other:", "one:n=1 @integer 1;other:")) {
            val j = JRules.parseDescription(text); val k = PluralRuleSamplesCore(PluralRuleListCore.parse(text))
            for (keyword in j.keywords) for (type in JRules.SampleType.entries) {
                val kt = SampleType.valueOf(type.name)
                val jq = j.getDecimalQuantitySamples(keyword, type) ?: continue
                val kq = k.getDecimalQuantitySamples(keyword, kt)!!
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
                val javaCollection = kq as java.util.Collection<DecimalQuantityCore>
                assertEquals(outcome { jq.clear() }, outcome { javaCollection.clear() }, "$text/$keyword/$type/clear")
                if (kq.isNotEmpty()) {
                    val ji = jq.iterator(); ji.next()
                    val ki = javaCollection.iterator(); ki.next()
                    assertEquals(outcome { ji.remove() }, outcome { ki.remove() }, "$text/$keyword/$type/remove")
                }
                val kd = k.getSamples(keyword, kt)!!
                @Suppress("UNCHECKED_CAST")
                (kd as MutableCollection<Double>).add(999.0)
                assertTrue(999.0 in kd)
            }
        }
    }
    @Test fun localeSampleCorpusMatchesSource() {
        val seen = mutableSetOf<String>()
        for (locale in JRules.getAvailableULocales()) for (pluralType in JRules.PluralType.entries) {
            val j = JRules.forLocale(locale, pluralType)
            if (!seen.add(j.toString())) continue
            val k = PluralRuleSamplesCore(PluralRuleListCore.parse(j.toString()))
            for (keyword in j.keywords) {
                assertEquals(j.getUniqueKeywordValue(keyword), k.getUniqueKeywordValue(keyword), "$locale/$pluralType/$keyword/unique")
                for (type in JRules.SampleType.entries) {
                    val kt = SampleType.valueOf(type.name)
                    val label = "$locale/$pluralType/$keyword/$type"
                    assertEquals(j.getDecimalQuantitySamples(keyword, type)?.map { it.toExponentString() }, k.getDecimalQuantitySamples(keyword, kt)?.map { it.toExponentString() }, "$label/quantities")
                    assertEquals(j.getSamples(keyword, type)?.toList(), k.getSamples(keyword, kt)?.toList(), "$label/doubles")
                    assertEquals(j.getAllKeywordValues(keyword, type)?.map { it.toExponentString() }, k.getAllKeywordValues(keyword, kt)?.map { it.toExponentString() }, "$label/all")
                    val jo = JOutput<JQuantity>(); val ko = Output<DecimalQuantityCore>()
                    val je = linkedSetOf<JQuantity>(JBCD(0), JBCD(1), JBCD(2))
                    val ke = linkedSetOf(DecimalQuantityCore(0L), DecimalQuantityCore(1L), DecimalQuantityCore(2L))
                    assertEquals(j.getKeywordStatus(keyword, 0, je, jo, type).name, k.getKeywordStatus(keyword, 0, ke, ko, kt).name, "$label/status")
                    assertEquals(jo.value?.toExponentString(), ko.value?.toExponentString(), "$label/output")
                }
            }
        }
        assertTrue(seen.size > 50)
    }
}
