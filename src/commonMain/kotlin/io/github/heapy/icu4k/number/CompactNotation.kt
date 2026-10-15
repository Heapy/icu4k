// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.CompactData
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.MicroProps
import io.github.heapy.icu4k.impl.number.MicroPropsGenerator
import io.github.heapy.icu4k.impl.number.MutablePatternModifier
import io.github.heapy.icu4k.impl.number.PatternStringParser
import io.github.heapy.icu4k.text.CompactStyle
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Compact notation configuration and quantity-dependent affix selection. */
open class CompactNotation : Notation {
    @JvmField internal val compactStyle: CompactStyle?
    @JvmField internal val compactCustomData: Map<out String?, Map<out String?, String?>?>?

    internal constructor(compactStyle: CompactStyle?) {
        this.compactStyle = compactStyle
        compactCustomData = null
    }
    internal constructor(compactCustomData: Map<out String?, Map<out String?, String?>?>?) {
        compactStyle = null
        this.compactCustomData = compactCustomData
    }

    internal fun withLocaleData(locale: ULocale?, nsName: String?, compactType: CompactData.CompactType?,
        rules: PluralRules?, buildReference: MutablePatternModifier?, safe: Boolean,
        parent: MicroPropsGenerator?): MicroPropsGenerator =
        CompactHandler(this, locale, nsName, compactType, rules, buildReference, safe, parent)

    private class CompactHandler(notation: CompactNotation, locale: ULocale?, nsName: String?,
        compactType: CompactData.CompactType?, val rules: PluralRules?, buildReference: MutablePatternModifier?,
        safe: Boolean, val parent: MicroPropsGenerator?) : MicroPropsGenerator {
        val data = CompactData()
        val precomputedMods: MutableMap<String, MutablePatternModifier.ImmutablePatternModifier>?
        val unsafePatternModifier: MutablePatternModifier?

        init {
            if (notation.compactStyle != null) data.populate(locale, nsName, notation.compactStyle, compactType)
            else data.populate(notation.compactCustomData)
            if (safe) {
                precomputedMods = HashMap()
                val allPatterns = compactPatternSet()
                data.getUniquePatterns(allPatterns)
                for (pattern in allPatterns) {
                    val info = PatternStringParser.parseToPatternInfo(pattern)
                    buildReference!!.setPatternInfo(info, NumberFormatField.COMPACT)
                    precomputedMods[pattern] = buildReference.createImmutable()
                }
                unsafePatternModifier = null
            } else {
                precomputedMods = null
                unsafePatternModifier = buildReference
            }
        }

        override fun processQuantity(quantity: DecimalQuantity?): MicroProps {
            val micros = parent!!.processQuantity(quantity)
            assertCompactNotation { micros!!.rounder != null }
            var magnitude: Int
            var multiplier = 0
            if (quantity!!.isZeroish()) {
                magnitude = 0
                micros!!.rounder!!.apply(quantity)
            } else {
                multiplier = micros!!.rounder!!.chooseMultiplierAndApply(quantity, data)
                magnitude = if (quantity.isZeroish()) 0 else quantity.getMagnitude()
                magnitude -= multiplier
            }
            val pattern = data.getPattern(magnitude, rules, quantity)
            if (pattern != null && precomputedMods != null) {
                precomputedMods[pattern]!!.applyToMicros(micros, quantity)
            } else if (pattern != null) {
                val info = PatternStringParser.parseToPatternInfo(pattern)
                unsafePatternModifier!!.setPatternInfo(info, NumberFormatField.COMPACT)
                unsafePatternModifier.setNumberProperties(quantity.signum(), null)
                micros!!.modMiddle = unsafePatternModifier
            }
            quantity.adjustExponent(-multiplier)
            micros!!.rounder = null
            return micros
        }
    }

    companion object {
        /** Internal ICU custom compact data API; retains the supplied map by reference. */
        @JvmStatic fun forCustomData(compactCustomData: Map<out String?, Map<out String?, String?>?>?): CompactNotation =
            CompactNotation(compactCustomData)
    }
}
internal expect fun assertCompactNotation(condition: () -> Boolean)
internal expect fun compactPatternSet(): MutableSet<String>
