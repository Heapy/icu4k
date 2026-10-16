// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.AnnualTimeZoneRule
import io.github.heapy.icu4k.util.DateTimeRule
import io.github.heapy.icu4k.util.InitialTimeZoneRule
import io.github.heapy.icu4k.util.TimeArrayTimeZoneRule
import io.github.heapy.icu4k.util.TimeZoneRule
import io.github.heapy.icu4k.util.TimeZoneTransition

/** BasicTimeZone algorithms, with virtual transition/offset queries supplied by the owning zone. */
internal abstract class BasicTimeZoneCore {
    abstract fun getID(): String?
    abstract fun getOffset(date: Long, local: Boolean, offsets: IntArray)
    abstract fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition?
    abstract fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition?
    abstract fun getTimeZoneRules(): Array<TimeZoneRule?>?

    fun hasEquivalentTransitions(other: BasicTimeZoneCore?, start: Long, end: Long, ignoreDstAmount: Boolean = false): Boolean {
        if (this === other) return true
        if (other == null) return false
        val offsets1 = IntArray(2); val offsets2 = IntArray(2)
        getOffset(start,false,offsets1); other.getOffset(start,false,offsets2)
        if (ignoreDstAmount) {
            if (offsets1[0]+offsets1[1] != offsets2[0]+offsets2[1] ||
                (offsets1[1] != 0 && offsets2[1] == 0) || (offsets1[1] == 0 && offsets2[1] != 0)) return false
        } else if (offsets1[0] != offsets2[0] || offsets1[1] != offsets2[1]) return false
        var time = start
        while (true) {
            var tr1 = getNextTransition(time,false)
            var tr2 = other.getNextTransition(time,false)
            if (ignoreDstAmount) {
                while (true) {
                    if (tr1 != null && tr1.getTime() <= end &&
                        tr1.getFrom()!!.getRawOffset()+tr1.getFrom()!!.getDSTSavings() == tr1.getTo()!!.getRawOffset()+tr1.getTo()!!.getDSTSavings() &&
                        tr1.getFrom()!!.getDSTSavings() != 0 && tr1.getTo()!!.getDSTSavings() != 0) tr1 = getNextTransition(tr1.getTime(),false) else break
                }
                while (true) {
                    if (tr2 != null && tr2.getTime() <= end &&
                        tr2.getFrom()!!.getRawOffset()+tr2.getFrom()!!.getDSTSavings() == tr2.getTo()!!.getRawOffset()+tr2.getTo()!!.getDSTSavings() &&
                        tr2.getFrom()!!.getDSTSavings() != 0 && tr2.getTo()!!.getDSTSavings() != 0) tr2 = other.getNextTransition(tr2.getTime(),false) else break
                }
            }
            val inRange1 = tr1 != null && tr1.getTime() <= end
            val inRange2 = tr2 != null && tr2.getTime() <= end
            if (!inRange1 && !inRange2) break
            if (!inRange1 || !inRange2) return false
            if (tr1!!.getTime() != tr2!!.getTime()) return false
            if (ignoreDstAmount) {
                if (tr1.getTo()!!.getRawOffset()+tr1.getTo()!!.getDSTSavings() != tr2.getTo()!!.getRawOffset()+tr2.getTo()!!.getDSTSavings() ||
                    tr1.getTo()!!.getDSTSavings() != 0 && tr2.getTo()!!.getDSTSavings() == 0 ||
                    tr1.getTo()!!.getDSTSavings() == 0 && tr2.getTo()!!.getDSTSavings() != 0) return false
            } else if (tr1.getTo()!!.getRawOffset() != tr2.getTo()!!.getRawOffset() || tr1.getTo()!!.getDSTSavings() != tr2.getTo()!!.getDSTSavings()) return false
            time = tr1.getTime()
        }
        return true
    }

    fun getTimeZoneRules(start: Long): Array<TimeZoneRule?>? {
        val all = getTimeZoneRules()
        var transition = getPreviousTransition(start,true) ?: return all
        val count = all!!.size // Source reads the array length before inspecting the transition.
        val processed = HashSet<Int>()
        val filtered = ArrayList<TimeZoneRule?>()
        val initial = InitialTimeZoneRule(transition.getTo()!!.getName(),transition.getTo()!!.getRawOffset(),transition.getTo()!!.getDSTSavings())
        filtered.add(initial); processed.add(0)
        for (i in 1 until count) if (all[i]!!.getNextStart(start,initial.getRawOffset(),initial.getDSTSavings(),false) == null) processed.add(i)
        var time = start
        var finalStd = false; var finalDst = false
        while (!finalStd || !finalDst) {
            transition = getNextTransition(time,false) ?: break
            time = transition.getTime()
            val toRule = transition.getTo()
            var ruleIndex = 1
            while (ruleIndex < all.size) { if (all[ruleIndex]!!.equals(toRule)) break; ruleIndex++ }
            if (ruleIndex >= all.size) throw IllegalStateException("The rule was not found")
            if (ruleIndex in processed) continue
            if (toRule is TimeArrayTimeZoneRule) {
                var t = start
                var firstTransition: TimeZoneTransition?
                while (true) {
                    firstTransition = getNextTransition(t,false)
                    if (firstTransition == null) break
                    if (firstTransition.getTo()!!.equals(toRule)) break
                    t = firstTransition.getTime()
                }
                if (firstTransition != null) {
                    val first = toRule.getFirstStart(firstTransition.getFrom()!!.getRawOffset(),firstTransition.getFrom()!!.getDSTSavings())
                    if (first!!.getTime() > start) filtered.add(toRule)
                    else {
                        val times = toRule.getStartTimes(); val type = toRule.getTimeType()
                        var i = 0
                        while (i < times.size) {
                            t = times[i]
                            // Preserve the source filter's distinct STANDARD and WALL adjustments.
                            if (type == DateTimeRule.STANDARD_TIME) t -= firstTransition.getFrom()!!.getRawOffset()
                            if (type == DateTimeRule.WALL_TIME) t -= firstTransition.getFrom()!!.getDSTSavings()
                            if (t > start) break
                            i++
                        }
                        if (times.size-i > 0) filtered.add(TimeArrayTimeZoneRule(toRule.getName(),toRule.getRawOffset(),toRule.getDSTSavings(),times.copyOfRange(i,times.size),toRule.getTimeType()))
                    }
                }
            } else if (toRule is AnnualTimeZoneRule) {
                val first = toRule.getFirstStart(transition.getFrom()!!.getRawOffset(),transition.getFrom()!!.getDSTSavings())
                if (first!!.getTime() == transition.getTime()) filtered.add(toRule)
                else filtered.add(AnnualTimeZoneRule(toRule.getName(),toRule.getRawOffset(),toRule.getDSTSavings(),toRule.getRule(),Grego.timeToYear(transition.getTime()),toRule.getEndYear()))
                if (toRule.getEndYear() == AnnualTimeZoneRule.MAX_YEAR) {
                    if (toRule.getDSTSavings() == 0) finalStd = true else finalDst = true
                }
            }
            processed.add(ruleIndex)
        }
        return filtered.toTypedArray()
    }

    fun getSimpleTimeZoneRulesNear(date: Long): Array<TimeZoneRule?> {
        var annual: Array<AnnualTimeZoneRule?>? = null
        val initial: TimeZoneRule
        var tr = getNextTransition(date,false)
        if (tr != null) {
            var name = tr.getFrom()!!.getName()
            var raw = tr.getFrom()!!.getRawOffset()
            var dst = tr.getFrom()!!.getDSTSavings()
            val nextTime = tr.getTime()
            if (((tr.getFrom()!!.getDSTSavings() == 0 && tr.getTo()!!.getDSTSavings() != 0) ||
                (tr.getFrom()!!.getDSTSavings() != 0 && tr.getTo()!!.getDSTSavings() == 0)) && date+MILLIS_PER_YEAR > nextTime) {
                annual = arrayOfNulls(2)
                var fields = Grego.timeToFields(nextTime+tr.getFrom()!!.getRawOffset()+tr.getFrom()!!.getDSTSavings(),null)
                var week = Grego.getDayOfWeekInMonth(fields[0],fields[1],fields[2])
                var dtr = DateTimeRule(fields[1],week,fields[3],fields[5],DateTimeRule.WALL_TIME)
                var second: AnnualTimeZoneRule?
                annual[0] = AnnualTimeZoneRule(tr.getTo()!!.getName(),raw,tr.getTo()!!.getDSTSavings(),dtr,fields[0],AnnualTimeZoneRule.MAX_YEAR)
                if (tr.getTo()!!.getRawOffset() == raw) {
                    tr = getNextTransition(nextTime,false)
                    if (tr != null && ((tr.getFrom()!!.getDSTSavings() == 0 && tr.getTo()!!.getDSTSavings() != 0) ||
                        (tr.getFrom()!!.getDSTSavings() != 0 && tr.getTo()!!.getDSTSavings() == 0)) && nextTime+MILLIS_PER_YEAR > tr.getTime()) {
                        fields = Grego.timeToFields(tr.getTime()+tr.getFrom()!!.getRawOffset()+tr.getFrom()!!.getDSTSavings(),fields)
                        week = Grego.getDayOfWeekInMonth(fields[0],fields[1],fields[2])
                        dtr = DateTimeRule(fields[1],week,fields[3],fields[5],DateTimeRule.WALL_TIME)
                        second = AnnualTimeZoneRule(tr.getTo()!!.getName(),tr.getTo()!!.getRawOffset(),tr.getTo()!!.getDSTSavings(),dtr,fields[0]-1,AnnualTimeZoneRule.MAX_YEAR)
                        val d = second.getPreviousStart(date,tr.getFrom()!!.getRawOffset(),tr.getFrom()!!.getDSTSavings(),true)
                        if (d != null && d.getTime() <= date && raw == tr.getTo()!!.getRawOffset() && dst == tr.getTo()!!.getDSTSavings()) annual[1] = second
                    }
                }
                if (annual[1] == null) {
                    tr = getPreviousTransition(date,true)
                    if (tr != null && ((tr.getFrom()!!.getDSTSavings() == 0 && tr.getTo()!!.getDSTSavings() != 0) ||
                        (tr.getFrom()!!.getDSTSavings() != 0 && tr.getTo()!!.getDSTSavings() == 0))) {
                        fields = Grego.timeToFields(tr.getTime()+tr.getFrom()!!.getRawOffset()+tr.getFrom()!!.getDSTSavings(),fields)
                        week = Grego.getDayOfWeekInMonth(fields[0],fields[1],fields[2])
                        dtr = DateTimeRule(fields[1],week,fields[3],fields[5],DateTimeRule.WALL_TIME)
                        second = AnnualTimeZoneRule(tr.getTo()!!.getName(),raw,dst,dtr,annual[0]!!.getStartYear()-1,AnnualTimeZoneRule.MAX_YEAR)
                        val d = second.getNextStart(date,tr.getFrom()!!.getRawOffset(),tr.getFrom()!!.getDSTSavings(),false)
                        if (d!!.getTime() > nextTime) annual[1] = second
                    }
                }
                if (annual[1] == null) annual = null else {
                    name = annual[0]!!.getName(); raw = annual[0]!!.getRawOffset(); dst = annual[0]!!.getDSTSavings()
                }
            }
            initial = InitialTimeZoneRule(name,raw,dst)
        } else {
            tr = getPreviousTransition(date,true)
            initial = if (tr != null) InitialTimeZoneRule(tr.getTo()!!.getName(),tr.getTo()!!.getRawOffset(),tr.getTo()!!.getDSTSavings())
                else {
                    val offsets = IntArray(2); getOffset(date,false,offsets)
                    InitialTimeZoneRule(getID(),offsets[0],offsets[1])
                }
        }
        return if (annual == null) arrayOf(initial) else arrayOf(initial,annual[0],annual[1])
    }
    companion object { private const val MILLIS_PER_YEAR = 365L*24*60*60*1000 }
}
