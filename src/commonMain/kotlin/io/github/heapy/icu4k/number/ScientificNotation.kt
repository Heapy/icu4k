// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.ConstantAffixModifier
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.MicroProps
import io.github.heapy.icu4k.impl.number.MicroPropsGenerator
import io.github.heapy.icu4k.impl.number.Modifier
import io.github.heapy.icu4k.impl.number.MultiplierProducer
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.FormatField
import io.github.heapy.icu4k.text.NumberFormatField
import kotlin.jvm.JvmField
import kotlin.math.abs

/** Scientific/engineering scaling, rounding and localized exponent affixes. */
open class ScientificNotation internal constructor(@JvmField internal var engineeringInterval: Int,
    @JvmField internal var requireMinInt: Boolean, @JvmField internal var minExponentDigits: Int,
    @JvmField internal var exponentSignDisplay: SignDisplay?) : Notation() {
    open fun withMinExponentDigits(minExponentDigits: Int): ScientificNotation {
        require(minExponentDigits in 1..999) { "Integer digits must be between 1 and 999 (inclusive)" }
        return createCopy().also { it.minExponentDigits = minExponentDigits }
    }
    open fun withExponentSignDisplay(exponentSignDisplay: SignDisplay?): ScientificNotation =
        createCopy().also { it.exponentSignDisplay = exponentSignDisplay }
    internal open fun createCopy(): ScientificNotation = ScientificNotation(engineeringInterval, requireMinInt, minExponentDigits, exponentSignDisplay)
    internal fun withLocaleData(symbols: DecimalFormatSymbols?, build: Boolean, parent: MicroPropsGenerator?): MicroPropsGenerator =
        ScientificHandler(this, symbols, build, parent)

    private class ScientificHandler(val notation: ScientificNotation, val symbols: DecimalFormatSymbols?,
        safe: Boolean, val parent: MicroPropsGenerator?) : MicroPropsGenerator, MultiplierProducer, Modifier {
        val precomputedMods = if (safe) Array(25) { ScientificModifier(it - 12, this) } else null
        var exponent = 0
        override fun processQuantity(quantity: DecimalQuantity?): MicroProps {
            val micros = parent!!.processQuantity(quantity)
            assertScientificNotation { micros!!.rounder != null }
            if (quantity!!.isInfinite() || quantity.isNaN()) {
                micros!!.modInner = ConstantAffixModifier.EMPTY
                return micros
            }
            val exponent: Int
            if (quantity.isZeroish()) {
                if (notation.requireMinInt && micros!!.rounder is Precision.SignificantRounderImpl) {
                    (micros.rounder as Precision.SignificantRounderImpl).apply(quantity, notation.engineeringInterval)
                    exponent = 0
                } else {
                    micros!!.rounder!!.apply(quantity)
                    exponent = 0
                }
            } else exponent = -micros!!.rounder!!.chooseMultiplierAndApply(quantity, this)
            if (precomputedMods != null && exponent in -12..12) micros.modInner = precomputedMods[exponent + 12]
            else if (precomputedMods != null) micros.modInner = ScientificModifier(exponent, this)
            else { this.exponent = exponent; micros.modInner = this }
            quantity.adjustExponent(exponent)
            micros.rounder = null
            return micros
        }
        override fun getMultiplier(magnitude: Int): Int {
            val interval = notation.engineeringInterval
            val digitsShown = if (notation.requireMinInt) interval
                else if (interval <= 1) 1 else ((magnitude % interval + interval) % interval) + 1
            return digitsShown - magnitude - 1
        }
        override fun getPrefixLength(): Int = 0
        override fun getCodePointCount(): Int = 999
        override fun isStrong(): Boolean = true
        override fun containsField(field: FormatField?): Boolean { assertScientificNotation { false }; return false }
        override fun getParameters(): Modifier.Parameters? { assertScientificNotation { false }; return null }
        override fun strictEquals(other: Modifier?): Boolean { assertScientificNotation { false }; return false }
        override fun apply(output: FormattedStringBuilder?, leftIndex: Int, rightIndex: Int): Int = doApply(exponent, output, rightIndex)
        fun doApply(exponent: Int, output: FormattedStringBuilder?, rightIndex: Int): Int {
            var i = rightIndex
            // Preserve getter evaluation before a null output receiver fails.
            val separator = symbols!!.getExponentSeparator()
            i += output!!.insert(i, separator!!, NumberFormatField.EXPONENT_SYMBOL)
            if (exponent < 0 && notation.exponentSignDisplay != SignDisplay.NEVER) {
                i += output.insert(i, symbols.getMinusSignString(), NumberFormatField.EXPONENT_SIGN)
            } else if (exponent >= 0 && notation.exponentSignDisplay == SignDisplay.ALWAYS) {
                i += output.insert(i, symbols.getPlusSignString(), NumberFormatField.EXPONENT_SIGN)
            }
            var disp = abs(exponent)
            var j = 0
            while (j < notation.minExponentDigits || disp > 0) {
                val digit = symbols.getDigitStringsLocal()[disp % 10]
                // ICU's insertion index counts digits, not code units in each localized digit string.
                i += output.insert(i - j, digit, NumberFormatField.EXPONENT)
                j++; disp /= 10
            }
            return i - rightIndex
        }
    }
    private class ScientificModifier(val exponent: Int, val handler: ScientificHandler) : Modifier {
        override fun apply(output: FormattedStringBuilder?, leftIndex: Int, rightIndex: Int): Int = handler.doApply(exponent, output, rightIndex)
        override fun getPrefixLength(): Int = 0
        override fun getCodePointCount(): Int = 999
        override fun isStrong(): Boolean = true
        override fun containsField(field: FormatField?): Boolean { assertScientificNotation { false }; return false }
        override fun getParameters(): Modifier.Parameters? = null
        override fun strictEquals(other: Modifier?): Boolean = other is ScientificModifier && exponent == other.exponent
    }
}
internal expect fun assertScientificNotation(condition: () -> Boolean)
