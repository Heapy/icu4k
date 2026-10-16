// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/**
 * Mutable epoch-millisecond boundary for ICU date/time APIs. JVM aliases java.util.Date.
 * The portable contract covers time values, ordering, equality and cloning; JDK calendar
 * constructors, deprecated field accessors and host-local debug formatting are JVM APIs.
 */
expect open class Date : Comparable<Date>, ICUSerializable {
    constructor()
    constructor(time: Long)
    open fun getTime(): Long
    open fun setTime(time: Long)
    open fun before(whenDate: Date?): Boolean
    open fun after(whenDate: Date?): Boolean
    override fun compareTo(other: Date): Int
    public open fun clone(): Any
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}
