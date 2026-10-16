// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.Date

/** Calendar's mutable field/time lifecycle, configuration and base Temporal accessors. */
internal class CalendarStateCore<Z>(
    private val fieldsProvider: () -> IntArray, private val stampsProvider: () -> ByteArray, private val state: State<Z>,
    private val owner: Owner, private val assertionsEnabled: Boolean,
) {
    constructor(fields: IntArray, stamps: ByteArray, state: State<Z>, owner: Owner, assertionsEnabled: Boolean) :
        this({ fields }, { stamps }, state, owner, assertionsEnabled)
    private val fields: IntArray get() = fieldsProvider()
    private val stamps: ByteArray get() = stampsProvider()
    interface State<Z> {
        var time: Long
        var isTimeSet: Boolean
        var areFieldsSet: Boolean
        var areAllFieldsSet: Boolean
        var areFieldsVirtuallySet: Boolean
        var nextStamp: Byte
        var zone: Z?
        var lenient: Boolean
        var repeatedWallTime: Int
        var skippedWallTime: Int
        var firstDayOfWeek: Int
        var minimalDaysInFirstWeek: Int
    }
    data class Values<Z>(
        override var time: Long = 0, override var isTimeSet: Boolean = false,
        override var areFieldsSet: Boolean = false, override var areAllFieldsSet: Boolean = false,
        override var areFieldsVirtuallySet: Boolean = false, override var nextStamp: Byte = 2,
        override var zone: Z? = null, override var lenient: Boolean = true,
        override var repeatedWallTime: Int = 0, override var skippedWallTime: Int = 0,
        override var firstDayOfWeek: Int = 0, override var minimalDaysInFirstWeek: Int = 0,
    ) : State<Z>
    interface Owner {
        fun computeFields()
        fun computeTime()
        fun complete()
        fun isLenient(): Boolean
        fun getTimeInMillis(): Long
        fun setTimeInMillis(millis: Long)
        fun getRelatedYearDifference(): Int
        fun getActualMaximum(field: Int): Int
    }

    fun getTime(): Date = Date(owner.getTimeInMillis())
    fun setTime(date: Date?) = owner.setTimeInMillis(date!!.getTime())
    fun getTimeInMillis(): Long { if (!state.isTimeSet) updateTime(); return state.time }
    fun setTimeInMillis(value: Long) {
        var millis = value
        if (millis > Grego.MAX_MILLIS) {
            if (owner.isLenient()) millis = Grego.MAX_MILLIS
            else throw IllegalArgumentException("millis value greater than upper bounds for a Calendar : $millis")
        } else if (millis < Grego.MIN_MILLIS) {
            if (owner.isLenient()) millis = Grego.MIN_MILLIS
            else throw IllegalArgumentException("millis value less than lower bounds for a Calendar : $millis")
        }
        state.time = millis
        state.areAllFieldsSet = false; state.areFieldsSet = false
        state.areFieldsVirtuallySet = true; state.isTimeSet = true
        fields.fill(0); stamps.fill(0); state.nextStamp = 2
    }
    fun get(field: Int): Int { owner.complete(); return getField(field) }
    fun getFieldCount(): Int = fields.size
    fun set(field: Int, value: Int) {
        if (state.areFieldsVirtuallySet) owner.computeFields()
        putField(field,value)
        if (state.nextStamp.toInt() == 127) recalculateStamp()
        val stamp = state.nextStamp
        state.nextStamp = (stamp + 1).toByte()
        putStamp(field,stamp)
        state.areFieldsVirtuallySet = false; state.areFieldsSet = false; state.isTimeSet = false
    }
    fun set(year: Int,month: Int,date: Int) { set(1,year); set(2,month); set(5,date) }
    fun set(year: Int,month: Int,date: Int,hour: Int,minute: Int) { set(year,month,date); set(11,hour); set(12,minute) }
    fun set(year: Int,month: Int,date: Int,hour: Int,minute: Int,second: Int) { set(year,month,date,hour,minute); set(13,second) }
    fun clear() {
        fields.fill(0); stamps.fill(0); state.nextStamp = 2
        state.areFieldsVirtuallySet = false; state.areAllFieldsSet = false; state.areFieldsSet = false; state.isTimeSet = false
    }
    fun clear(field: Int) {
        if (state.areFieldsVirtuallySet) owner.computeFields()
        putField(field,0); putStamp(field,0)
        if (field == 2) { putField(23,0); putStamp(23,0) }
        if (field == 23) { putField(2,0); putStamp(2,0) }
        state.areFieldsVirtuallySet = false; state.areAllFieldsSet = false; state.areFieldsSet = false; state.isTimeSet = false
    }
    fun isSet(field: Int): Boolean = state.areFieldsVirtuallySet || getStamp(field).toInt() != 0
    fun complete() {
        if (!state.isTimeSet) updateTime()
        if (!state.areFieldsSet) {
            owner.computeFields()
            state.areFieldsSet = true; state.areAllFieldsSet = true
        }
    }
    private fun updateTime() {
        owner.computeTime()
        if (owner.isLenient() || !state.areAllFieldsSet) state.areFieldsSet = false
        state.isTimeSet = true; state.areFieldsVirtuallySet = false
    }
    private fun recalculateStamp() {
        state.nextStamp = 1
        repeat(stamps.size) {
            var current: Byte = 127
            var index = -1
            for (i in stamps.indices) {
                if (stamps[i] > state.nextStamp && stamps[i] < current) { current = stamps[i]; index = i }
            }
            if (index < 0) {
                state.nextStamp = (state.nextStamp + 1).toByte()
                return
            }
            state.nextStamp = (state.nextStamp + 1).toByte()
            stamps[index] = state.nextStamp
        }
        state.nextStamp = (state.nextStamp + 1).toByte()
    }

    fun setTimeZone(zone: Z?) { state.zone = zone; state.areFieldsSet = false }
    fun getTimeZone(): Z? = state.zone
    fun setLenient(value: Boolean) { state.lenient = value }
    fun isLenient(): Boolean = state.lenient
    fun setRepeatedWallTimeOption(option: Int) {
        require(option == 0 || option == 1) { "Illegal repeated wall time option - $option" }
        state.repeatedWallTime = option
    }
    fun getRepeatedWallTimeOption(): Int = state.repeatedWallTime
    fun setSkippedWallTimeOption(option: Int) {
        require(option in 0..2) { "Illegal skipped wall time option - $option" }
        state.skippedWallTime = option
    }
    fun getSkippedWallTimeOption(): Int = state.skippedWallTime
    fun setFirstDayOfWeek(value: Int) {
        if (state.firstDayOfWeek != value) {
            require(value in 1..7) { "Invalid day of week" }
            state.firstDayOfWeek = value; state.areFieldsSet = false
        }
    }
    fun getFirstDayOfWeek(): Int = state.firstDayOfWeek
    fun setMinimalDaysInFirstWeek(value: Int) {
        val normalized = value.coerceIn(1,7)
        if (state.minimalDaysInFirstWeek != normalized) { state.minimalDaysInFirstWeek = normalized; state.areFieldsSet = false }
    }
    fun getMinimalDaysInFirstWeek(): Int = state.minimalDaysInFirstWeek
    fun getRelatedYearDifference(): Int = 0
    fun getRelatedYear(): Int = get(19) + owner.getRelatedYearDifference()
    fun setRelatedYear(year: Int) = set(19,year - owner.getRelatedYearDifference())
    fun inTemporalLeapYear(): Boolean = owner.getActualMaximum(6) == 366
    fun getTemporalMonthCode(): String {
        val month = get(2)
        if (assertionsEnabled && month >= 12) throw AssertionError()
        if (assertionsEnabled && getField(22) != 0) throw AssertionError()
        if (month !in monthCodes.indices) throw IndexOutOfBoundsException()
        return monthCodes[month]
    }
    fun setTemporalMonthCode(code: String?) {
        if (code!!.length == 3 && code[0] == 'M') {
            for (m in monthCodes.indices) if (code == monthCodes[m]) { set(2,m); set(22,0); return }
        }
        throw IllegalArgumentException("Incorrect temporal Month code: $code")
    }

    private fun getField(field: Int): Int { if (field !in fields.indices) throw IndexOutOfBoundsException(); return fields[field] }
    private fun putField(field: Int,value: Int) { if (field !in fields.indices) throw IndexOutOfBoundsException(); fields[field] = value }
    private fun getStamp(field: Int): Byte { if (field !in stamps.indices) throw IndexOutOfBoundsException(); return stamps[field] }
    private fun putStamp(field: Int,value: Byte) { if (field !in stamps.indices) throw IndexOutOfBoundsException(); stamps[field] = value }

    data class Storage(val fields: IntArray,val stamps: ByteArray,val internalSetMask: Int)
    companion object {
        private val monthCodes = arrayOf("M01","M02","M03","M04","M05","M06","M07","M08","M09","M10","M11","M12")
        fun createStorage(fields: IntArray?): Storage {
            if (fields == null || fields.size < 24 || fields.size > 32) throw IllegalStateException("Invalid fields[]")
            var mask = listOf(0,1,2,5,6,19,22,23).fold(0) { value,bit -> value or (1 shl bit) }
            for (i in 24 until fields.size) mask = mask or (1 shl i)
            return Storage(fields,ByteArray(fields.size),mask)
        }
    }
}
