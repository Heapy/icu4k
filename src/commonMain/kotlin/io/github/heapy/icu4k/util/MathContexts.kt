// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Common access to the standard contexts, including the canonical JVM instances. */
object MathContexts {
    @JvmField val UNLIMITED = standardMathContext(0)
    @JvmField val DECIMAL32 = standardMathContext(1)
    @JvmField val DECIMAL64 = standardMathContext(2)
    @JvmField val DECIMAL128 = standardMathContext(3)
}

object RoundingModes {
    @JvmStatic fun valueOf(mode: Int): RoundingMode = RoundingMode.entries.getOrNull(mode)
        ?: throw IllegalArgumentException("argument out of range")
}

internal expect fun standardMathContext(index: Int): MathContext
