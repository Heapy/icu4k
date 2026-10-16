// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import kotlin.math.abs

/** Calendar's wall-time conversion, timezone disambiguation and nearby-transition search. */
internal class CalendarTimeCore<Z>(private val stamps: ByteArray, private val owner: Owner<Z>, private val assertionsEnabled: Boolean) {
    fun interface Transition { fun getTime(): Long }
    interface Owner<Z> {
        fun isLenient(): Boolean
        fun getLenientFlag(): Boolean
        fun validateFields()
        fun computeJulianDay(): Int
        fun newestStamp(first: Int, last: Int, best: Int): Int
        fun internalGet(field: Int): Int
        fun computeMillisInDay(): Int
        fun computeMillisInDayLong(): Long
        fun computeZoneOffset(millis: Long, millisInDay: Long): Int
        fun setTime(time: Long)
        /** Read Calendar's stored zone, bypassing its virtual getTimeZone(). */
        fun getZone(): Z?
        fun getSkippedWallTime(): Int
        fun getRepeatedWallTime(): Int
        fun isBasicTimeZone(zone: Z?): Boolean
        fun getOffsetFromLocal(zone: Z?, wall: Long, nonExisting: Int, duplicated: Int, offsets: IntArray)
        fun getOffset(zone: Z?, date: Long, local: Boolean, offsets: IntArray)
        fun getTotalOffset(zone: Z?, date: Long): Int
        fun getPreviousTransition(zone: Z?, base: Long, inclusive: Boolean): Transition?
    }

    fun computeTime() {
        if (!owner.isLenient()) owner.validateFields()
        val day = owner.computeJulianDay()
        val millis = CalendarDateCore.julianDayToMillis(day)
        val millisInDay: Long
        if (stamp(21) >= 2 && owner.newestStamp(9,14,0) <= stamp(21)) {
            millisInDay = owner.internalGet(21).toLong()
        } else {
            var hour = abs(owner.internalGet(11))
            hour = maxOf(hour,abs(owner.internalGet(10)))
            millisInDay = if (hour > 548) owner.computeMillisInDayLong() else owner.computeMillisInDay().toLong()
        }
        if (stamp(15) >= 2 || stamp(16) >= 2) {
            owner.setTime(millis + millisInDay - (owner.internalGet(15) + owner.internalGet(16)))
        } else if (!owner.getLenientFlag() || owner.getSkippedWallTime() == NEXT_VALID) {
            val offset = owner.computeZoneOffset(millis,millisInDay)
            val candidate = millis + millisInDay - offset
            val actualOffset = owner.getTotalOffset(owner.getZone(),candidate)
            if (offset != actualOffset) {
                if (!owner.getLenientFlag()) throw IllegalArgumentException("The specified wall time does not exist due to time zone offset transition.")
                if (assertionsEnabled && owner.getSkippedWallTime() != NEXT_VALID) throw AssertionError(owner.getSkippedWallTime().toString())
                val transition = getImmediatePreviousZoneTransition(candidate)
                    ?: throw RuntimeException("Could not locate a time zone transition before $candidate")
                owner.setTime(transition)
            } else owner.setTime(candidate)
        } else {
            owner.setTime(millis + millisInDay - owner.computeZoneOffset(millis,millisInDay))
        }
    }

    fun computeZoneOffset(millis: Long, millisInDay: Int): Int = computeZoneOffset(millis,millisInDay.toLong())
    fun computeZoneOffset(millis: Long, millisInDay: Long): Int {
        val offsets = IntArray(2)
        val wall = millis + millisInDay
        if (owner.isBasicTimeZone(owner.getZone())) {
            val nonExisting = if (owner.getSkippedWallTime() == FIRST) LATTER else FORMER
            val duplicated = if (owner.getRepeatedWallTime() == FIRST) FORMER else LATTER
            owner.getOffsetFromLocal(owner.getZone(),wall,nonExisting,duplicated,offsets)
        } else {
            owner.getOffset(owner.getZone(),wall,true,offsets)
            var negativeShift = false
            if (owner.getRepeatedWallTime() == FIRST) {
                val utc = wall - (offsets[0] + offsets[1])
                val before = owner.getTotalOffset(owner.getZone(),utc - SIX_HOURS)
                val delta = offsets[0] + offsets[1] - before
                if (assertionsEnabled && delta <= -SIX_HOURS) throw AssertionError(delta.toString())
                if (delta < 0) {
                    negativeShift = true
                    owner.getOffset(owner.getZone(),wall + delta,true,offsets)
                }
            }
            if (!negativeShift && owner.getSkippedWallTime() == FIRST) {
                val utc = wall - (offsets[0] + offsets[1])
                owner.getOffset(owner.getZone(),utc,false,offsets)
            }
        }
        return offsets[0] + offsets[1]
    }

    fun getImmediatePreviousZoneTransition(base: Long): Long? {
        if (owner.isBasicTimeZone(owner.getZone())) {
            return owner.getPreviousTransition(owner.getZone(),base,true)?.getTime()
        }
        return getPreviousZoneTransitionTime(owner.getZone(),base,7200000)
            ?: getPreviousZoneTransitionTime(owner.getZone(),base,108000000)
    }
    fun getPreviousZoneTransitionTime(zone: Z?, base: Long, duration: Long): Long? {
        if (assertionsEnabled && duration <= 0) throw AssertionError()
        val lower = base - duration - 1
        val upperOffset = owner.getTotalOffset(zone,base)
        val lowerOffset = owner.getTotalOffset(zone,lower)
        if (upperOffset == lowerOffset) return null
        return findPreviousZoneTransitionTime(zone,upperOffset,base,lower)
    }
    fun findPreviousZoneTransitionTime(zone: Z?, upperOffset: Int, upperBound: Long, lower: Long): Long {
        var upper = upperBound
        var onUnit = false
        var mid = 0L
        for (unit in units) {
            val lowUnits = lower / unit
            val upperUnits = upper / unit
            if (upperUnits > lowUnits) {
                mid = ((lowUnits + upperUnits + 1) ushr 1) * unit
                onUnit = true
                break
            }
        }
        if (!onUnit) mid = (upper + lower) ushr 1
        if (onUnit) {
            if (mid != upper) {
                val offset = owner.getTotalOffset(zone,mid)
                if (offset != upperOffset) return findPreviousZoneTransitionTime(zone,upperOffset,upper,mid)
                upper = mid
            }
            mid--
        } else mid = (upper + lower) ushr 1
        if (mid == lower) return upper
        val offset = owner.getTotalOffset(zone,mid)
        if (offset != upperOffset) {
            if (onUnit) return upper
            return findPreviousZoneTransitionTime(zone,upperOffset,upper,mid)
        }
        return findPreviousZoneTransitionTime(zone,upperOffset,mid,lower)
    }

    private fun stamp(field: Int): Int { if (field !in stamps.indices) throw IndexOutOfBoundsException(); return stamps[field].toInt() }
    companion object {
        const val LAST = 0; const val FIRST = 1; const val NEXT_VALID = 2
        const val FORMER = 4; const val LATTER = 12
        private const val SIX_HOURS = 21600000
        private val units = intArrayOf(3600000,1800000,60000,1000)
    }
}
