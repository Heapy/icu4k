// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

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

/** Unicode plural rules. Formatted-number and decimal-quantity adapters are ported separately. */
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
    abstract class Factory protected constructor() {
        abstract fun forLocale(locale: ULocale?, type: PluralType?): PluralRules
        fun forLocale(locale: ULocale?): PluralRules = forLocale(locale, PluralType.CARDINAL)
        abstract fun getAvailableULocales(): Array<ULocale>
        abstract fun getFunctionalEquivalent(locale: ULocale?, isAvailable: BooleanArray?): ULocale
        abstract fun hasOverride(locale: ULocale?): Boolean
        companion object { @JvmStatic fun getDefaultFactory(): PluralRulesLoader = PluralRulesLoader.loader }
    }
    private val queries = PluralRuleSamplesCore(rules)
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

private fun pluralReadOnlyKeywords(values: Set<String>): Set<String> = object : AbstractMutableSet<String>() {
    override val size get() = values.size
    override fun contains(element: String) = values.contains(element)
    override fun iterator(): MutableIterator<String> {
        val source = values.iterator()
        return object : MutableIterator<String> {
            override fun hasNext() = source.hasNext()
            override fun next() = source.next()
            override fun remove(): Nothing = throw UnsupportedOperationException()
        }
    }
    override fun add(element: String): Nothing = throw UnsupportedOperationException()
    override fun addAll(elements: Collection<String>): Nothing = throw UnsupportedOperationException()
    override fun remove(element: String): Nothing = throw UnsupportedOperationException()
    override fun removeAll(elements: Collection<String>): Nothing = throw UnsupportedOperationException()
    override fun retainAll(elements: Collection<String>): Nothing = throw UnsupportedOperationException()
    override fun clear(): Nothing = throw UnsupportedOperationException()
}

internal expect fun pluralRulesSerialProxy(description: String): Any
internal expect open class PluralObjectInputStream
internal expect open class PluralObjectOutputStream
