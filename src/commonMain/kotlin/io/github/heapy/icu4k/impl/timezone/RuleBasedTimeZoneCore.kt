// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.util.*
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/** RuleBasedTimeZone state and transition engine, independent of the pending TimeZone front end. */
internal class RuleBasedTimeZoneCore(
    private val initialRule: InitialTimeZoneRule?,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    internal var historicRules: MutableList<TimeZoneRule>? = null
        private set
    internal var finalRules: Array<AnnualTimeZoneRule?>? = null
        private set
    internal var historicTransitions: MutableList<TimeZoneTransition>? = null
        private set
    internal var upToDate = false
        private set
    @Volatile private var frozen = false

    fun addTransitionRule(rule: TimeZoneRule?, frozenCheck: Boolean = isFrozen()) {
        if (frozenCheck) throw UnsupportedOperationException("Attempt to modify a frozen RuleBasedTimeZone instance.")
        if (!rule!!.isTransitionRule()) throw IllegalArgumentException("Rule must be a transition rule")
        if (rule is AnnualTimeZoneRule && rule.getEndYear() == AnnualTimeZoneRule.MAX_YEAR) {
            if (finalRules == null) { finalRules = arrayOfNulls(2); finalRules!![0] = rule }
            else if (finalRules!![1] == null) finalRules!![1] = rule
            else throw IllegalStateException("Too many final rules")
        } else {
            if (historicRules == null) historicRules = ArrayList()
            historicRules!!.add(rule)
        }
        upToDate = false
    }
    fun getOffset(era: Int, originalYear: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int): Int {
        val year = if (era == 0) 1-originalYear else originalYear
        val time = Grego.fieldsToDay(year,month,day)*Grego.MILLIS_PER_DAY+milliseconds
        val offsets = IntArray(2)
        calculateOffset(time,true,LOCAL_DST,LOCAL_STD,offsets)
        return offsets[0]+offsets[1]
    }
    fun getOffset(time: Long, local: Boolean, offsets: IntArray?) = calculateOffset(time,local,LOCAL_FORMER,LOCAL_LATTER,offsets)
    fun getOffsetFromLocal(time: Long, nonExisting: Int, duplicated: Int, offsets: IntArray?) = calculateOffset(time,true,nonExisting,duplicated,offsets)
    fun getRawOffset(query: (Long,Boolean,IntArray?) -> Unit = ::getOffset): Int {
        val now = clock(); val offsets = IntArray(2); query(now,false,offsets); return offsets[0]
    }
    fun inDaylightTime(date: Date?, query: (Long,Boolean,IntArray?) -> Unit = ::getOffset): Boolean {
        val offsets = IntArray(2); query(date!!.getTime(),false,offsets); return offsets[1] != 0
    }
    fun setRawOffset(offsetMillis: Int): Nothing = throw UnsupportedOperationException("setRawOffset in RuleBasedTimeZone is not supported.")
    fun useDaylightTime(
        offsetsQuery: (Long,Boolean,IntArray?) -> Unit = ::getOffset,
        nextQuery: (Long,Boolean) -> TimeZoneTransition? = { b,i -> getNextTransition(b,i) },
    ): Boolean {
        val now = clock(); val offsets = IntArray(2); offsetsQuery(now,false,offsets)
        if (offsets[1] != 0) return true
        val transition = nextQuery(now,false)
        return transition != null && transition.getTo()!!.getDSTSavings() != 0
    }
    fun observesDaylightTime(
        offsetsQuery: (Long,Boolean,IntArray?) -> Unit = ::getOffset,
        nextQuery: (Long,Boolean) -> TimeZoneTransition? = { b,i -> getNextTransition(b,i) },
    ): Boolean {
        var time = clock(); val offsets = IntArray(2); offsetsQuery(time,false,offsets)
        if (offsets[1] != 0) return true
        val checked = if (finalRules == null) null else BooleanArray(finalRules!!.size)
        while (true) {
            val transition = nextQuery(time,false) ?: break
            val toRule = transition.getTo()
            if (toRule!!.getDSTSavings() != 0) return true
            if (checked != null) {
                for (i in finalRules!!.indices) if (finalRules!![i]!!.equals(toRule)) checked[i] = true
                if (checked.count { it } == finalRules!!.size) break
            }
            time = transition.getTime()
        }
        return false
    }
    fun hasSameRules(other: RuleBasedTimeZoneCore?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (!initialRule!!.isEquivalentTo(other.initialRule)) return false
        if (finalRules != null && other.finalRules != null) {
            for (i in finalRules!!.indices) {
                if (finalRules!![i] == null && other.finalRules!![i] == null) continue
                if (finalRules!![i] != null && other.finalRules!![i] != null && finalRules!![i]!!.isEquivalentTo(other.finalRules!![i])) continue
                return false
            }
        } else if (finalRules != null || other.finalRules != null) return false
        if (historicRules != null && other.historicRules != null) {
            if (historicRules!!.size != other.historicRules!!.size) return false
            for (rule in historicRules!!) {
                var found = false
                for (candidate in other.historicRules!!) if (rule.isEquivalentTo(candidate)) { found = true; break }
                if (!found) return false
            }
        } else if (historicRules != null || other.historicRules != null) return false
        return true
    }
    fun getTimeZoneRules(): Array<TimeZoneRule?> {
        val result = ArrayList<TimeZoneRule?>(); result.add(initialRule)
        historicRules?.let { result.addAll(it) }
        finalRules?.let { result.add(it[0]); if (it[1] != null) result.add(it[1]) }
        return result.toTypedArray()
    }
    fun getNextTransition(base: Long, inclusive: Boolean, recurse: ((Long,Boolean) -> TimeZoneTransition?)? = null): TimeZoneTransition? {
        complete()
        val transitions = historicTransitions ?: return null
        var isFinal = false
        var result: TimeZoneTransition?
        var transition = transitions[0]
        var time = transition.getTime()
        if (time > base || inclusive && time == base) result = transition
        else {
            var i = transitions.size-1
            transition = transitions[i]; time = transition.getTime()
            if (inclusive && time == base) result = transition
            else if (time <= base) {
                val finals = finalRules ?: return null
                val start0 = finals[0]!!.getNextStart(base,finals[1]!!.getRawOffset(),finals[1]!!.getDSTSavings(),inclusive)
                val start1 = finals[1]!!.getNextStart(base,finals[0]!!.getRawOffset(),finals[0]!!.getDSTSavings(),inclusive)
                transition = if (start1!!.after(start0)) TimeZoneTransition(start0!!.getTime(),finals[1],finals[0])
                    else TimeZoneTransition(start1.getTime(),finals[0],finals[1])
                result = transition; isFinal = true
            } else {
                i--; var previous = transition
                while (i > 0) {
                    transition = transitions[i]; time = transition.getTime()
                    if (time < base || !inclusive && time == base) break
                    i--; previous = transition
                }
                result = previous
            }
        }
        val from = result.getFrom()!!; val to = result.getTo()!!
        if (from.getRawOffset() == to.getRawOffset() && from.getDSTSavings() == to.getDSTSavings()) {
            if (isFinal) return null
            result = if (recurse == null) getNextTransition(result.getTime(),false) else recurse(result.getTime(),false)
        }
        return result
    }
    fun getPreviousTransition(base: Long, inclusive: Boolean, recurse: ((Long,Boolean) -> TimeZoneTransition?)? = null): TimeZoneTransition? {
        complete()
        val transitions = historicTransitions ?: return null
        var result: TimeZoneTransition?
        var transition = transitions[0]
        var time = transition.getTime()
        if (inclusive && time == base) result = transition
        else if (time >= base) return null
        else {
            var i = transitions.size-1
            transition = transitions[i]; time = transition.getTime()
            if (inclusive && time == base) result = transition
            else if (time < base) {
                val finals = finalRules
                if (finals != null) {
                    val start0 = finals[0]!!.getPreviousStart(base,finals[1]!!.getRawOffset(),finals[1]!!.getDSTSavings(),inclusive)
                    val start1 = finals[1]!!.getPreviousStart(base,finals[0]!!.getRawOffset(),finals[0]!!.getDSTSavings(),inclusive)
                    transition = if (start1!!.before(start0)) TimeZoneTransition(start0!!.getTime(),finals[1],finals[0])
                        else TimeZoneTransition(start1.getTime(),finals[0],finals[1])
                }
                result = transition
            } else {
                i--
                while (i >= 0) {
                    transition = transitions[i]; time = transition.getTime()
                    if (time < base || inclusive && time == base) break
                    i--
                }
                result = transition
            }
        }
        val from = result.getFrom()!!; val to = result.getTo()!!
        if (from.getRawOffset() == to.getRawOffset() && from.getDSTSavings() == to.getDSTSavings())
            result = if (recurse == null) getPreviousTransition(result.getTime(),false) else recurse(result.getTime(),false)
        return result
    }
    private fun complete() {
        if (upToDate) return
        if (finalRules != null && finalRules!![1] == null) throw IllegalStateException("Incomplete final rules")
        if (historicRules != null || finalRules != null) {
            var current: TimeZoneRule? = initialRule
            var lastTime = Grego.MIN_MILLIS
            val history = historicRules
            if (history != null) {
                val done = HashSet<Int>()
                while (true) {
                    val raw = current!!.getRawOffset(); val dst = current!!.getDSTSavings()
                    var nextTime = Grego.MAX_MILLIS
                    var nextRule: TimeZoneRule? = null
                    var cursor = 0
                    while (cursor < history.size) {
                        val i = cursor++
                        if (i in done) continue
                        val rule = history[i]
                        val date = rule.getNextStart(lastTime,raw,dst,false)
                        if (date == null) done.add(i)
                        else {
                            if (rule === current || sameName(rule.getName(),current!!.getName()) && rule.getRawOffset() == current!!.getRawOffset() && rule.getDSTSavings() == current!!.getDSTSavings()) continue
                            val time = date.getTime()
                            if (time < nextTime) { nextTime = time; nextRule = rule }
                        }
                    }
                    if (nextRule == null && history.indices.all { it in done }) break
                    finalRules?.let { finals ->
                        for (i in 0 until 2) {
                            if (finals[i] === current) continue
                            val date = finals[i]!!.getNextStart(lastTime,raw,dst,false)
                            if (date != null) {
                                val time = date.getTime()
                                if (time < nextTime) { nextTime = time; nextRule = finals[i] }
                            }
                        }
                    }
                    if (nextRule == null) break
                    if (historicTransitions == null) historicTransitions = ArrayList()
                    historicTransitions!!.add(TimeZoneTransition(nextTime,current,nextRule))
                    lastTime = nextTime; current = nextRule
                }
            }
            finalRules?.let { finals ->
                if (historicTransitions == null) historicTransitions = ArrayList()
                var d0 = finals[0]!!.getNextStart(lastTime,current!!.getRawOffset(),current!!.getDSTSavings(),false)
                var d1 = finals[1]!!.getNextStart(lastTime,current!!.getRawOffset(),current!!.getDSTSavings(),false)
                if (d1!!.after(d0)) {
                    historicTransitions!!.add(TimeZoneTransition(d0!!.getTime(),current,finals[0]))
                    d1 = finals[1]!!.getNextStart(d0.getTime(),finals[0]!!.getRawOffset(),finals[0]!!.getDSTSavings(),false)
                    historicTransitions!!.add(TimeZoneTransition(d1!!.getTime(),finals[0],finals[1]))
                } else {
                    historicTransitions!!.add(TimeZoneTransition(d1.getTime(),current,finals[1]))
                    d0 = finals[0]!!.getNextStart(d1.getTime(),finals[1]!!.getRawOffset(),finals[1]!!.getDSTSavings(),false)
                    historicTransitions!!.add(TimeZoneTransition(d0!!.getTime(),finals[1],finals[0]))
                }
            }
        }
        upToDate = true
    }
    private fun calculateOffset(time: Long, local: Boolean, nonExisting: Int, duplicated: Int, offsets: IntArray?) {
        complete()
        var rule: TimeZoneRule? = null
        val transitions = historicTransitions
        if (transitions == null) rule = initialRule
        else {
            val first = transitionTime(transitions[0],local,nonExisting,duplicated)
            if (time < first) rule = initialRule
            else {
                var i = transitions.size-1
                val last = transitionTime(transitions[i],local,nonExisting,duplicated)
                if (time > last) {
                    if (finalRules != null) rule = findRuleInFinal(time,local,nonExisting,duplicated)
                    if (rule == null) rule = transitions[i].getTo()
                } else {
                    while (i >= 0) { if (time >= transitionTime(transitions[i],local,nonExisting,duplicated)) break; i-- }
                    rule = checkedTransition(transitions,i).getTo()
                }
            }
        }
        writeOffset(offsets,0,rule!!.getRawOffset())
        writeOffset(offsets,1,rule.getDSTSavings())
    }
    private fun findRuleInFinal(time: Long, local: Boolean, nonExisting: Int, duplicated: Int): TimeZoneRule? {
        val finals = finalRules
        if (finals == null || finals.size != 2 || finals[0] == null || finals[1] == null) return null
        var base = time
        if (local) base -= getLocalDelta(finals[1]!!.getRawOffset(),finals[1]!!.getDSTSavings(),finals[0]!!.getRawOffset(),finals[0]!!.getDSTSavings(),nonExisting,duplicated)
        val start0 = finals[0]!!.getPreviousStart(base,finals[1]!!.getRawOffset(),finals[1]!!.getDSTSavings(),true)
        base = time
        if (local) base -= getLocalDelta(finals[0]!!.getRawOffset(),finals[0]!!.getDSTSavings(),finals[1]!!.getRawOffset(),finals[1]!!.getDSTSavings(),nonExisting,duplicated)
        val start1 = finals[1]!!.getPreviousStart(base,finals[0]!!.getRawOffset(),finals[0]!!.getDSTSavings(),true)
        if (start0 == null || start1 == null) return if (start0 != null) finals[0] else if (start1 != null) finals[1] else null
        return if (start0.after(start1)) finals[0] else finals[1]
    }
    fun isFrozen(): Boolean = frozen
    fun freeze(): RuleBasedTimeZoneCore { complete(); frozen = true; return this }
    fun clone(frozenCheck: Boolean = isFrozen()): RuleBasedTimeZoneCore = if (frozenCheck) this else cloneAsThawed()
    fun cloneAsThawed(): RuleBasedTimeZoneCore = RuleBasedTimeZoneCore(initialRule,clock).also {
        it.historicRules = historicRules?.toMutableList(); it.finalRules = finalRules?.copyOf()
        it.historicTransitions = historicTransitions; it.upToDate = upToDate
    }
    companion object {
        const val LOCAL_STD = 0x01
        const val LOCAL_DST = 0x03
        const val LOCAL_FORMER = 0x04
        const val LOCAL_LATTER = 0x0c
        private fun sameName(a: String?, b: String?): Boolean = a!!.equals(b)
        private fun checkedTransition(list: List<TimeZoneTransition>,i: Int): TimeZoneTransition {
            if (i < 0 || i >= list.size) throw IndexOutOfBoundsException()
            return list[i]
        }
        private fun writeOffset(a: IntArray?, i: Int, value: Int) {
            val array = a!!
            if (i < 0 || i >= array.size) throw IndexOutOfBoundsException()
            array[i] = value
        }
        private fun transitionTime(t: TimeZoneTransition, local: Boolean, nonExisting: Int, duplicated: Int): Long {
            var time = t.getTime()
            if (local) time += getLocalDelta(t.getFrom()!!.getRawOffset(),t.getFrom()!!.getDSTSavings(),t.getTo()!!.getRawOffset(),t.getTo()!!.getDSTSavings(),nonExisting,duplicated)
            return time
        }
        internal fun getLocalDelta(rawBefore: Int, dstBefore: Int, rawAfter: Int, dstAfter: Int, nonExisting: Int, duplicated: Int): Int {
            val before = rawBefore+dstBefore; val after = rawAfter+dstAfter
            val dstToStd = dstBefore != 0 && dstAfter == 0
            val stdToDst = dstBefore == 0 && dstAfter != 0
            return if (after-before >= 0) {
                if ((nonExisting and 3 == LOCAL_STD && dstToStd) || (nonExisting and 3 == LOCAL_DST && stdToDst)) before
                else if ((nonExisting and 3 == LOCAL_STD && stdToDst) || (nonExisting and 3 == LOCAL_DST && dstToStd)) after
                else if (nonExisting and 12 == LOCAL_LATTER) before else after
            } else {
                if ((duplicated and 3 == LOCAL_STD && dstToStd) || (duplicated and 3 == LOCAL_DST && stdToDst)) after
                else if ((duplicated and 3 == LOCAL_STD && stdToDst) || (duplicated and 3 == LOCAL_DST && dstToStd)) before
                else if (duplicated and 12 == LOCAL_FORMER) before else after
            }
        }
    }
}
