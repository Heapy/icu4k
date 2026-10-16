// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** Calendar.add, including wall-time preservation across offset transitions. */
internal class CalendarAddCore(private val owner: Owner) {
    interface Owner {
        fun get(field: Int): Int
        fun set(field: Int, value: Int)
        fun pinField(field: Int)
        fun isEra0CountingBackward(): Boolean
        fun isLenient(): Boolean
        fun setLenient(value: Boolean)
        fun complete()
        fun fieldName(field: Int): String?
        fun getTimeInMillis(): Long
        fun setTimeInMillis(value: Long)
        fun storedTimeInMillis(): Long
        fun storedSkippedWallTime(): Int
        fun getImmediatePreviousZoneTransition(time: Long): Long?
    }

    fun add(field: Int, amount: Int) {
        if (amount == 0) return
        var delta = amount.toLong()
        var keepWall = true
        when (field) {
            0 -> { owner.set(field, owner.get(field) + amount); owner.pinField(0); return }
            1, 17, 19, 2, 23 -> {
                var adjusted = amount
                if ((field == 1 || field == 17) && owner.get(0) == 0 && owner.isEra0CountingBackward()) adjusted = -adjusted
                val oldLenient = owner.isLenient()
                owner.setLenient(true)
                owner.set(field, owner.get(field) + adjusted)
                owner.pinField(5)
                if (!oldLenient) { owner.complete(); owner.setLenient(oldLenient) }
                return
            }
            3, 4, 8 -> delta *= 604800000L
            9 -> delta *= 43200000L
            5, 6, 7, 18, 20 -> delta *= 86400000L
            10, 11 -> { delta *= 3600000L; keepWall = false }
            12 -> { delta *= 60000L; keepWall = false }
            13 -> { delta *= 1000L; keepWall = false }
            14, 21 -> keepWall = false
            else -> throw IllegalArgumentException("Calendar.add(${owner.fieldName(field)}) not supported")
        }
        var previousOffset = 0
        var previousWall = 0
        if (keepWall) {
            previousOffset = owner.get(16) + owner.get(15)
            previousWall = owner.get(21)
        }
        owner.setTimeInMillis(owner.getTimeInMillis() + delta)
        if (keepWall) {
            var newWall = owner.get(21)
            if (newWall != previousWall) {
                val time = owner.storedTimeInMillis()
                val newOffset = owner.get(16) + owner.get(15)
                if (newOffset != previousOffset) {
                    val adjustment = (previousOffset - newOffset).toLong() % 86400000L
                    if (adjustment != 0L) {
                        owner.setTimeInMillis(time + adjustment)
                        newWall = owner.get(21)
                    }
                    if (newWall != previousWall) when (owner.storedSkippedWallTime()) {
                        1 -> if (adjustment > 0) owner.setTimeInMillis(time)
                        0 -> if (adjustment < 0) owner.setTimeInMillis(time)
                        2 -> {
                            val before = if (adjustment > 0) owner.storedTimeInMillis() else time
                            val transition = owner.getImmediatePreviousZoneTransition(before)
                                ?: throw RuntimeException("Could not locate a time zone transition before $before")
                            owner.setTimeInMillis(transition)
                        }
                    }
                }
            }
        }
    }
}
