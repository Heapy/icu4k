// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** A time-zone rule taking effect at a sorted, defensively copied set of millisecond times. */
open class TimeArrayTimeZoneRule(name: String?, rawOffset: Int, dstSavings: Int, times: LongArray?, private val timeType: Int) : TimeZoneRule(name,rawOffset,dstSavings) {
    private val startTimes: LongArray
    init {
        if (times == null || times.isEmpty()) throw IllegalArgumentException("No start times are specified.")
        startTimes = times.copyOf().also { it.sort() }
    }
    open fun getStartTimes(): LongArray = startTimes.copyOf()
    open fun getTimeType(): Int = timeType
    override fun getFirstStart(prevRawOffset: Int, prevDSTSavings: Int): Date? = Date(getUTC(startTimes[0],prevRawOffset,prevDSTSavings))
    override fun getFinalStart(prevRawOffset: Int, prevDSTSavings: Int): Date? = Date(getUTC(startTimes.last(),prevRawOffset,prevDSTSavings))
    override fun getNextStart(base: Long, prevRawOffset: Int, prevDSTSavings: Int, inclusive: Boolean): Date? {
        var i = startTimes.size-1
        while (i >= 0) {
            val time = getUTC(startTimes[i],prevRawOffset,prevDSTSavings)
            if (time < base || (!inclusive && time == base)) break
            i--
        }
        return if (i == startTimes.size-1) null else Date(getUTC(startTimes[i+1],prevRawOffset,prevDSTSavings))
    }
    override fun getPreviousStart(base: Long, prevRawOffset: Int, prevDSTSavings: Int, inclusive: Boolean): Date? {
        var i = startTimes.size-1
        while (i >= 0) {
            val time = getUTC(startTimes[i],prevRawOffset,prevDSTSavings)
            if (time < base || (inclusive && time == base)) return Date(time)
            i--
        }
        return null
    }
    override fun isEquivalentTo(other: TimeZoneRule?): Boolean = other is TimeArrayTimeZoneRule && timeType == other.timeType &&
        startTimes.contentEquals(other.startTimes) && super.isEquivalentTo(other)
    override fun isTransitionRule(): Boolean = true
    private fun getUTC(value: Long, raw: Int, dst: Int): Long {
        var time = value
        if (timeType != DateTimeRule.UTC_TIME) time -= raw
        if (timeType == DateTimeRule.WALL_TIME) time -= dst
        return time
    }
    override fun toString(): String = super.toString()+", timeType=$timeType, startTimes=["+startTimes.joinToString(", ")+"]"
    private companion object { private const val serialVersionUID = -1117109130077415245L }
}
