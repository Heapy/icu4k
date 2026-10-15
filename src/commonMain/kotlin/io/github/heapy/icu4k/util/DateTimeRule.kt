// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** A date within a year and its wall, standard, or UTC time rule. */
open class DateTimeRule private constructor(
    private val dateRuleType: Int,
    private val month: Int,
    private val dayOfMonth: Int,
    private val dayOfWeek: Int,
    private val weekInMonth: Int,
    private val millisInDay: Int,
    private val timeRuleType: Int,
) : ICUSerializable {
    constructor(month: Int, dayOfMonth: Int, millisInDay: Int, timeType: Int) :
        this(DOM,month,dayOfMonth,0,0,millisInDay,timeType)
    constructor(month: Int, weekInMonth: Int, dayOfWeek: Int, millisInDay: Int, timeType: Int) :
        this(DOW,month,0,dayOfWeek,weekInMonth,millisInDay,timeType)
    constructor(month: Int, dayOfMonth: Int, dayOfWeek: Int, after: Boolean, millisInDay: Int, timeType: Int) :
        this(if (after) DOW_GEQ_DOM else DOW_LEQ_DOM,month,dayOfMonth,dayOfWeek,0,millisInDay,timeType)

    open fun getDateRuleType(): Int = dateRuleType
    open fun getRuleMonth(): Int = month
    open fun getRuleDayOfMonth(): Int = dayOfMonth
    open fun getRuleDayOfWeek(): Int = dayOfWeek
    open fun getRuleWeekInMonth(): Int = weekInMonth
    open fun getTimeRuleType(): Int = timeRuleType
    open fun getRuleMillisInDay(): Int = millisInDay

    override fun toString(): String {
        val date = when (dateRuleType) {
            DOM -> dayOfMonth.toString()
            DOW -> weekInMonth.toString()+element(DOWSTR,dayOfWeek)
            DOW_GEQ_DOM -> element(DOWSTR,dayOfWeek)+">="+dayOfMonth
            DOW_LEQ_DOM -> element(DOWSTR,dayOfWeek)+"<="+dayOfMonth
            else -> null
        }
        val timeType = when (timeRuleType) { WALL_TIME -> "WALL"; STANDARD_TIME -> "STD"; UTC_TIME -> "UTC"; else -> null }
        var time = millisInDay
        val millis = time%1000; time /= 1000
        val secs = time%60; time /= 60
        val mins = time%60; val hours = time/60
        return buildString {
            append("month="); append(element(MONSTR,month)); append(", date="); append(date)
            append(", time="); append(hours); append(':'); append(mins/10); append(mins%10)
            append(':'); append(secs/10); append(secs%10); append('.')
            append(millis/100); append((millis/10)%10); append(millis%10)
            append('('); append(timeType); append(')')
        }
    }
    companion object {
        const val DOM = 0
        const val DOW = 1
        const val DOW_GEQ_DOM = 2
        const val DOW_LEQ_DOM = 3
        const val WALL_TIME = 0
        const val STANDARD_TIME = 1
        const val UTC_TIME = 2
        private const val serialVersionUID = 2183055795738051443L
        private val DOWSTR = arrayOf("","Sun","Mon","Tue","Wed","Thu","Fri","Sat")
        private val MONSTR = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        private fun element(a: Array<String>,i: Int): String {
            if (i < 0 || i >= a.size) throw IndexOutOfBoundsException()
            return a[i]
        }
    }
}
