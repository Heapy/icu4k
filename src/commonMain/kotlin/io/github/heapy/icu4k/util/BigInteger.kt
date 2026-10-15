// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("EXPECT_ACTUAL_IR_INCOMPATIBILITY")
package io.github.heapy.icu4k.util

/** Arbitrary integer interchange used by ICU APIs. JVM aliases java.math.BigInteger.
 * The Common contract exposes construction, radix output, comparison and Number conversions.
 */
expect open class BigInteger : Number, Comparable<BigInteger> {
    constructor(value: String?)
    constructor(value: String?, radix: Int)
    open fun signum(): Int
    open fun toString(radix: Int): String
    override fun compareTo(other: BigInteger): Int
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
    override fun toDouble(): Double
    override fun toFloat(): Float
    override fun toLong(): Long
    override fun toInt(): Int
    override fun toShort(): Short
    override fun toByte(): Byte
}
