// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Portable counterpart of java.math.RoundingMode. */
expect enum class RoundingMode {
    UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY;
}
