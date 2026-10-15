// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.UResource
import io.github.heapy.icu4k.text.CompactStyle
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.ICUException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Compact notation patterns and their decimal magnitude multipliers. */
class CompactData : MultiplierProducer {
    enum class CompactType { DECIMAL, CURRENCY }

    private val patterns = arrayOfNulls<String>(21 * StandardPlural.COUNT)
    private val multipliers = ByteArray(21)
    private var largestMagnitude: Byte = 0
    private var isEmpty = true

    fun populate(locale: ULocale?, nsName: String?, compactStyle: CompactStyle?, compactType: CompactType?) {
        assertCompactData { isEmpty }
        val sink = CompactDataSink(this)
        val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
        val nsIsLatn = nsName!!.equals("latn")
        val compactIsShort = compactStyle == CompactStyle.SHORT
        fun load(ns: String, style: CompactStyle?) {
            val key = "NumberElements/$ns/" +
                (if (style == CompactStyle.SHORT) "patternsShort" else "patternsLong") +
                (if (compactType == CompactType.DECIMAL) "/decimalFormat" else "/currencyFormat")
            bundle.getAllItemsWithFallbackNoFail(key, sink)
        }
        load(nsName, compactStyle)
        if (isEmpty && !nsIsLatn) load("latn", compactStyle)
        if (isEmpty && !compactIsShort) load(nsName, CompactStyle.SHORT)
        if (isEmpty && !nsIsLatn && !compactIsShort) load("latn", CompactStyle.SHORT)
        if (isEmpty) throw ICUException("Could not load compact decimal data for locale $locale")
    }

    fun populate(powersToPluralsToPatterns: Map<out String?, Map<out String?, String?>?>?) {
        assertCompactData { isEmpty }
        for ((power, variants) in powersToPluralsToPatterns!!) {
            val magnitude = (power!!.length - 1).toByte().toInt()
            for ((keyword, value) in variants!!) {
                val plural = StandardPlural.fromString(keyword!!.toString())
                val pattern = value!!.toString()
                patterns[index(magnitude, plural)] = pattern
                val zeros = countZeros(pattern)
                if (zeros > 0) {
                    multipliers[magnitude] = (zeros - magnitude - 1).toByte()
                    if (magnitude > largestMagnitude) largestMagnitude = magnitude.toByte()
                    isEmpty = false
                }
            }
        }
    }

    override fun getMultiplier(magnitude: Int): Int {
        if (magnitude < 0) return 0
        return multipliers[minOf(magnitude, largestMagnitude.toInt())].toInt()
    }

    fun getPattern(magnitude: Int, rules: PluralRules?, dq: DecimalQuantity?): String? {
        if (magnitude < 0) return null
        val mag = minOf(magnitude, largestMagnitude.toInt())
        if (dq!!.isHasIntegerValue()) {
            val integer = dq.toLong(true)
            val exact = when (integer) {
                0L -> patterns[index(mag, StandardPlural.EQ_0)]
                1L -> patterns[index(mag, StandardPlural.EQ_1)]
                else -> null
            }
            if (exact != null) return exact
        }
        val plural = dq.getStandardPlural(rules)
        var pattern = patterns[index(mag, plural)]
        if (pattern == null && plural != StandardPlural.OTHER) pattern = patterns[index(mag, StandardPlural.OTHER)]
        return if (pattern === USE_FALLBACK) null else pattern
    }

    @Suppress("UNCHECKED_CAST")
    fun getUniquePatterns(output: MutableSet<String>?) {
        assertCompactData { output!!.isEmpty() }
        val nullableOutput = output!! as MutableSet<String?>
        nullableOutput.addAll(compactPatternList(patterns))
        nullableOutput.remove(USE_FALLBACK)
        nullableOutput.remove(null)
    }

    private class CompactDataSink(val data: CompactData) : UResource.Sink() {
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val powers = value.getTable()
            var i = 0
            while (powers.getKeyAndValue(i++, key, value)) {
                val magnitude = (key.length - 1).toByte().toInt()
                if (magnitude >= 20) continue
                var multiplier = data.multipliers[magnitude]
                val variants = value.getTable()
                var j = 0
                while (variants.getKeyAndValue(j++, key, value)) {
                    val plural = StandardPlural.fromString(key.toString())
                    val index = index(magnitude, plural)
                    if (data.patterns[index] != null) continue
                    var pattern = value.toString()
                    if (pattern == "0") pattern = USE_FALLBACK
                    data.patterns[index] = pattern
                    if (multiplier.toInt() == 0) {
                        val zeros = countZeros(pattern)
                        if (zeros > 0) multiplier = (zeros - magnitude - 1).toByte()
                    }
                }
                if (data.multipliers[magnitude].toInt() == 0) {
                    data.multipliers[magnitude] = multiplier
                    if (magnitude > data.largestMagnitude) data.largestMagnitude = magnitude.toByte()
                    data.isEmpty = false
                } else {
                    assertCompactData { data.multipliers[magnitude] == multiplier }
                }
            }
        }
    }

    private companion object {
        const val USE_FALLBACK = "<USE FALLBACK>"
        fun index(magnitude: Int, plural: StandardPlural) = magnitude * StandardPlural.COUNT + plural.ordinal
        fun countZeros(pattern: String): Int {
            var zeros = 0
            for (char in pattern) {
                if (char == '0') zeros++ else if (zeros > 0) break
            }
            return zeros
        }
    }
}

internal expect fun assertCompactData(condition: () -> Boolean)
internal expect fun compactPatternList(patterns: Array<String?>): MutableList<String?>

internal class CompactPatternList(private val patterns: Array<String?>) : AbstractMutableList<String?>(), RandomAccess {
    override val size: Int get() = patterns.size
    override fun get(index: Int): String? = patterns[index]
    override fun set(index: Int, element: String?): String? = patterns[index].also { patterns[index] = element }
    override fun add(index: Int, element: String?): Unit = throw UnsupportedOperationException()
    override fun removeAt(index: Int): String? = throw UnsupportedOperationException()
}
