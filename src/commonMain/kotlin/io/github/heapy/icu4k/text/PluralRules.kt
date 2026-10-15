// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.createResourceCacheLock
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.impl.number.PluralSamplesCore
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.impl.PluralRulesLoader
import io.github.heapy.icu4k.impl.number.DecimalQuantityCore
import io.github.heapy.icu4k.impl.number.FixedDecimalCore
import io.github.heapy.icu4k.impl.number.PluralCondition
import io.github.heapy.icu4k.impl.number.PluralConditionParseException
import io.github.heapy.icu4k.impl.number.PluralRuleListCore
import io.github.heapy.icu4k.impl.number.PluralRuleSamplesCore
import io.github.heapy.icu4k.impl.number.fixedDecimalSerializationFailure
import io.github.heapy.icu4k.impl.number.range.StandardPluralRanges
import io.github.heapy.icu4k.util.ICUSerializable
import io.github.heapy.icu4k.util.ParseException
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Unicode plural rules. Formatted-number adapters are ported separately. */
open class PluralRules private constructor(internal val rules: PluralRuleListCore, internal val ranges: StandardPluralRanges?) : ICUSerializable {
    enum class Operand { n, i, f, t, v, w, e, c, j }
    enum class PluralType { CARDINAL, ORDINAL }
    enum class SampleType { INTEGER, DECIMAL }
    enum class KeywordStatus { INVALID, SUPPRESSED, UNIQUE, BOUNDED, UNBOUNDED }
    interface IFixedDecimal {
        fun getPluralOperand(operand: Operand?): Double
        fun isNaN(): Boolean
        fun isInfinite(): Boolean
        fun isHasIntegerValue(): Boolean
    }
    open class FixedDecimal private constructor(internal val core: FixedDecimalCore) : Number(), Comparable<FixedDecimal>, IFixedDecimal {
        constructor(n: Double, v: Int, f: Long, e: Int, c: Int) : this(FixedDecimalCore(n, v, f, e, c))
        constructor(n: Double, v: Int, f: Long, e: Int) : this(FixedDecimalCore(n, v, f, e))
        constructor(n: Double, v: Int, f: Long) : this(FixedDecimalCore(n, v, f))
        constructor(n: Double, v: Int) : this(FixedDecimalCore(n, v))
        constructor(n: Double) : this(FixedDecimalCore(n))
        constructor(n: Long) : this(FixedDecimalCore(n))
        open fun getSource(): Double = core.getSource()
        open fun getVisibleDecimalDigitCount(): Int = core.getVisibleDecimalDigitCount()
        open fun getVisibleDecimalDigitCountWithoutTrailingZeros(): Int = core.getVisibleDecimalDigitCountWithoutTrailingZeros()
        open fun getDecimalDigits(): Long = core.getDecimalDigits()
        open fun getDecimalDigitsWithoutTrailingZeros(): Long = core.getDecimalDigitsWithoutTrailingZeros()
        open fun getIntegerValue(): Long = core.getIntegerValue()
        open fun getBaseFactor(): Int = core.getBaseFactor()
        open fun isNegative(): Boolean = core.isNegative()
        open fun hasIntegerValue(): Boolean = core.hasIntegerValue()
        override fun isHasIntegerValue(): Boolean = core.isHasIntegerValue()
        override fun isNaN(): Boolean = core.isNaN()
        override fun isInfinite(): Boolean = core.isInfinite()
        override fun getPluralOperand(operand: Operand?): Double = core.getPluralOperand(operand?.let { DecimalQuantityCore.Operand.entries[it.ordinal] })
        override fun compareTo(other: FixedDecimal): Int = core.compareTo(other.core)
        override fun equals(other: Any?): Boolean = this === other || other is FixedDecimal && core == other.core
        override fun hashCode(): Int = core.hashCode()
        override fun toString(): String = core.toString()
        override fun toDouble(): Double = core.toDouble()
        override fun toFloat(): Float = core.toFloat()
        override fun toLong(): Long = core.toLong()
        override fun toInt(): Int = core.toInt()
        override fun toShort(): Short = core.toShort()
        override fun toByte(): Byte = core.toByte()
        private fun writeReplace(): Any = fixedDecimalSerializationFailure()
        private fun readResolve(): Any = fixedDecimalSerializationFailure()
        private fun writeObject(output: PluralObjectOutputStream): Unit { fixedDecimalSerializationFailure() }
        private fun readObject(input: PluralObjectInputStream): Unit { fixedDecimalSerializationFailure() }
        companion object {
            @JvmStatic fun createWithExponent(n: Double, v: Int, e: Int): FixedDecimal = FixedDecimal(FixedDecimalCore.createWithExponent(n, v, e))
            @JvmStatic fun decimals(n: Double): Int = FixedDecimalCore.decimals(n)
            @JvmStatic fun getOperand(text: String?): Operand = Operand.valueOf(text!!)
        }
    }
    open class DecimalQuantitySamplesRange(start: DecimalQuantity?, end: DecimalQuantity?) {
        @JvmField val start: DecimalQuantity
        @JvmField val end: DecimalQuantity
        init {
            require(start!!.getPluralOperand(Operand.v) == end!!.getPluralOperand(Operand.v)) {
                "Ranges must have the same number of visible decimals: $start~$end"
            }
            this.start = start
            this.end = end
        }
        override fun toString(): String = start.toExponentString() + if (end === start) "" else "~${end.toExponentString()}"
    }
    open class DecimalQuantitySamples internal constructor(private val core: PluralSamplesCore) {
        @JvmField val sampleType: SampleType = SampleType.entries[core.sampleType.ordinal]
        @JvmField val bounded: Boolean = core.bounded
        @JvmField val samples: Set<DecimalQuantitySamplesRange> = pluralReadOnlySet(core.samples.mapTo(linkedSetOf()) {
            val start = DecimalQuantity_DualStorageBCD.fromCore(it.start)
            DecimalQuantitySamplesRange(start, if (it.end === it.start) start else DecimalQuantity_DualStorageBCD.fromCore(it.end))
        })
        fun getSamples(): Set<DecimalQuantitySamplesRange> = samples
        fun addSamples(result: MutableCollection<Double>?): MutableCollection<Double>? {
            addSamples(result, null)
            return result
        }
        fun addDecimalQuantitySamples(result: MutableCollection<DecimalQuantity>?): MutableCollection<DecimalQuantity>? {
            addSamples(null, result)
            return result
        }
        fun addSamples(doubleResult: MutableCollection<Double>?, dqResult: MutableCollection<DecimalQuantity>?) {
            if ((doubleResult == null) == (dqResult == null)) return
            if (doubleResult != null) core.addSamples(doubleResult, null)
            else core.forEachQuantity { dqResult!!.add(DecimalQuantity_DualStorageBCD.fromCore(it)) }
        }
        fun getStartEndSamples(target: MutableSet<DecimalQuantity>?) {
            for (range in samples) { target!!.add(range.start); target.add(range.end) }
        }
        override fun toString(): String = core.toString()
        companion object {
            internal fun parse(source: String): DecimalQuantitySamples = DecimalQuantitySamples(PluralSamplesCore.parse(source))
        }
    }
    abstract class Factory protected constructor() {
        abstract fun forLocale(locale: ULocale?, type: PluralType?): PluralRules
        fun forLocale(locale: ULocale?): PluralRules = forLocale(locale, PluralType.CARDINAL)
        abstract fun getAvailableULocales(): Array<ULocale>
        abstract fun getFunctionalEquivalent(locale: ULocale?, isAvailable: BooleanArray?): ULocale
        abstract fun hasOverride(locale: ULocale?): Boolean
        companion object { @JvmStatic fun getDefaultFactory(): PluralRulesLoader = PluralRulesLoader.loader }
    }
    private val queries = PluralRuleSamplesCore(rules)
    private val sampleViews = HashMap<PluralSamplesCore, DecimalQuantitySamples>()
    private val sampleMonitor = createResourceCacheLock()
    private val keywords: Set<String> = pluralReadOnlyKeywords(rules.getKeywords())
    fun select(number: Double): String = select(FixedDecimal(number))
    fun select(number: Double, countVisibleFractionDigits: Int, fractionaldigits: Long): String = select(FixedDecimal(number, countVisibleFractionDigits, fractionaldigits))
    fun select(number: IFixedDecimal?): String {
        val value = number!!
        if (value.isInfinite() || value.isNaN()) return KEYWORD_OTHER
        return rules.select({ value.getPluralOperand(Operand.entries[it.ordinal]) })
    }
    fun matches(sample: FixedDecimal?, keyword: String?): Boolean = keyword != null && rules.select({ sample!!.getPluralOperand(Operand.entries[it.ordinal]) }, keyword)
    fun getKeywords(): Set<String> = keywords
    fun getRules(keyword: String?): String? = keyword?.let { rules.getRules(it) }
    fun getSamples(keyword: String?): Collection<Double>? = keyword?.let { queries.getSamples(it) }
    fun getSamples(keyword: String?, sampleType: SampleType?): Collection<Double>? = keyword?.let { queries.getSamples(it, sampleType?.core()) }
    fun getAllKeywordValues(keyword: String?): Collection<Double>? {
        if (keyword == null && !rules.hasExplicitBoundingInfo) throw NullPointerException()
        return keyword?.let { queries.getAllKeywordValues(it) }
    }
    fun getUniqueKeywordValue(keyword: String?): Double {
        if (keyword == null && !rules.hasExplicitBoundingInfo) throw NullPointerException()
        return keyword?.let { queries.getUniqueKeywordValue(it) } ?: NO_UNIQUE_VALUE
    }
    fun getDecimalSamples(keyword: String?, sampleType: SampleType?): DecimalQuantitySamples? {
        val samples = keyword?.let { rules.getDecimalSamples(it, sampleType?.core()) } ?: return null
        return sampleMonitor.withLock { sampleViews.getOrPut(samples) { DecimalQuantitySamples(samples) } }
    }
    fun getDecimalQuantitySamples(keyword: String?): Collection<DecimalQuantity>? = getDecimalQuantitySamples(keyword, SampleType.INTEGER)
    fun getDecimalQuantitySamples(keyword: String?, sampleType: SampleType?): Collection<DecimalQuantity>? =
        keyword?.let { queries.getDecimalQuantitySamples(it, sampleType?.core()) }?.publicView()
    fun getAllKeywordDecimalQuantityValues(keyword: String?): Collection<DecimalQuantity>? = getAllKeywordValues(keyword, SampleType.INTEGER)
    fun getAllKeywordValues(keyword: String?, type: SampleType?): Collection<DecimalQuantity>? =
        if (!isLimited(keyword, type)) null else getDecimalQuantitySamples(keyword, type)
    fun getUniqueKeywordDecimalQuantityValue(keyword: String?): DecimalQuantity {
        val values = getAllKeywordDecimalQuantityValues(keyword)
        return if (values?.size == 1) values.first() else NO_UNIQUE_VALUE_DECIMAL_QUANTITY
    }
    fun getKeywordStatus(keyword: String?, offset: Int, explicits: Set<DecimalQuantity>?, uniqueValue: Output<DecimalQuantity>?): KeywordStatus =
        getKeywordStatus(keyword, offset, explicits, uniqueValue, SampleType.INTEGER)
    fun getKeywordStatus(keyword: String?, offset: Int, explicits: Set<DecimalQuantity>?, uniqueValue: Output<DecimalQuantity>?, sampleType: SampleType?): KeywordStatus {
        uniqueValue?.value = null
        if (keyword !in keywords) return KeywordStatus.INVALID
        if (!isLimited(keyword, sampleType)) return KeywordStatus.UNBOUNDED
        val values = getDecimalQuantitySamples(keyword, sampleType)!!
        val originalSize = values.size
        if (originalSize > (explicits?.size ?: 0)) {
            if (originalSize == 1) {
                uniqueValue?.value = values.first()
                return KeywordStatus.UNIQUE
            }
            return KeywordStatus.BOUNDED
        }
        val remaining = values.toMutableList()
        for (explicit in explicits.orEmpty()) {
            val remove = DecimalQuantity_DualStorageBCD(explicit.toBigDecimal().subtract(BigDecimal(offset)))
            remaining.remove(remove)
        }
        if (remaining.isEmpty()) return KeywordStatus.SUPPRESSED
        if (remaining.size == 1) uniqueValue?.value = remaining.first()
        return if (originalSize == 1) KeywordStatus.UNIQUE else KeywordStatus.BOUNDED
    }
    fun isLimited(keyword: String?, sampleType: SampleType?): Boolean =
        if (rules.hasExplicitBoundingInfo) keyword?.let { rules.isLimited(it, sampleType?.core()) } ?: true else computeLimited(keyword, sampleType)
    fun computeLimited(keyword: String?, sampleType: SampleType?): Boolean = rules.computeLimited(keyword!!, sampleType?.core())
    override fun toString(): String = rules.toString()
    override fun equals(other: Any?): Boolean = other is PluralRules && toString() == other.toString()
    override fun hashCode(): Int = rules.hashCode()
    fun compareTo(other: PluralRules?): Int = toString().compareTo(other!!.toString())
    private fun writeReplace(): Any = pluralRulesSerialProxy(toString())
    private fun writeObject(output: PluralObjectOutputStream): Unit { fixedDecimalSerializationFailure() }
    private fun readObject(input: PluralObjectInputStream): Unit { fixedDecimalSerializationFailure() }

    companion object {
        const val KEYWORD_ZERO = "zero"
        const val KEYWORD_ONE = "one"
        const val KEYWORD_TWO = "two"
        const val KEYWORD_FEW = "few"
        const val KEYWORD_MANY = "many"
        const val KEYWORD_OTHER = "other"
        const val NO_UNIQUE_VALUE = PluralRuleSamplesCore.NO_UNIQUE_VALUE
        @JvmField val NO_UNIQUE_VALUE_DECIMAL_QUANTITY: DecimalQuantity = DecimalQuantity_DualStorageBCD.fromCore(PluralRuleSamplesCore.NO_UNIQUE_VALUE_DECIMAL_QUANTITY)
        @JvmField val DEFAULT = PluralRules(PluralRuleListCore.parse(""), StandardPluralRanges.DEFAULT)
        @JvmStatic fun parseDescription(description: String?): PluralRules = newInternal(description, null)
        @JvmStatic fun createRules(description: String?): PluralRules? = try { parseDescription(description) } catch (_: Exception) { null }
        @JvmStatic fun newInternal(description: String?, ranges: StandardPluralRanges?): PluralRules {
            val trimmed = description!!.trim { it <= ' ' }
            if (trimmed.isEmpty()) return DEFAULT
            val parsed = try { PluralRuleListCore.parse(trimmed) } catch (e: PluralConditionParseException) { throw ParseException(e.message, e.errorOffset) }
            return PluralRules(parsed, ranges)
        }
        internal fun fromLoaded(rules: PluralRuleListCore, ranges: StandardPluralRanges): PluralRules = PluralRules(rules, ranges)
        @JvmStatic fun forLocale(locale: ULocale?): PluralRules = Factory.getDefaultFactory().forLocale(locale)
        @JvmStatic fun forLocale(locale: ULocale?, type: PluralType?): PluralRules = Factory.getDefaultFactory().forLocale(locale, type)
        @JvmStatic fun getAvailableULocales(): Array<ULocale> = Factory.getDefaultFactory().getAvailableULocales()
        @JvmStatic fun getFunctionalEquivalent(locale: ULocale?, isAvailable: BooleanArray?): ULocale = Factory.getDefaultFactory().getFunctionalEquivalent(locale, isAvailable)
        private fun SampleType.core(): PluralCondition.SampleType = PluralCondition.SampleType.entries[ordinal]
    }
}

private fun pluralReadOnlyKeywords(values: Set<String>): Set<String> = pluralReadOnlySet(values)

private fun <T> pluralReadOnlySet(values: Set<T>): Set<T> = object : AbstractMutableSet<T>() {
    override val size get() = values.size
    override fun contains(element: T) = values.contains(element)
    override fun iterator(): MutableIterator<T> {
        val source = values.iterator()
        return object : MutableIterator<T> {
            override fun hasNext() = source.hasNext()
            override fun next() = source.next()
            override fun remove(): Nothing = throw UnsupportedOperationException()
        }
    }
    override fun add(element: T): Nothing = throw UnsupportedOperationException()
    override fun addAll(elements: Collection<T>): Nothing = throw UnsupportedOperationException()
    override fun remove(element: T): Nothing = throw UnsupportedOperationException()
    override fun removeAll(elements: Collection<T>): Nothing = throw UnsupportedOperationException()
    override fun retainAll(elements: Collection<T>): Nothing = throw UnsupportedOperationException()
    override fun clear(): Nothing = throw UnsupportedOperationException()
}

internal expect fun pluralRulesSerialProxy(description: String): Any
internal expect open class PluralObjectInputStream
internal expect open class PluralObjectOutputStream

private fun Collection<DecimalQuantityCore>.publicView(): Collection<DecimalQuantity> {
    val source = this
    // Stable element identities, and membership based on the quantities' current state.
    val values: Set<DecimalQuantity> = source.mapTo(linkedSetOf()) { DecimalQuantity_DualStorageBCD.fromCore(it) }
    return if (source is Set<*>) pluralReadOnlySet(values) else object : AbstractMutableCollection<DecimalQuantity>() {
        override val size get() = values.size
        override fun contains(element: DecimalQuantity) = values.contains(element)
        override fun iterator(): MutableIterator<DecimalQuantity> {
            val iterator = values.iterator()
            return object : MutableIterator<DecimalQuantity> {
                override fun hasNext() = iterator.hasNext()
                override fun next() = iterator.next()
                override fun remove(): Nothing = throw UnsupportedOperationException()
            }
        }
        override fun add(element: DecimalQuantity): Nothing = throw UnsupportedOperationException()
        override fun addAll(elements: Collection<DecimalQuantity>): Nothing = throw UnsupportedOperationException()
        override fun remove(element: DecimalQuantity): Nothing = throw UnsupportedOperationException()
        override fun removeAll(elements: Collection<DecimalQuantity>): Nothing = throw UnsupportedOperationException()
        override fun retainAll(elements: Collection<DecimalQuantity>): Nothing = throw UnsupportedOperationException()
        override fun clear(): Nothing = throw UnsupportedOperationException()
    }
}
