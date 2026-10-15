// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

actual typealias MathContext = java.math.MathContext

internal actual fun standardMathContext(index: Int): MathContext = when (index) {
    0 -> MathContext.UNLIMITED
    1 -> MathContext.DECIMAL32
    2 -> MathContext.DECIMAL64
    3 -> MathContext.DECIMAL128
    else -> throw AssertionError()
}
