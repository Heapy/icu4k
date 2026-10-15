// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.number.DecimalQuantityCore.Operand
import io.github.heapy.icu4k.impl.number.PluralCondition.SampleType
import io.github.heapy.icu4k.impl.units.UnitDecimal
import kotlin.math.floor

/** Java Pattern.split(input, 0), using the source's ASCII whitespace class. */
internal fun splitPluralText(input: String, separator: String): List<String> {
    if (input.isEmpty()) return listOf("")
    return Regex("[\\t-\\r ]*${Regex.escape(separator)}[\\t-\\r ]*").split(input).dropLastWhile { it.isEmpty() }
}

internal class PluralSampleRangeCore(val start: DecimalQuantityCore, val end: DecimalQuantityCore) {
    init {
        require(start.getPluralOperand(Operand.v) == end.getPluralOperand(Operand.v)) {
            "Ranges must have the same number of visible decimals: $start~$end"
        }
    }
    // Source tests identity, so two separate equal-valued endpoints retain '~'.
    override fun toString() = start.toExponentString() + if (start === end) "" else "~${end.toExponentString()}"
}

internal class PluralSamplesCore private constructor(
    val sampleType: SampleType, private val ranges: List<PluralSampleRangeCore>, val bounded: Boolean
) {
    // A new abstract view prevents callers from mutating the source's unmodifiable set.
    val samples: Set<PluralSampleRangeCore> = object : AbstractSet<PluralSampleRangeCore>() {
        override val size: Int get() = ranges.size
        override fun iterator(): Iterator<PluralSampleRangeCore> = object : Iterator<PluralSampleRangeCore> {
            private var index = 0
            override fun hasNext() = index < ranges.size
            override fun next(): PluralSampleRangeCore {
                if (!hasNext()) throw NoSuchElementException()
                return ranges[index++]
            }
        }
    }
    fun addSamples(doubleResult: MutableCollection<Double>?, quantityResult: MutableCollection<DecimalQuantityCore>?) {
        if ((doubleResult == null) == (quantityResult == null)) return
        forEachQuantity { dq ->
            if (doubleResult != null) {
                val value = dq.toDouble()
                // Source's double API excludes integer-valued samples with visible zeros.
                if (value != floor(value) && dq.getPluralOperand(Operand.v) > 0) doubleResult.add(value)
            } else quantityResult!!.add(dq)
        }
    }
    // Invoke before incrementing: a caller's collection may mutate the supplied quantity.
    internal fun forEachQuantity(action: (DecimalQuantityCore) -> Unit) {
        for (range in ranges) {
            val lower = range.start.getLowerDisplayMagnitude()
            val exponent = range.start.getExponent()
            val increment = UnitDecimal.ONE.movePointRight(lower + exponent)
            var dq = range.start.createCopy()
            while (dq.toDouble() <= range.end.toDouble()) {
                action(dq)
                dq = DecimalQuantityCore(dq.toDecimal().add(increment)).apply {
                    setMinFraction(-lower)
                    adjustMagnitude(-exponent)
                    adjustExponent(exponent)
                }
            }
        }
    }
    fun getStartEndSamples(target: MutableSet<DecimalQuantityCore>) {
        for (range in ranges) { target.add(range.start); target.add(range.end) }
    }
    override fun toString(): String = buildString {
        append('@'); append(sampleType.name.lowercase())
        for ((index, range) in ranges.withIndex()) { if (index > 0) append(','); append(' '); append(range) }
        if (!bounded) append(", …")
    }
    companion object {
        fun parse(source: String): PluralSamplesCore {
            val type = when {
                source.startsWith("integer") -> SampleType.INTEGER
                source.startsWith("decimal") -> SampleType.DECIMAL
                else -> throw IllegalArgumentException("Samples must start with 'integer' or 'decimal'")
            }
            var bounded = true
            var haveBound = false
            val ranges = mutableListOf<PluralSampleRangeCore>()
            for (range in splitPluralText(source.substring(7).trim { it <= ' ' }, ",")) {
                if (range == "…" || range == "...") { bounded = false; haveBound = true; continue }
                require(!haveBound) { "Can only have … at the end of samples: $range" }
                val parts = splitPluralText(range, "~")
                when (parts.size) {
                    1 -> {
                        val sample = DecimalQuantityCore.fromExponentString(parts[0]); checkDecimal(type, sample)
                        ranges.add(PluralSampleRangeCore(sample, sample))
                    }
                    2 -> {
                        val start = DecimalQuantityCore.fromExponentString(parts[0])
                        val end = DecimalQuantityCore.fromExponentString(parts[1])
                        checkDecimal(type, start); checkDecimal(type, end)
                        ranges.add(PluralSampleRangeCore(start, end))
                    }
                    else -> throw IllegalArgumentException("Ill-formed number range: $range")
                }
            }
            return PluralSamplesCore(type, ranges, bounded)
        }
        private fun checkDecimal(type: SampleType, sample: DecimalQuantityCore) {
            val v = sample.getPluralOperand(Operand.v)
            require(!((type == SampleType.INTEGER && v != 0.0) || (type == SampleType.DECIMAL && v == 0.0 && sample.getPluralOperand(Operand.e) == 0.0))) {
                "Ill-formed number range: $sample"
            }
        }
    }
}
