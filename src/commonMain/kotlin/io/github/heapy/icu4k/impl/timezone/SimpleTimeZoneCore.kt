// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.util.*
import kotlin.concurrent.Volatile

/** SimpleTimeZone's rule/offset engine, independent of its pending public Calendar front end. */
internal class SimpleTimeZoneCore(
    private var raw: Int, id: String?,
    private var startMonth: Int = 0, private var startDay: Int = 0, private var startDayOfWeek: Int = 0,
    private var startTime: Int = 0, private var startTimeMode: Int = WALL_TIME,
    private var endMonth: Int = 0, private var endDay: Int = 0, private var endDayOfWeek: Int = 0,
    private var endTime: Int = 0, private var endTimeMode: Int = WALL_TIME,
    private var dst: Int = Grego.MILLIS_PER_HOUR,
    private val owner: OwnerQueries? = null,
) {
    interface OwnerQueries {
        fun getID(): String?
        fun getRawOffset(): Int
        fun getDSTSavings(): Int
        fun isFrozen(): Boolean
        fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int): Int
        fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int, monthLength: Int): Int
    }
    private var id: String = id!!
    private var startYear = 0
    private var useDaylight = false
    private var startMode = DOM_MODE
    private var endMode = DOM_MODE
    private var info: STZInfo? = null
    private val lock = createAvailabilityLock()
    private var transitionRulesInitialized = false
    private var initialRule: InitialTimeZoneRule? = null
    private var firstTransition: TimeZoneTransition? = null
    private var stdRule: AnnualTimeZoneRule? = null
    private var dstRule: AnnualTimeZoneRule? = null
    @Volatile private var frozen = false
    init {
        val originalDST = dst
        decodeStartRule()
        decodeEndRule()
        if (originalDST == 0) throw IllegalArgumentException()
    }
    private fun queryFrozen(): Boolean = if (owner == null) isFrozen() else owner.isFrozen()
    private fun queryID(): String? = if (owner == null) id else owner.getID()
    private fun queryRaw(): Int = if (owner == null) raw else owner.getRawOffset()
    private fun queryDST(): Int = if (owner == null) dst else owner.getDSTSavings()
    private fun queryOffset(era: Int, year: Int, month: Int, day: Int, dow: Int, millis: Int): Int =
        if (owner == null) getOffset(era, year, month, day, dow, millis) else owner.getOffset(era, year, month, day, dow, millis)
    private fun checkMutable(frozenCheck: Boolean) {
        if (frozenCheck) throw UnsupportedOperationException("Attempt to modify a frozen SimpleTimeZone instance.")
    }
    fun getID(): String = id
    fun setID(value: String?, frozenCheck: Boolean = queryFrozen(), baseSetID: ((String) -> Unit)? = null) {
        checkMutable(frozenCheck)
        val newID = value!!
        // TimeZone.setID checks null before its own virtual frozen query.
        if (baseSetID != null) baseSetID(newID)
        else if (queryFrozen()) throw UnsupportedOperationException("Attempt to modify a frozen TimeZone instance.")
        id = newID
        transitionRulesInitialized = false
    }
    fun getRawOffset(): Int = raw
    fun setRawOffset(value: Int, frozenCheck: Boolean = queryFrozen()) {
        checkMutable(frozenCheck); raw = value; transitionRulesInitialized = false
    }
    fun getDSTSavings(): Int = dst
    fun setDSTSavings(value: Int, frozenCheck: Boolean = queryFrozen()) {
        checkMutable(frozenCheck)
        if (value == 0) throw IllegalArgumentException()
        dst = value; transitionRulesInitialized = false
    }
    fun setStartYear(value: Int, frozenCheck: Boolean = queryFrozen()) {
        checkMutable(frozenCheck)
        getInfo().sy = value
        startYear = value; transitionRulesInitialized = false
    }
    fun setStartRule(month: Int, day: Int, dow: Int, time: Int) {
        checkMutable(queryFrozen())
        getInfo().setStart(month, day, dow, time, -1, false)
        setStart(month, day, dow, time, WALL_TIME)
    }
    fun setStartRule(month: Int, day: Int, time: Int) {
        checkMutable(queryFrozen())
        getInfo().setStart(month, -1, -1, time, day, false)
        setStart(month, day, 0, time, WALL_TIME)
    }
    fun setStartRule(month: Int, day: Int, dow: Int, time: Int, after: Boolean) {
        checkMutable(queryFrozen())
        getInfo().setStart(month, -1, dow, time, day, after)
        setStart(month, if (after) day else -day, -dow, time, WALL_TIME)
    }
    fun setEndRule(month: Int, day: Int, dow: Int, time: Int) {
        checkMutable(queryFrozen())
        getInfo().setEnd(month, day, dow, time, -1, false)
        setEnd(month, day, dow, time, WALL_TIME)
    }
    fun setEndRule(month: Int, day: Int, time: Int) {
        checkMutable(queryFrozen())
        getInfo().setEnd(month, -1, -1, time, day, false)
        // The source delegates through the public four-argument overload and overwrites xinfo.
        setEndRule(month, day, WALL_TIME, time)
    }
    fun setEndRule(month: Int, day: Int, dow: Int, time: Int, after: Boolean) {
        checkMutable(queryFrozen())
        getInfo().setEnd(month, -1, dow, time, day, after)
        assertMutable()
        setEnd(month, if (after) day else -day, -dow, time, WALL_TIME)
    }
    private fun assertMutable() {
        if (assertionsEnabled && queryFrozen()) throw AssertionError()
    }
    private fun setStart(month: Int, day: Int, dow: Int, time: Int, mode: Int) {
        assertMutable()
        startMonth = month; startDay = day; startDayOfWeek = dow; startTime = time; startTimeMode = mode
        decodeStartRule(); transitionRulesInitialized = false
    }
    private fun setEnd(month: Int, day: Int, dow: Int, time: Int, mode: Int) {
        assertMutable()
        endMonth = month; endDay = day; endDayOfWeek = dow; endTime = time; endTimeMode = mode
        decodeEndRule(); transitionRulesInitialized = false
    }
    private fun decodeStartRule() {
        useDaylight = startDay != 0 && endDay != 0
        if (useDaylight && dst == 0) dst = Grego.MILLIS_PER_DAY
        if (startDay != 0) {
            if (startMonth !in 0..11 || startTime !in 0..Grego.MILLIS_PER_DAY || startTimeMode !in WALL_TIME..UTC_TIME) throw IllegalArgumentException()
            if (startDayOfWeek == 0) startMode = DOM_MODE
            else {
                if (startDayOfWeek > 0) startMode = DOW_IN_MONTH_MODE
                else {
                    startDayOfWeek = -startDayOfWeek
                    if (startDay > 0) startMode = DOW_GE_DOM_MODE
                    else { startDay = -startDay; startMode = DOW_LE_DOM_MODE }
                }
                if (startDayOfWeek > 7) throw IllegalArgumentException()
            }
            if (startMode == DOW_IN_MONTH_MODE) {
                if (startDay < -5 || startDay > 5) throw IllegalArgumentException()
            } else if (startDay < 1 || startDay > MONTH_LENGTH[startMonth]) throw IllegalArgumentException()
        }
    }
    private fun decodeEndRule() {
        useDaylight = startDay != 0 && endDay != 0
        if (useDaylight && dst == 0) dst = Grego.MILLIS_PER_DAY
        if (endDay != 0) {
            if (endMonth !in 0..11 || endTime !in 0..Grego.MILLIS_PER_DAY || endTimeMode !in WALL_TIME..UTC_TIME) throw IllegalArgumentException()
            if (endDayOfWeek == 0) endMode = DOM_MODE
            else {
                if (endDayOfWeek > 0) endMode = DOW_IN_MONTH_MODE
                else {
                    endDayOfWeek = -endDayOfWeek
                    if (endDay > 0) endMode = DOW_GE_DOM_MODE
                    else { endDay = -endDay; endMode = DOW_LE_DOM_MODE }
                }
                if (endDayOfWeek > 7) throw IllegalArgumentException()
            }
            if (endMode == DOW_IN_MONTH_MODE) {
                if (endDay < -5 || endDay > 5) throw IllegalArgumentException()
            } else if (endDay < 1 || endDay > MONTH_LENGTH[endMonth]) throw IllegalArgumentException()
        }
    }
    fun useDaylightTime(): Boolean = useDaylight
    fun observesDaylightTime(): Boolean = useDaylight
    fun getOffset(era: Int, year: Int, month: Int, day: Int, dow: Int, millis: Int): Int {
        if (month !in 0..11) throw IllegalArgumentException()
        val length = Grego.monthLength(year, month)
        return if (owner == null) getOffset(era, year, month, day, dow, millis, length)
            else owner.getOffset(era, year, month, day, dow, millis, length)
    }
    @Suppress("UNUSED_PARAMETER")
    fun getOffset(era: Int, year: Int, month: Int, day: Int, dow: Int, millis: Int, ignoredMonthLength: Int): Int {
        if (month !in 0..11) throw IllegalArgumentException()
        val length = Grego.monthLength(year, month)
        val previousLength = Grego.previousMonthLength(year, month)
        if ((era != 0 && era != 1) || day < 1 || day > length || dow !in 1..7 || millis !in 0 until Grego.MILLIS_PER_DAY || length !in 28..31 || previousLength !in 28..31) throw IllegalArgumentException()
        var result = raw
        if (!useDaylight || year < startYear || era != 1) return result
        val southern = startMonth > endMonth
        val startCompare = compareToRule(month, length, previousLength, day, dow, millis, if (startTimeMode == UTC_TIME) -raw else 0,
            startMode, startMonth, startDayOfWeek, startDay, startTime)
        var endCompare = 0
        if (southern != (startCompare >= 0)) endCompare = compareToRule(month, length, previousLength, day, dow, millis,
            if (endTimeMode == WALL_TIME) dst else if (endTimeMode == UTC_TIME) -raw else 0, endMode, endMonth, endDayOfWeek, endDay, endTime)
        if ((!southern && startCompare >= 0 && endCompare < 0) || (southern && (startCompare >= 0 || endCompare < 0))) result += dst
        return result
    }
    private fun compareToRule(originalMonth: Int, monthLength: Int, previousLength: Int, originalDay: Int, originalDOW: Int,
        originalMillis: Int, delta: Int, mode: Int, ruleMonth: Int, ruleDOW: Int, originalRuleDay: Int, ruleMillis: Int): Int {
        var month = originalMonth; var day = originalDay; var dow = originalDOW; var millis = originalMillis + delta
        while (millis >= Grego.MILLIS_PER_DAY) {
            millis -= Grego.MILLIS_PER_DAY; ++day; dow = 1 + dow % 7
            if (day > monthLength) { day = 1; ++month }
        }
        while (millis < 0) {
            --day; dow = 1 + (dow + 5) % 7
            if (day < 1) { day = previousLength; --month }
            millis += Grego.MILLIS_PER_DAY
        }
        if (month < ruleMonth) return -1
        if (month > ruleMonth) return 1
        val ruleDay = if (originalRuleDay > monthLength) monthLength else originalRuleDay
        val ruleDOM = when (mode) {
            DOM_MODE -> ruleDay
            DOW_IN_MONTH_MODE -> if (ruleDay > 0) 1 + (ruleDay - 1) * 7 + (7 + ruleDOW - (dow - day + 1)) % 7
                else monthLength + (ruleDay + 1) * 7 - (7 + (dow + monthLength - day) - ruleDOW) % 7
            DOW_GE_DOM_MODE -> ruleDay + (49 + ruleDOW - ruleDay - dow + day) % 7
            DOW_LE_DOM_MODE -> ruleDay - (49 - ruleDOW + ruleDay + dow - day) % 7
            else -> 0
        }
        if (day < ruleDOM) return -1
        if (day > ruleDOM) return 1
        return millis.compareTo(ruleMillis)
    }
    // Assignment's RHS is evaluated before Java's array null/bounds checks.
    private fun write(offsets: IntArray?, index: Int, value: Int) {
        val target = offsets ?: throw NullPointerException()
        if (index !in target.indices) throw IndexOutOfBoundsException()
        target[index] = value
    }
    /** Inherited TimeZone.getOffset(long,boolean,int[]) standard-time conversion. */
    fun getOffset(originalDate: Long, local: Boolean, offsets: IntArray?) {
        var date = originalDate
        write(offsets, 0, queryRaw())
        if (!local) date += offsets!![0]
        val fields = IntArray(6)
        var pass = 0
        while (true) {
            Grego.timeToFields(date, fields)
            write(offsets, 1, queryOffset(1, fields[0], fields[1], fields[2], fields[3], fields[5]) - offsets!![0])
            if (pass != 0 || !local || offsets[1] == 0) break
            date -= offsets[1]; ++pass
        }
    }
    fun getOffsetFromLocal(originalDate: Long, nonExisting: Int, duplicated: Int, offsets: IntArray?) {
        var date = originalDate
        write(offsets, 0, queryRaw())
        val fields = IntArray(6)
        Grego.timeToFields(date, fields)
        write(offsets, 1, queryOffset(1, fields[0], fields[1], fields[2], fields[3], fields[5]) - offsets!![0])
        val recalc = if (offsets[1] > 0) (nonExisting and 3) == 1 || (nonExisting and 3) != 3 && (nonExisting and 12) != 12
            else (duplicated and 3) == 3 || (duplicated and 3) != 1 && (duplicated and 12) == 4
        if (recalc) {
            date -= queryDST()
            Grego.timeToFields(date, fields)
            write(offsets, 1, queryOffset(1, fields[0], fields[1], fields[2], fields[3], fields[5]) - offsets[0])
        }
    }
    private fun initTransitionRules() = lock.withLock {
        if (transitionRulesInitialized) return@withLock
        if (useDaylight) {
            fun rule(mode: Int, month: Int, day: Int, dow: Int, time: Int, timeMode: Int): DateTimeRule? = when (mode) {
                DOM_MODE -> DateTimeRule(month, day, time, timeMode)
                DOW_IN_MONTH_MODE -> DateTimeRule(month, day, dow, time, timeMode)
                DOW_GE_DOM_MODE -> DateTimeRule(month, day, dow, true, time, timeMode)
                DOW_LE_DOM_MODE -> DateTimeRule(month, day, dow, false, time, timeMode)
                else -> null
            }
            val startDateRule = rule(startMode, startMonth, startDay, startDayOfWeek, startTime, startTimeMode)
            dstRule = AnnualTimeZoneRule(queryID() + "(DST)", queryRaw(), queryDST(), startDateRule, startYear, AnnualTimeZoneRule.MAX_YEAR)
            val firstDST = dstRule!!.getFirstStart(queryRaw(), 0)!!.getTime()
            val endDateRule = rule(endMode, endMonth, endDay, endDayOfWeek, endTime, endTimeMode)
            stdRule = AnnualTimeZoneRule(queryID() + "(STD)", queryRaw(), 0, endDateRule, startYear, AnnualTimeZoneRule.MAX_YEAR)
            val firstSTD = stdRule!!.getFirstStart(queryRaw(), dstRule!!.getDSTSavings())!!.getTime()
            if (firstSTD < firstDST) {
                initialRule = InitialTimeZoneRule(queryID() + "(DST)", queryRaw(), dstRule!!.getDSTSavings())
                firstTransition = TimeZoneTransition(firstSTD, initialRule, stdRule)
            } else {
                initialRule = InitialTimeZoneRule(queryID() + "(STD)", queryRaw(), 0)
                firstTransition = TimeZoneTransition(firstDST, initialRule, dstRule)
            }
        } else initialRule = InitialTimeZoneRule(queryID(), queryRaw(), 0)
        transitionRulesInitialized = true
    }
    fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition? {
        if (!useDaylight) return null
        initTransitionRules()
        val first = firstTransition!!.getTime()
        if (base < first || inclusive && base == first) return firstTransition
        val standard = stdRule!!.getNextStart(base, dstRule!!.getRawOffset(), dstRule!!.getDSTSavings(), inclusive)
        val daylight = dstRule!!.getNextStart(base, stdRule!!.getRawOffset(), stdRule!!.getDSTSavings(), inclusive)
        if (standard != null && (daylight == null || standard.before(daylight))) return TimeZoneTransition(standard.getTime(), dstRule, stdRule)
        if (daylight != null && (standard == null || daylight.before(standard))) return TimeZoneTransition(daylight.getTime(), stdRule, dstRule)
        return null
    }
    fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition? {
        if (!useDaylight) return null
        initTransitionRules()
        val first = firstTransition!!.getTime()
        if (base < first || !inclusive && base == first) return null
        val standard = stdRule!!.getPreviousStart(base, dstRule!!.getRawOffset(), dstRule!!.getDSTSavings(), inclusive)
        val daylight = dstRule!!.getPreviousStart(base, stdRule!!.getRawOffset(), stdRule!!.getDSTSavings(), inclusive)
        if (standard != null && (daylight == null || standard.after(daylight))) return TimeZoneTransition(standard.getTime(), dstRule, stdRule)
        if (daylight != null && (standard == null || daylight.after(standard))) return TimeZoneTransition(daylight.getTime(), stdRule, dstRule)
        return null
    }
    fun getTimeZoneRules(): Array<TimeZoneRule> {
        initTransitionRules()
        return if (useDaylight) arrayOf(initialRule!!, stdRule!!, dstRule!!) else arrayOf(initialRule!!)
    }
    fun hasSameRules(other: SimpleTimeZoneCore?): Boolean {
        if (this === other) return true
        if (other == null) return false
        return raw == other.raw && useDaylight == other.useDaylight && sameDST(other)
    }
    private fun sameDST(other: SimpleTimeZoneCore): Boolean = !useDaylight ||
            dst == other.dst && startMode == other.startMode && startMonth == other.startMonth && startDay == other.startDay &&
            startDayOfWeek == other.startDayOfWeek && startTime == other.startTime && startTimeMode == other.startTimeMode &&
            endMode == other.endMode && endMonth == other.endMonth && endDay == other.endDay && endDayOfWeek == other.endDayOfWeek &&
            endTime == other.endTime && endTimeMode == other.endTimeMode && startYear == other.startYear
    override fun equals(other: Any?): Boolean = this === other || other is SimpleTimeZoneCore && raw == other.raw &&
        useDaylight == other.useDaylight && queryID() == other.queryID() && sameDST(other)
    override fun hashCode(): Int {
        var result = (id.hashCode() + raw) xor ((raw ushr 8) + if (useDaylight) 0 else 1)
        if (!useDaylight) result += dst xor ((dst ushr 10) + startMode) xor ((startMode ushr 11) + startMonth) xor
            ((startMonth ushr 12) + startDay) xor ((startDay ushr 13) + startDayOfWeek) xor ((startDayOfWeek ushr 14) + startTime) xor
            ((startTime ushr 15) + startTimeMode) xor ((startTimeMode ushr 16) + endMode) xor ((endMode ushr 17) + endMonth) xor
            ((endMonth ushr 18) + endDay) xor ((endDay ushr 19) + endDayOfWeek) xor ((endDayOfWeek ushr 20) + endTime) xor
            ((endTime ushr 21) + endTimeMode) xor ((endTimeMode ushr 22) + startYear) xor (startYear ushr 23)
        return result
    }
    override fun toString(): String = "SimpleTimeZone: " + queryID()
    fun isFrozen(): Boolean = frozen
    fun freeze(): SimpleTimeZoneCore { frozen = true; return this }
    fun clone(): SimpleTimeZoneCore = if (queryFrozen()) this else cloneAsThawed()
    fun cloneAsThawed(newOwner: OwnerQueries? = owner): SimpleTimeZoneCore {
        // Construct from zero rules to avoid decoding already-decoded signed rule fields.
        val copy = SimpleTimeZoneCore(raw, id, owner = newOwner)
        copy.startMonth = startMonth; copy.startDay = startDay; copy.startDayOfWeek = startDayOfWeek
        copy.startTime = startTime; copy.startTimeMode = startTimeMode; copy.endMonth = endMonth; copy.endDay = endDay
        copy.endDayOfWeek = endDayOfWeek; copy.endTime = endTime; copy.endTimeMode = endTimeMode; copy.dst = dst
        copy.startYear = startYear; copy.startMode = startMode; copy.endMode = endMode; copy.useDaylight = useDaylight
        copy.info = info; copy.transitionRulesInitialized = transitionRulesInitialized; copy.initialRule = initialRule
        copy.firstTransition = firstTransition; copy.stdRule = stdRule; copy.dstRule = dstRule
        return copy
    }
    private fun getInfo(): STZInfo = info ?: STZInfo().also { info = it }
    internal class STZInfo {
        var sy = -1
        var sm = -1; var sdwm = 0; var sdw = 0; var st = 0; var sdm = 0; var sa = false
        var em = -1; var edwm = 0; var edw = 0; var et = 0; var edm = 0; var ea = false
        fun setStart(m: Int, dwm: Int, dw: Int, t: Int, dm: Int, after: Boolean) { sm = m; sdwm = dwm; sdw = dw; st = t; sdm = dm; sa = after }
        fun setEnd(m: Int, dwm: Int, dw: Int, t: Int, dm: Int, after: Boolean) { em = m; edwm = dwm; edw = dw; et = t; edm = dm; ea = after }
        fun applyTo(zone: SimpleTimeZoneCore) {
            if (sy != -1) zone.setStartYear(sy)
            if (sm != -1) {
                if (sdm == -1) zone.setStartRule(sm, sdwm, sdw, st)
                else if (sdw == -1) zone.setStartRule(sm, sdm, st)
                else zone.setStartRule(sm, sdm, sdw, st, sa)
            }
            if (em != -1) {
                if (edm == -1) zone.setEndRule(em, edwm, edw, et)
                else if (edw == -1) zone.setEndRule(em, edm, et)
                else zone.setEndRule(em, edm, edw, et, ea)
            }
        }
    }
    companion object {
        const val WALL_TIME = 0
        const val STANDARD_TIME = 1
        const val UTC_TIME = 2
        private const val DOM_MODE = 1
        private const val DOW_IN_MONTH_MODE = 2
        private const val DOW_GE_DOM_MODE = 3
        private const val DOW_LE_DOM_MODE = 4
        private val assertionsEnabled = simpleTimeZoneAssertionsEnabled()
        private val MONTH_LENGTH = intArrayOf(31,29,31,30,31,30,31,31,30,31,30,31)
    }
}

internal expect fun simpleTimeZoneAssertionsEnabled(): Boolean
