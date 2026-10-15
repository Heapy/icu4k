// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.number.MathContextCore

actual class MathContext private constructor(private val core: MathContextCore) {
    actual constructor(precision: Int) : this(MathContextCore(precision, RoundingMode.HALF_UP))
    actual constructor(precision: Int, roundingMode: RoundingMode?) : this(MathContextCore(precision, roundingMode))
    actual constructor(value: String?) : this(MathContextCore.parse(value))
    actual fun getPrecision(): Int = core.precision
    actual fun getRoundingMode(): RoundingMode = core.roundingMode
    actual override fun equals(other: Any?): Boolean = other is MathContext && core == other.core
    actual override fun hashCode(): Int = core.hashCode()
    actual override fun toString(): String = core.toString()
}

internal actual fun standardMathContext(index: Int): MathContext = when (index) {
    0 -> MathContext(0, RoundingMode.HALF_UP)
    1 -> MathContext(7, RoundingMode.HALF_EVEN)
    2 -> MathContext(16, RoundingMode.HALF_EVEN)
    3 -> MathContext(34, RoundingMode.HALF_EVEN)
    else -> throw AssertionError()
}
