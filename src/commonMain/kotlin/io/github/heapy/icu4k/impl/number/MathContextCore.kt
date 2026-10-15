// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.util.RoundingMode

/** Portable value/validation behavior for the standard decimal arithmetic context. */
internal class MathContextCore(val precision: Int, rounding: RoundingMode?) {
    val roundingMode: RoundingMode
    init {
        if (precision < 0) throw IllegalArgumentException("Digits < 0")
        roundingMode = rounding ?: throw NullPointerException("null RoundingMode")
    }
    override fun equals(other: Any?): Boolean = other is MathContextCore && precision == other.precision && roundingMode == other.roundingMode
    override fun hashCode(): Int = precision + 59 * roundingMode.hashCode()
    override fun toString(): String = "precision=$precision roundingMode=${roundingMode.name}"
    companion object {
        fun parse(text: String?): MathContextCore {
            if (text == null) throw NullPointerException("null String")
            val parsed = try {
                val separator = text.indexOf(' ')
                if (!text.startsWith("precision=") || !text.startsWith("roundingMode=", separator + 1)) throw IllegalArgumentException()
                val precision = DecimalNumberParsing.int(text.substring(10, separator))
                val name = text.substring(separator + 14)
                precision to enumValueOf<RoundingMode>(name)
            } catch (_: RuntimeException) { throw IllegalArgumentException("bad string format") }
            return MathContextCore(parsed.first, parsed.second)
        }
    }
}
