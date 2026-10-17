// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2008-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.Grego
import kotlin.math.abs

/** JDK calendar wall-time normalization policy, separated from JVM interoperation. */
internal class JavaTimeZoneOffsetCore(private val owner: Owner) {
    interface Owner {
        fun <T> withCalendarLock(block: () -> T): T
        fun clear()
        fun setDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int)
        fun set(field: Int, value: Int)
        fun get(field: Int): Int
        fun getTimeInMillis(): Long
        fun setTimeInMillis(value: Long)
    }
    fun getOffset(date: Long, local: Boolean, offsets: IntArray?) = owner.withCalendarLock {
        if (local) {
            val fields = Grego.timeToFields(date, IntArray(6))
            var time = fields[5]
            val millisecond = time % 1000; time /= 1000
            val second = time % 60; time /= 60
            val minute = time % 60; val hour = time / 60
            owner.clear(); owner.setDateTime(fields[0], fields[1], fields[2], hour, minute, second)
            owner.set(14, millisecond)
            val doy = owner.get(6); val h = owner.get(11); val m = owner.get(12); val s = owner.get(13); val ms = owner.get(14)
            if (fields[4] != doy || hour != h || minute != m || second != s || millisecond != ms) {
                val dayDelta = if (abs(doy - fields[4]) > 1) 1 else doy - fields[4]
                val delta = ((((dayDelta * 24) + h - hour) * 60 + m - minute) * 60 + s - second) * 1000 + ms - millisecond
                owner.setTimeInMillis(owner.getTimeInMillis() - delta - 1)
            }
        } else owner.setTimeInMillis(date)
        // Preserve partial writes and virtual reads before a short-array failure.
        val raw = owner.get(15)
        offsets!![0] = raw
        val dst = owner.get(16)
        offsets[1] = dst
    }
}
