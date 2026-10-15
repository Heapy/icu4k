// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Portable precision/rounding context; the JVM type is java.math.MathContext. */
expect class MathContext {
    constructor(precision: Int)
    constructor(precision: Int, roundingMode: RoundingMode?)
    constructor(value: String?)
    fun getPrecision(): Int
    fun getRoundingMode(): RoundingMode
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}
