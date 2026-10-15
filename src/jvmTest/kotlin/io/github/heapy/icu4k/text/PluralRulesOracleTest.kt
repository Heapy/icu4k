@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.PluralRulesLoader
import io.github.heapy.icu4k.util.ParseException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.io.*
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import kotlin.test.*

class PluralRulesOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        if (e is java.text.ParseException) "parse:${e.errorOffset}:${e.message}" else e.javaClass.simpleName
    }
    @Test fun publicLocaleRulesSamplesAndFactoriesMatchSource() {
        val jf = JRules.Factory.getDefaultFactory(); val kf = PluralRules.Factory.getDefaultFactory()
        assertSame(PluralRulesLoader.loader, kf)
        assertEquals(JRules.getAvailableULocales().map { it.name }, PluralRules.getAvailableULocales().map { it.getName() })
        val seen = mutableSetOf<String>()
        for ((jl, kl) in JRules.getAvailableULocales().zip(PluralRules.getAvailableULocales())) for (type in JRules.PluralType.entries + listOf(null)) {
            val kt = type?.let { PluralRules.PluralType.valueOf(it.name) }
            val j = JRules.forLocale(jl, type); val k = PluralRules.forLocale(kl, kt)
            assertSame(k, kf.forLocale(kl, kt))
            assertEquals(j.toString(), k.toString(), "$jl/$type")
            assertEquals(j.getKeywords().toList(), k.getKeywords().toList(), "$jl/$type")
            assertEquals(jf.getRulesIdForLocale(jl, type), kf.getRulesIdForLocale(kl, kt), "$jl/$type")
            if (!seen.add(j.toString())) continue
            for (n in listOf(-0.0, -1.0, 0.0, 1.0, 2.0, 3.1, 21.0, 1e6, Double.NaN, Double.POSITIVE_INFINITY)) {
                assertEquals(j.select(n), k.select(n), "$jl/$type/$n")
                for (v in listOf(0, 1, 3)) assertEquals(j.select(n, v, 120), k.select(n, v, 120), "$jl/$type/$n/$v")
            }
            for (keyword in j.keywords + setOf("unknown", null)) {
                assertEquals(j.getRules(keyword), k.getRules(keyword), "$jl/$type/$keyword")
                assertEquals(outcome { j.getUniqueKeywordValue(keyword) }, outcome { k.getUniqueKeywordValue(keyword) }, "$jl/$type/$keyword")
                assertEquals(outcome { j.getAllKeywordValues(keyword)?.toList() }, outcome { k.getAllKeywordValues(keyword)?.toList() }, "$jl/$type/$keyword")
                for (sample in JRules.SampleType.entries) {
                    val ks = PluralRules.SampleType.valueOf(sample.name)
                    assertEquals(j.getSamples(keyword, sample)?.toList(), k.getSamples(keyword, ks)?.toList(), "$jl/$type/$keyword/$sample")
                    assertEquals(outcome { j.computeLimited(keyword, sample) }, outcome { k.computeLimited(keyword, ks) })
                    assertEquals(outcome { j.isLimited(keyword, sample) }, outcome { k.isLimited(keyword, ks) })
                }
            }
        }
        for (locale in listOf(Locale.US, Locale.FRANCE, Locale.ROOT)) {
            assertSame(PluralRules.forLocale(ULocale.forLocale(locale)), PluralRules.forLocale(locale))
            assertEquals(JRules.forLocale(locale).toString(), PluralRules.forLocale(locale).toString())
        }
    }
    @Test fun parseErrorsEqualityAndKeywordMutationMatchSource() {
        for (description in listOf(null, "", " ", "one", "1:n=1", "one:n=1", "other:", "ONE:N=1; other:", "one:n mod", "one:n=1.2", "one:", "other:n=1", "one:n=1;one:n=2", "one:n=1 @integer 1")) {
            assertEquals(outcome { JRules.parseDescription(description).toString() }, outcome { PluralRules.parseDescription(description).toString() }, description)
            assertEquals(JRules.createRules(description)?.toString(), PluralRules.createRules(description)?.toString(), description)
        }
        val texts = listOf("", "other:", "one:n=1", "ONE:N IS 1", "one:n=1 @integer 1")
        for (a in texts) for (b in texts) {
            val ja = JRules.parseDescription(a); val jb = JRules.parseDescription(b)
            val ka = PluralRules.parseDescription(a); val kb = PluralRules.parseDescription(b)
            assertEquals(ja == jb, ka == kb, "$a/$b")
            assertEquals(ja.compareTo(jb), ka.compareTo(kb), "$a/$b")
        }
        val j = JRules.parseDescription("one:n=1").keywords
        val k = PluralRules.parseDescription("one:n=1").getKeywords() as MutableSet<String>
        assertEquals(outcome { j.clear() }, outcome { k.clear() })
        assertEquals(outcome { j.remove("unknown") }, outcome { k.remove("unknown") })
        assertEquals(outcome { j.addAll(emptySet()) }, outcome { k.addAll(emptySet()) })
    }
    @Test fun nullableSampleTypePreservesSourceBranching() {
        for (text in listOf("other:", "one:n=1", "one:n", "one:n or n=1", "one:n and n=1", "one:n=1 @integer 1", "one:n=1 @decimal 1.0,1.00")) {
            val j = JRules.parseDescription(text); val k = PluralRules.parseDescription(text)
            for (keyword in listOf("one", "other", "unknown", null)) {
                val label = "$text/$keyword"
                assertEquals(outcome { j.getSamples(keyword, null)?.toList() }, outcome { k.getSamples(keyword, null)?.toList() }, "$label/samples")
                assertEquals(outcome { j.isLimited(keyword, null) }, outcome { k.isLimited(keyword, null) }, "$label/limited")
                assertEquals(outcome { j.computeLimited(keyword, null) }, outcome { k.computeLimited(keyword, null) }, "$label/computed")
            }
        }
    }
    @Test fun fixedDecimalPublicFieldsMethodsAndSerializationMatchSource() {
        for (n in listOf(-0.0, 1.2, -1.2, 1e19, -1e19, Double.NaN, Double.POSITIVE_INFINITY)) for (v in listOf(0, 2, 6)) {
            val j = JRules.FixedDecimal.createWithExponent(n, v, 3)
            val k = PluralRules.FixedDecimal.createWithExponent(n, v, 3)
            assertEquals(j.toString(), k.toString())
            assertEquals(j.hashCode(), k.hashCode())
            assertEquals(j.toLong(), k.toLong()); assertEquals(j.toInt(), k.toInt())
            assertEquals(j.toDouble().toBits(), k.toDouble().toBits())
            assertEquals(j.source.toBits(), k.getSource().toBits())
            assertEquals(j.visibleDecimalDigitCount, k.getVisibleDecimalDigitCount())
            assertEquals(j.visibleDecimalDigitCountWithoutTrailingZeros, k.getVisibleDecimalDigitCountWithoutTrailingZeros())
            assertEquals(j.decimalDigits, k.getDecimalDigits()); assertEquals(j.decimalDigitsWithoutTrailingZeros, k.getDecimalDigitsWithoutTrailingZeros())
            assertEquals(j.integerValue, k.getIntegerValue()); assertEquals(j.baseFactor, k.getBaseFactor())
            assertEquals(j.isNegative, k.isNegative()); assertEquals(j.isHasIntegerValue, k.isHasIntegerValue())
            for (op in JRules.Operand.entries) assertEquals(j.getPluralOperand(op).toBits(), k.getPluralOperand(PluralRules.Operand.valueOf(op.name)).toBits())
            assertEquals(outcome { serialize(j) }, outcome { serialize(k) })
        }
    }
    private fun serialize(value: Any): ByteArray = ByteArrayOutputStream().also { bytes -> ObjectOutputStream(bytes).use { it.writeObject(value) } }.toByteArray()
    private fun roundTrip(value: Any): Any = ObjectInputStream(ByteArrayInputStream(serialize(value))).use { it.readObject() }
    @Test fun serializationProxyRoundTripsAndRejectsRawCallbacks() {
        for (text in listOf("", "one:n=1", "one:n=1 @integer 1; other:")) {
            val j = roundTrip(JRules.parseDescription(text)) as JRules
            val k = roundTrip(PluralRules.parseDescription(text)) as PluralRules
            assertEquals(j.toString(), k.toString())
            assertNull(k.ranges)
        }
        val restored = roundTrip(PluralRules.forLocale(ULocale("en"))) as PluralRules
        assertEquals(PluralRules.forLocale(ULocale("en")), restored)
        assertNull(restored.ranges)
        for ((target, clazz) in listOf(PluralRules.DEFAULT to PluralRules::class.java, PluralRules.FixedDecimal(1.0) to PluralRules.FixedDecimal::class.java)) {
            for ((name, streamType) in listOf("readObject" to ObjectInputStream::class.java, "writeObject" to ObjectOutputStream::class.java)) {
                val method = clazz.getDeclaredMethod(name, streamType).apply { isAccessible = true }
                val thrown = assertFailsWith<InvocationTargetException> { method.invoke(target, null) }
                assertIs<NotSerializableException>(thrown.targetException)
            }
        }
        val parse: ParseException = assertFailsWith { PluralRules.parseDescription("one") }
        assertIs<java.text.ParseException>(parse)
    }
}
