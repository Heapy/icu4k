// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.time.Clock

actual open class Date actual constructor(private var time: Long) : ICUCloneableBase(), Comparable<Date> {
    actual constructor() : this(Clock.System.now().toEpochMilliseconds())
    actual open fun getTime(): Long = time
    actual open fun setTime(time: Long) { this.time = time }
    actual open fun before(whenDate: Date?): Boolean = getTime() < whenDate!!.getTime()
    actual open fun after(whenDate: Date?): Boolean = getTime() > whenDate!!.getTime()
    actual override fun compareTo(other: Date): Int = getTime().compareTo(other.getTime())
    actual public override fun clone(): Any = cloneHost() ?: Date(time)
    actual override fun equals(other: Any?): Boolean = other is Date && getTime() == other.getTime()
    actual override fun hashCode(): Int { val t = getTime(); return (t xor (t ushr 32)).toInt() }
}
