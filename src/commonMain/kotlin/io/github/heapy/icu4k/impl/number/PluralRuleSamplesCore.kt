// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.number.PluralCondition.SampleType
import io.github.heapy.icu4k.impl.units.UnitDecimal
import io.github.heapy.icu4k.util.Output

/** Sample enumeration and keyword status from PluralRules, independent of locale loading. */
internal class PluralRuleSamplesCore(private val rules: PluralRuleListCore) {
    enum class KeywordStatus { INVALID, SUPPRESSED, UNIQUE, BOUNDED, UNBOUNDED }

    fun getDecimalQuantitySamples(keyword: String, type: SampleType = SampleType.INTEGER): Collection<DecimalQuantityCore>? {
        if (keyword !in rules.getKeywords()) return null
        val result = linkedSetOf<DecimalQuantityCore>()
        if (rules.hasExplicitBoundingInfo) {
            val samples = rules.getDecimalSamples(keyword, type)
            samples?.addSamples(null, result)
            return readOnly(result, samples == null)
        }
        val maxCount = if (rules.isLimited(keyword, type)) Int.MAX_VALUE else 20
        fun addSample(sample: DecimalQuantityCore): Boolean {
            if (rules.select(sample::getPluralOperand, sample.isInfinite(), sample.isNaN()) == keyword) {
                result.add(sample)
                // Source decrements a by-value parameter, so the limit is not cumulative.
                if (maxCount - 1 < 0) return false
            }
            return true
        }
        when (type) {
            SampleType.INTEGER -> {
                for (i in 0 until 200) if (!addSample(DecimalQuantityCore(i.toLong()))) break
                addSample(DecimalQuantityCore(1000000L))
            }
            SampleType.DECIMAL -> {
                for (i in 0 until 2000) {
                    val sample = DecimalQuantityCore(i.toLong()).apply { adjustMagnitude(-1) }
                    if (!addSample(sample)) break
                }
                addSample(DecimalQuantityCore.fromExponentString("1000000.0"))
            }
        }
        return if (result.isEmpty()) null else readOnly(result, true)
    }

    fun getSamples(keyword: String, type: SampleType = SampleType.INTEGER): Collection<Double>? =
        getDecimalQuantitySamples(keyword, type)?.mapTo(linkedSetOf()) { it.toDouble() }

    fun getAllKeywordValues(keyword: String, type: SampleType): Collection<DecimalQuantityCore>? =
        if (!rules.isLimited(keyword, type)) null else getDecimalQuantitySamples(keyword, type)

    fun getAllKeywordDecimalQuantityValues(keyword: String): Collection<DecimalQuantityCore>? =
        getAllKeywordValues(keyword, SampleType.INTEGER)

    fun getAllKeywordValues(keyword: String): Collection<Double>? =
        getAllKeywordDecimalQuantityValues(keyword)?.mapTo(linkedSetOf()) { it.toDouble() }

    fun getUniqueKeywordDecimalQuantityValue(keyword: String): DecimalQuantityCore {
        val values = getAllKeywordDecimalQuantityValues(keyword)
        return if (values?.size == 1) values.first() else NO_UNIQUE_VALUE_DECIMAL_QUANTITY
    }

    fun getUniqueKeywordValue(keyword: String): Double {
        val value = getUniqueKeywordDecimalQuantityValue(keyword)
        return if (value == NO_UNIQUE_VALUE_DECIMAL_QUANTITY) NO_UNIQUE_VALUE else value.toDouble()
    }

    fun getKeywordStatus(keyword: String, offset: Int, explicits: Set<DecimalQuantityCore>?,
                         uniqueValue: Output<DecimalQuantityCore>?, type: SampleType = SampleType.INTEGER): KeywordStatus {
        uniqueValue?.value = null
        if (keyword !in rules.getKeywords()) return KeywordStatus.INVALID
        if (!rules.isLimited(keyword, type)) return KeywordStatus.UNBOUNDED
        val values = getDecimalQuantitySamples(keyword, type)!!
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
            val remove = DecimalQuantityCore(explicit.toDecimal().subtract(UnitDecimal.valueOf(offset.toLong())))
            remaining.remove(remove)
        }
        if (remaining.isEmpty()) return KeywordStatus.SUPPRESSED
        if (remaining.size == 1) uniqueValue?.value = remaining.first()
        return if (originalSize == 1) KeywordStatus.UNIQUE else KeywordStatus.BOUNDED
    }

    companion object {
        const val NO_UNIQUE_VALUE = -0.00123456777
        val NO_UNIQUE_VALUE_DECIMAL_QUANTITY = DecimalQuantityCore(NO_UNIQUE_VALUE)

        private fun <T> readOnly(values: Set<T>, asSet: Boolean): Collection<T> {
            fun readOnlyIterator(): Iterator<T> {
                val delegate = values.iterator()
                return object : Iterator<T> {
                    override fun hasNext() = delegate.hasNext()
                    override fun next(): T = delegate.next()
                }
            }
            return if (asSet) object : AbstractSet<T>() {
                override val size get() = values.size
                override fun contains(element: T) = values.contains(element)
                override fun iterator(): Iterator<T> = readOnlyIterator()
            } else object : AbstractCollection<T>() {
                override val size get() = values.size
                override fun contains(element: T) = values.contains(element)
                override fun iterator(): Iterator<T> = readOnlyIterator()
            }
        }
    }
}
