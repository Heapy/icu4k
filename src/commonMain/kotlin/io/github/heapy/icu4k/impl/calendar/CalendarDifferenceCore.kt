// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.util.Date

/** Calendar.fieldDifference's exponential bracketing and binary search. */
internal class CalendarDifferenceCore(private val owner: Owner) {
    interface Owner {
        fun getTimeInMillis(): Long
        fun setTimeInMillis(value: Long)
        fun add(field: Int, amount: Int)
    }

    fun fieldDifference(whenDate: Date?, field: Int): Int {
        var min = 0
        val start = owner.getTimeInMillis()
        val target = whenDate!!.getTime()
        if (start < target) {
            var max = 1
            while (true) {
                owner.setTimeInMillis(start)
                owner.add(field, max)
                val time = owner.getTimeInMillis()
                if (time == target) return max
                else if (time > target) break
                else if (max < Int.MAX_VALUE) {
                    min = max
                    max = max shl 1
                    if (max < 0) max = Int.MAX_VALUE
                } else throw RuntimeException()
            }
            while (max - min > 1) {
                val middle = min + (max - min) / 2
                owner.setTimeInMillis(start)
                owner.add(field, middle)
                val time = owner.getTimeInMillis()
                if (time == target) return middle
                else if (time > target) max = middle
                else min = middle
            }
        } else if (start > target) {
            var max = -1
            while (true) {
                owner.setTimeInMillis(start)
                owner.add(field, max)
                val time = owner.getTimeInMillis()
                if (time == target) return max
                else if (time < target) break
                else {
                    min = max
                    max = max shl 1
                    if (max == 0) throw RuntimeException()
                }
            }
            while (min - max > 1) {
                val middle = min + (max - min) / 2
                owner.setTimeInMillis(start)
                owner.add(field, middle)
                val time = owner.getTimeInMillis()
                if (time == target) return middle
                else if (time < target) max = middle
                else min = middle
            }
        }
        owner.setTimeInMillis(start)
        owner.add(field, min)
        return min
    }
}
