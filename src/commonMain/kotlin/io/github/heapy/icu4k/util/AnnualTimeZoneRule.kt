// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.Grego

/** An annually recurring time-zone rule, using astronomical Gregorian years. */
open class AnnualTimeZoneRule(
    name: String?, rawOffset: Int, dstSavings: Int,
    private val dateTimeRule: DateTimeRule?, private val startYear: Int, private val endYear: Int,
) : TimeZoneRule(name,rawOffset,dstSavings) {
    open fun getRule(): DateTimeRule? = dateTimeRule
    open fun getStartYear(): Int = startYear
    open fun getEndYear(): Int = endYear
    open fun getStartInYear(year: Int, prevRawOffset: Int, prevDSTSavings: Int): Date? {
        if (year < startYear || year > endYear) return null
        val rule = dateTimeRule!!
        val type = rule.getDateRuleType()
        var ruleDay: Long
        if (type == DateTimeRule.DOM) ruleDay = Grego.fieldsToDay(year,rule.getRuleMonth(),rule.getRuleDayOfMonth())
        else {
            var after = true
            if (type == DateTimeRule.DOW) {
                val weeks = rule.getRuleWeekInMonth()
                if (weeks > 0) {
                    ruleDay = Grego.fieldsToDay(year,rule.getRuleMonth(),1)
                    ruleDay += (7*(weeks-1)).toLong()
                } else {
                    after = false
                    ruleDay = Grego.fieldsToDay(year,rule.getRuleMonth(),Grego.monthLength(year,rule.getRuleMonth()))
                    ruleDay += (7*(weeks+1)).toLong()
                }
            } else {
                val month = rule.getRuleMonth()
                var dom = rule.getRuleDayOfMonth()
                if (type == DateTimeRule.DOW_LEQ_DOM) {
                    after = false
                    if (month == 1 && dom == 29 && !Grego.isLeapYear(year)) dom--
                }
                ruleDay = Grego.fieldsToDay(year,month,dom)
            }
            val dow = Grego.dayOfWeek(ruleDay)
            var delta = rule.getRuleDayOfWeek()-dow
            if (after) { if (delta < 0) delta += 7 } else if (delta > 0) delta -= 7
            ruleDay += delta
        }
        var time = ruleDay*Grego.MILLIS_PER_DAY+rule.getRuleMillisInDay()
        if (rule.getTimeRuleType() != DateTimeRule.UTC_TIME) time -= prevRawOffset
        if (rule.getTimeRuleType() == DateTimeRule.WALL_TIME) time -= prevDSTSavings
        return Date(time)
    }
    override fun getFirstStart(prevRawOffset: Int, prevDSTSavings: Int): Date? = getStartInYear(startYear,prevRawOffset,prevDSTSavings)
    override fun getFinalStart(prevRawOffset: Int, prevDSTSavings: Int): Date? = if (endYear == MAX_YEAR) null else getStartInYear(endYear,prevRawOffset,prevDSTSavings)
    override fun getNextStart(base: Long, prevRawOffset: Int, prevDSTSavings: Int, inclusive: Boolean): Date? {
        val year = Grego.timeToYear(base)
        if (year < startYear) return getFirstStart(prevRawOffset,prevDSTSavings)
        var date = getStartInYear(year,prevRawOffset,prevDSTSavings)
        if (date != null && (date.getTime() < base || (!inclusive && date.getTime() == base))) date = getStartInYear(year+1,prevRawOffset,prevDSTSavings)
        return date
    }
    override fun getPreviousStart(base: Long, prevRawOffset: Int, prevDSTSavings: Int, inclusive: Boolean): Date? {
        val year = Grego.timeToYear(base)
        if (year > endYear) return getFinalStart(prevRawOffset,prevDSTSavings)
        var date = getStartInYear(year,prevRawOffset,prevDSTSavings)
        if (date != null && (date.getTime() > base || (!inclusive && date.getTime() == base))) date = getStartInYear(year-1,prevRawOffset,prevDSTSavings)
        return date
    }
    override fun isEquivalentTo(other: TimeZoneRule?): Boolean = other is AnnualTimeZoneRule && startYear == other.startYear &&
        endYear == other.endYear && dateTimeRule!!.equals(other.dateTimeRule) && super.isEquivalentTo(other)
    override fun isTransitionRule(): Boolean = true
    override fun toString(): String = super.toString()+", rule={$dateTimeRule}, startYear=$startYear, endYear="+(if (endYear == MAX_YEAR) "max" else endYear.toString())
    companion object {
        const val MAX_YEAR = Int.MAX_VALUE
        private const val serialVersionUID = -8870666707791230688L
    }
}
