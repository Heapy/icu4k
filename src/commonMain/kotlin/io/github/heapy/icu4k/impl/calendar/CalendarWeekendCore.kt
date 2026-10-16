// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.util.Date

/** Calendar week configuration and weekend predicates, preserving virtual dispatch and partial state. */
internal class CalendarWeekendCore<C : Any>(private val state: State, private val owner: Owner<C>) {
    interface State {
        val firstDayOfWeek: Int
        val minimalDaysInFirstWeek: Int
        var weekendOnset: Int
        var weekendOnsetMillis: Int
        var weekendCease: Int
        var weekendCeaseMillis: Int
    }
    interface Owner<C : Any> {
        val self: C
        fun setFirstDayOfWeek(value: Int)
        fun setMinimalDaysInFirstWeek(value: Int)
        fun setWeekData(value: CalendarWeekData): C
        fun get(field: Int): Int
        fun internalGet(field: Int): Int
        fun getDayOfWeekType(day: Int): Int
        fun getWeekendTransition(day: Int): Int
        fun setTime(date: Date?)
        fun isWeekend(): Boolean
    }

    fun getDayOfWeekType(day: Int): Int {
        if (day < 1 || day > 7) throw IllegalArgumentException("Invalid day of week")
        if (state.weekendOnset == state.weekendCease) {
            if (day != state.weekendOnset) return 0
            return if (state.weekendOnsetMillis == 0) 1 else 2
        }
        if (state.weekendOnset < state.weekendCease) {
            if (day < state.weekendOnset || day > state.weekendCease) return 0
        } else {
            if (day > state.weekendCease && day < state.weekendOnset) return 0
        }
        if (day == state.weekendOnset) return if (state.weekendOnsetMillis == 0) 1 else 2
        if (day == state.weekendCease) return if (state.weekendCeaseMillis >= 86400000) 1 else 3
        return 1
    }

    fun getWeekendTransition(day: Int): Int {
        if (day == state.weekendOnset) return state.weekendOnsetMillis
        if (day == state.weekendCease) return state.weekendCeaseMillis
        throw IllegalArgumentException("Not weekend transition day")
    }

    fun isWeekend(date: Date?): Boolean {
        owner.setTime(date)
        return owner.isWeekend()
    }
    fun isWeekend(): Boolean {
        val dow = owner.get(7)
        val type = owner.getDayOfWeekType(dow)
        return when (type) {
            0 -> false
            1 -> true
            else -> {
                val millis = owner.internalGet(14) + 1000 * (owner.internalGet(13) +
                    60 * (owner.internalGet(12) + 60 * owner.internalGet(11)))
                val transition = owner.getWeekendTransition(dow)
                if (type == 2) millis >= transition else millis < transition
            }
        }
    }

    fun getWeekData(): CalendarWeekData = CalendarWeekData(state.firstDayOfWeek, state.minimalDaysInFirstWeek,
        state.weekendOnset, state.weekendOnsetMillis, state.weekendCease, state.weekendCeaseMillis)
    fun setWeekData(data: CalendarWeekData?): C {
        val value = data!!
        owner.setFirstDayOfWeek(value.firstDayOfWeek)
        owner.setMinimalDaysInFirstWeek(value.minimalDaysInFirstWeek)
        state.weekendOnset = value.weekendOnset
        state.weekendOnsetMillis = value.weekendOnsetMillis
        state.weekendCease = value.weekendCease
        state.weekendCeaseMillis = value.weekendCeaseMillis
        return owner.self
    }
    fun setWeekDataForRegion(region: String?) { owner.setWeekData(CalendarWeekDataCore.cachedForRegion(region)) }
}
