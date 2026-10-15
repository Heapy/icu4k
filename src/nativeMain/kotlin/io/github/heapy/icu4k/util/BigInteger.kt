// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.number.BigIntegerCore

actual open class BigInteger private constructor(private val core: BigIntegerCore) : Number(), Comparable<BigInteger> {
    actual constructor(value: String?) : this(BigIntegerCore(value))
    actual constructor(value: String?, radix: Int) : this(BigIntegerCore(value, radix))
    actual open fun signum(): Int = core.sign
    actual open fun toString(radix: Int): String = core.toString(radix)
    actual override fun compareTo(other: BigInteger): Int = core.compareTo(other.core)
    actual override fun equals(other: Any?): Boolean = other is BigInteger && core == other.core
    actual override fun hashCode(): Int = core.hashCode()
    actual override fun toString(): String = core.toString()
    actual override fun toDouble(): Double = core.toDouble()
    actual override fun toFloat(): Float = core.toFloat()
    actual override fun toLong(): Long = core.toLong()
    actual override fun toInt(): Int = core.toInt()
    actual override fun toShort(): Short = toInt().toShort()
    actual override fun toByte(): Byte = toInt().toByte()
}
