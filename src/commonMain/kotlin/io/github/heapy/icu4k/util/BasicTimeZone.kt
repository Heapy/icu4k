// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.timezone.BasicTimeZoneCore

/** A time zone exposing its transitions and their underlying rules. */
abstract class BasicTimeZone : TimeZone {
    protected constructor() : super()
    protected constructor(id: String?) : super(id)
    abstract fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition?
    abstract fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition?
    abstract fun getTimeZoneRules(): Array<TimeZoneRule?>?
    private fun core() = object : BasicTimeZoneCore() {
        override fun getID() = this@BasicTimeZone.getID()
        override fun getOffset(date: Long, local: Boolean, offsets: IntArray) = this@BasicTimeZone.getOffset(date, local, offsets)
        override fun getNextTransition(base: Long, inclusive: Boolean) = this@BasicTimeZone.getNextTransition(base, inclusive)
        override fun getPreviousTransition(base: Long, inclusive: Boolean) = this@BasicTimeZone.getPreviousTransition(base, inclusive)
        override fun getTimeZoneRules() = this@BasicTimeZone.getTimeZoneRules()
    }
    open fun hasEquivalentTransitions(other: TimeZone?, start: Long, end: Long): Boolean = hasEquivalentTransitions(other, start, end, false)
    open fun hasEquivalentTransitions(other: TimeZone?, start: Long, end: Long, ignoreDstAmount: Boolean): Boolean {
        if (this === other) return true
        if (other !is BasicTimeZone) return false
        return core().hasEquivalentTransitions(other.core(), start, end, ignoreDstAmount)
    }
    open fun getTimeZoneRules(start: Long): Array<TimeZoneRule?>? = core().getTimeZoneRules(start)
    open fun getSimpleTimeZoneRulesNear(date: Long): Array<TimeZoneRule?> = core().getSimpleTimeZoneRulesNear(date)
    enum class LocalOption(internal val flag: Int) {
        FORMER(4), LATTER(12), STANDARD_FORMER(5), STANDARD_LATTER(13), DAYLIGHT_FORMER(7), DAYLIGHT_LATTER(15)
    }
    open fun getOffsetFromLocal(date: Long, nonExistingTimeOpt: LocalOption?, duplicatedTimeOpt: LocalOption?, offsets: IntArray?) {
        throw IllegalStateException("Not implemented")
    }
    protected companion object {
        fun getLocalOptionValue(option: LocalOption?): Int = option!!.flag
        const val LOCAL_STD = 1
        const val LOCAL_DST = 3
        const val LOCAL_FORMER = 4
        const val LOCAL_LATTER = 12
        const val STD_DST_MASK = 3
        const val FORMER_LATTER_MASK = 12
    }
}
