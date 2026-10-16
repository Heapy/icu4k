// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.impl.number.javaCompatibleDoubleString
import io.github.heapy.icu4k.util.*
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/** Olson resource-backed state and transition engine, independent of the public TimeZone front end. */
internal class OlsonTimeZoneCore private constructor(
    id: String?, private val clock: () -> Long, private val owner: OwnerQueries?, @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    interface OwnerQueries {
        fun getID(): String?
        fun isFrozen(): Boolean
        fun getRawOffset(): Int
        fun useDaylightTime(): Boolean
        fun getOffset(date: Long, local: Boolean, offsets: IntArray?)
        fun getOffset(era: Int, year: Int, month: Int, day: Int, dow: Int, millis: Int, monthLength: Int): Int
        fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition?
        fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition?
        fun getSimpleTimeZoneRulesNear(date: Long): Array<TimeZoneRule?>?
    }
    private var id = id!!
    private var transitionCount = 0
    private var typeCount = 0
    private var transitionTimes64: LongArray? = null
    private var typeOffsets = intArrayOf()
    private var typeMapData: ByteArray? = null
    private var finalStartYear = Int.MAX_VALUE
    private var finalStartMillis = Double.MAX_VALUE
    private var finalZone: SimpleTimeZoneCore? = null
    @Volatile private var canonicalID: String? = null
    private val lock = createAvailabilityLock()
    private var initialRule: InitialTimeZoneRule? = null
    private var firstTZTransition: TimeZoneTransition? = null
    private var firstTZTransitionIdx = 0
    private var firstFinalTZTransition: TimeZoneTransition? = null
    private var historicRules: Array<TimeArrayTimeZoneRule?>? = null
    private var finalZoneWithStartYear: SimpleTimeZoneCore? = null
    private var transitionRulesInitialized = false
    @Volatile private var frozen = false

    constructor(top: UResourceBundle?, resource: UResourceBundle?, id: String?, clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }, owner: OwnerQueries? = null) : this(id, clock, owner, Unit) {
        construct(top, resource)
    }
    constructor(id: String?, clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }, owner: OwnerQueries? = null) : this(id, clock, owner, Unit) {
        val top = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "zoneinfo64")
        construct(top, ZoneMetaCore.openOlsonResource(top, id))
    }
    private fun construct(top: UResourceBundle?, resource: UResourceBundle?) {
        if (top == null || resource == null) throw IllegalArgumentException()
        fun optional(key: String): IntArray? = try { resource.get(key).getIntVector() } catch (_: MissingResourceException) { null }
        val pre = optional("transPre32")
        if (pre != null) {
            if (pre.size % 2 != 0) invalidFormat()
            transitionCount += pre.size / 2
        }
        val middle = optional("trans")
        if (middle != null) transitionCount += middle.size
        val post = optional("transPost32")
        if (post != null) {
            if (post.size % 2 != 0) invalidFormat()
            transitionCount += post.size / 2
        }
        if (transitionCount > 0) {
            transitionTimes64 = LongArray(transitionCount)
            var index = 0
            if (pre != null) for (i in 0 until pre.size / 2) transitionTimes64!![index++] =
                ((pre[i * 2].toLong() and 0xffffffffL) shl 32) or (pre[i * 2 + 1].toLong() and 0xffffffffL)
            if (middle != null) for (value in middle) transitionTimes64!![index++] = value.toLong()
            if (post != null) for (i in 0 until post.size / 2) transitionTimes64!![index++] =
                ((post[i * 2].toLong() and 0xffffffffL) shl 32) or (post[i * 2 + 1].toLong() and 0xffffffffL)
        }
        typeOffsets = resource.get("typeOffsets").getIntVector()
        if (typeOffsets.size < 2 || typeOffsets.size > 0x7ffe || typeOffsets.size % 2 != 0) invalidFormat()
        typeCount = typeOffsets.size / 2
        if (transitionCount > 0) {
            typeMapData = resource.get("typeMap").getBinary(null)
            if (typeMapData!!.size != transitionCount) invalidFormat()
        }
        var ruleID: String? = null
        try {
            ruleID = resource.getString("finalRule")
            val raw = resource.get("finalRaw").getInt() * Grego.MILLIS_PER_SECOND
            val data = top.get("Rules").get(ruleID).getIntVector()
            if (data.size != 11) invalidFormat()
            finalZone = SimpleTimeZoneCore(raw, id, data[0], data[1], data[2], data[3] * 1000, data[4],
                data[5], data[6], data[7], data[8] * 1000, data[9], data[10] * 1000)
            finalStartYear = resource.get("finalYear").getInt()
            // Multiplication is Long before widening to the stored Double boundary.
            finalStartMillis = (Grego.fieldsToDay(finalStartYear, 0, 1) * Grego.MILLIS_PER_DAY).toDouble()
        } catch (_: MissingResourceException) { if (ruleID != null) invalidFormat() }
    }
    private fun invalidFormat(): Nothing = throw IllegalArgumentException("Invalid Format")
    private fun queryID(): String? = if (owner == null) id else owner.getID()
    private fun queryFrozen(): Boolean = if (owner == null) frozen else owner.isFrozen()
    private fun queryRaw(): Int = if (owner == null) getRawOffset() else owner.getRawOffset()
    private fun queryUseDST(): Boolean = if (owner == null) useDaylightTime() else owner.useDaylightTime()
    private fun queryOffset(date: Long, local: Boolean, offsets: IntArray?) {
        if (owner == null) getOffset(date, local, offsets) else owner.getOffset(date, local, offsets)
    }
    private fun queryNext(base: Long, inclusive: Boolean): TimeZoneTransition? = if (owner == null) getNextTransition(base, inclusive) else owner.getNextTransition(base, inclusive)
    private fun queryPrevious(base: Long, inclusive: Boolean): TimeZoneTransition? = if (owner == null) getPreviousTransition(base, inclusive) else owner.getPreviousTransition(base, inclusive)
    private fun querySimpleRules(date: Long): Array<TimeZoneRule?>? = if (owner == null) basicCore().getSimpleTimeZoneRulesNear(date) else owner.getSimpleTimeZoneRulesNear(date)
    fun basicCore(): BasicTimeZoneCore = object : BasicTimeZoneCore() {
        override fun getID(): String? = queryID()
        override fun getOffset(date: Long, local: Boolean, offsets: IntArray) = queryOffset(date, local, offsets)
        override fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = queryNext(base, inclusive)
        override fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = queryPrevious(base, inclusive)
        override fun getTimeZoneRules(): Array<TimeZoneRule?> = this@OlsonTimeZoneCore.getTimeZoneRules()
    }
    fun getID(): String = id
    fun getCanonicalID(): String? {
        if (canonicalID == null) lock.withLock {
            if (canonicalID == null) {
                canonicalID = canonical(queryID())
                if (assertionsEnabled && canonicalID == null) throw AssertionError()
                if (canonicalID == null) canonicalID = queryID()
            }
        }
        return canonicalID
    }
    private fun canonical(value: String?): String? = when {
        value.isNullOrEmpty() -> null
        value == "Etc/Unknown" -> value
        else -> ZoneMetaCore.getCanonicalCLDRID(value) ?: ZoneMetaCore.getCustomID(value)
    }
    fun setID(value: String?, baseSetID: ((String) -> Unit)? = null) {
        if (queryFrozen()) throw UnsupportedOperationException("Attempt to modify a frozen OlsonTimeZone instance.")
        if (canonicalID == null) {
            canonicalID = canonical(queryID())
            if (assertionsEnabled && canonicalID == null) throw AssertionError()
            if (canonicalID == null) canonicalID = queryID()
        }
        finalZone?.setID(value)
        val newID = value ?: throw NullPointerException()
        if (baseSetID != null) baseSetID(newID)
        else if (queryFrozen()) throw UnsupportedOperationException("Attempt to modify a frozen TimeZone instance.")
        id = newID
        transitionRulesInitialized = false
    }
    fun getOffset(era: Int, year: Int, month: Int, day: Int, dow: Int, millis: Int): Int {
        if (month !in 0..11) throw IllegalArgumentException("Month is not in the legal range: $month")
        val length = Grego.monthLength(year, month)
        return if (owner == null) getOffset(era, year, month, day, dow, millis, length)
            else owner.getOffset(era, year, month, day, dow, millis, length)
    }
    fun getOffset(era: Int, originalYear: Int, month: Int, day: Int, dow: Int, millis: Int, monthLength: Int): Int {
        if ((era != 0 && era != 1) || month !in 0..11 || day < 1 || day > monthLength || dow !in 1..7 ||
            millis !in 0 until Grego.MILLIS_PER_DAY || monthLength !in 28..31) throw IllegalArgumentException()
        val year = if (era == 0) -originalYear else originalYear
        val final = finalZone
        if (final != null && year >= finalStartYear) return final.getOffset(era, year, month, day, dow, millis)
        val time = Grego.fieldsToDay(year, month, day) * Grego.MILLIS_PER_DAY + millis
        val offsets = IntArray(2)
        historicalOffset(time, true, 3, 1, offsets)
        return offsets[0] + offsets[1]
    }
    fun getOffset(date: Long, local: Boolean, offsets: IntArray?) {
        val final = finalZone
        if (final != null && date.toDouble() >= finalStartMillis) final.getOffset(date, local, offsets)
        else historicalOffset(date, local, 4, 12, offsets)
    }
    fun getOffsetFromLocal(date: Long, nonExisting: Int, duplicated: Int, offsets: IntArray?) {
        val final = finalZone
        if (final != null && date.toDouble() >= finalStartMillis) final.getOffsetFromLocal(date, nonExisting, duplicated, offsets)
        else historicalOffset(date, true, nonExisting, duplicated, offsets)
    }
    private fun write(offsets: IntArray?, index: Int, value: Int) {
        val target = offsets ?: throw NullPointerException()
        if (index !in target.indices) throw IndexOutOfBoundsException()
        target[index] = value
    }
    private fun typeIndex(index: Int): Int {
        if (index < 0) return 0
        val data = typeMapData ?: throw NullPointerException()
        if (index !in data.indices) throw IndexOutOfBoundsException()
        return data[index].toInt() and 255
    }
    private fun offset(index: Int): Int {
        if (index !in typeOffsets.indices) throw IndexOutOfBoundsException()
        return typeOffsets[index]
    }
    private fun rawOffsetAt(index: Int): Int = offset(typeIndex(index) * 2)
    private fun dstOffsetAt(index: Int): Int = offset(typeIndex(index) * 2 + 1)
    private fun zoneOffsetAt(index: Int): Int { val type = typeIndex(index) * 2; return offset(type) + offset(type + 1) }
    private fun historicRule(index: Int): TimeArrayTimeZoneRule? {
        val rules = historicRules ?: throw NullPointerException()
        if (index !in rules.indices) throw IndexOutOfBoundsException()
        return rules[index]
    }
    private fun historicalOffset(date: Long, local: Boolean, nonExisting: Int, duplicated: Int, offsets: IntArray?) {
        if (transitionCount != 0) {
            val second = Grego.floorDivide(date, 1000L)
            if (!local && second < transitionTimes64!![0]) {
                write(offsets, 0, offset(0) * 1000); write(offsets, 1, offset(1) * 1000)
            } else {
                var index = transitionCount - 1
                while (index >= 0) {
                    var transition = transitionTimes64!![index]
                    if (local && second >= transition - 86400) {
                        val before = zoneOffsetAt(index - 1)
                        val dstBefore = dstOffsetAt(index - 1) != 0
                        val after = zoneOffsetAt(index)
                        val dstAfter = dstOffsetAt(index) != 0
                        val dstToStd = dstBefore && !dstAfter
                        val stdToDst = !dstBefore && dstAfter
                        transition += if (after - before >= 0) {
                            when {
                                (nonExisting and 3) == 1 && dstToStd || (nonExisting and 3) == 3 && stdToDst -> before
                                (nonExisting and 3) == 1 && stdToDst || (nonExisting and 3) == 3 && dstToStd -> after
                                (nonExisting and 12) == 12 -> before
                                else -> after
                            }
                        } else {
                            when {
                                (duplicated and 3) == 1 && dstToStd || (duplicated and 3) == 3 && stdToDst -> after
                                (duplicated and 3) == 1 && stdToDst || (duplicated and 3) == 3 && dstToStd -> before
                                (duplicated and 12) == 4 -> before
                                else -> after
                            }
                        }
                    }
                    if (second >= transition) break
                    --index
                }
                write(offsets, 0, rawOffsetAt(index) * 1000); write(offsets, 1, dstOffsetAt(index) * 1000)
            }
        } else { write(offsets, 0, offset(0) * 1000); write(offsets, 1, offset(1) * 1000) }
    }
    fun getRawOffset(): Int { val offsets = IntArray(2); queryOffset(clock(), false, offsets); return offsets[0] }
    fun useDaylightTime(): Boolean {
        val current = clock()
        val final = finalZone
        if (final != null && current.toDouble() >= finalStartMillis) return final.useDaylightTime()
        val year = Grego.timeToYear(current)
        val start = Grego.fieldsToDay(year, 0, 1) * 86400
        val limit = Grego.fieldsToDay(year + 1, 0, 1) * 86400
        for (i in 0 until transitionCount) {
            if (transitionTimes64!![i] >= limit) break
            if (transitionTimes64!![i] >= start && dstOffsetAt(i) != 0 || transitionTimes64!![i] > start && i > 0 && dstOffsetAt(i - 1) != 0) return true
        }
        return false
    }
    fun observesDaylightTime(): Boolean {
        val current = clock()
        val final = finalZone
        if (final != null) {
            if (final.useDaylightTime()) return true
            if (current.toDouble() >= finalStartMillis) return false
        }
        val currentSecond = Grego.floorDivide(current, 1000L)
        var index = transitionCount - 1
        if (dstOffsetAt(index) != 0) return true
        while (index >= 0) {
            if (transitionTimes64!![index] <= currentSecond) break
            if (dstOffsetAt(index - 1) != 0) return true
            --index
        }
        return false
    }
    fun getDSTSavings(): Int = finalZone?.getDSTSavings() ?: if (queryUseDST()) 3_600_000 else 0
    fun inDaylightTime(date: Date?): Boolean {
        val offsets = IntArray(2); queryOffset(date!!.getTime(), false, offsets); return offsets[1] != 0
    }
    fun setRawOffset(value: Int) {
        if (queryFrozen()) throw UnsupportedOperationException("Attempt to modify a frozen OlsonTimeZone instance.")
        if (queryRaw() == value) return
        val current = clock()
        if (current.toDouble() < finalStartMillis) {
            val simple = SimpleTimeZoneCore(value, queryID())
            val usesDST = queryUseDST()
            if (usesDST) {
                var rules = querySimpleRules(current)
                if (rules!!.size != 3) {
                    val transition = queryPrevious(current, false)
                    if (transition != null) rules = querySimpleRules(transition.getTime() - 1)
                }
                if (rules!!.size == 3 && rules[1] is AnnualTimeZoneRule && rules[2] is AnnualTimeZoneRule) {
                    val r1 = rules[1] as AnnualTimeZoneRule; val r2 = rules[2] as AnnualTimeZoneRule
                    val offset1 = r1.getRawOffset() + r1.getDSTSavings(); val offset2 = r2.getRawOffset() + r2.getDSTSavings()
                    val start: DateTimeRule?; val end: DateTimeRule?; val savings: Int
                    if (offset1 > offset2) { start = r1.getRule(); end = r2.getRule(); savings = offset1 - offset2 }
                    else { start = r2.getRule(); end = r1.getRule(); savings = offset2 - offset1 }
                    simple.setStartRule(start!!.getRuleMonth(), start.getRuleWeekInMonth(), start.getRuleDayOfWeek(), start.getRuleMillisInDay())
                    simple.setEndRule(end!!.getRuleMonth(), end.getRuleWeekInMonth(), end.getRuleDayOfWeek(), end.getRuleMillisInDay())
                    simple.setDSTSavings(savings)
                } else {
                    simple.setStartRule(0, 1, 0); simple.setEndRule(11, 31, Grego.MILLIS_PER_DAY - 1)
                }
            }
            finalStartYear = Grego.timeToYear(current)
            // Retain the source's day-valued boundary here (no MILLIS_PER_DAY multiplier).
            finalStartMillis = Grego.fieldsToDay(finalStartYear, 0, 1).toDouble()
            if (usesDST) simple.setStartYear(finalStartYear)
            finalZone = simple
        } else finalZone!!.setRawOffset(value)
        transitionRulesInitialized = false
    }
    private fun initTransitionRules() = lock.withLock {
        if (transitionRulesInitialized) return@withLock
        initialRule = null; firstTZTransition = null; firstFinalTZTransition = null; historicRules = null
        firstTZTransitionIdx = 0; finalZoneWithStartYear = null
        val stdName = queryID() + "(STD)"; val dstName = queryID() + "(DST)"
        var raw = offset(0) * 1000; var dst = offset(1) * 1000
        initialRule = InitialTimeZoneRule(if (dst == 0) stdName else dstName, raw, dst)
        if (transitionCount > 0) {
            var index = 0
            while (index < transitionCount) {
                if (typeIndex(index) != 0) break
                ++firstTZTransitionIdx; ++index
            }
            if (index != transitionCount) {
                val times = LongArray(transitionCount)
                for (type in 0 until typeCount) {
                    var count = 0
                    for (i in firstTZTransitionIdx until transitionCount) if (type == typeIndex(i)) {
                        val time = transitionTimes64!![i] * 1000
                        if (time.toDouble() < finalStartMillis) times[count++] = time
                    }
                    if (count > 0) {
                        val starts = times.copyOf(count)
                        raw = offset(type * 2) * 1000; dst = offset(type * 2 + 1) * 1000
                        if (historicRules == null) historicRules = arrayOfNulls(typeCount)
                        historicRules!![type] = TimeArrayTimeZoneRule(if (dst == 0) stdName else dstName, raw, dst, starts, DateTimeRule.UTC_TIME)
                    }
                }
                firstTZTransition = TimeZoneTransition(transitionTimes64!![firstTZTransitionIdx] * 1000, initialRule, historicRule(typeIndex(firstTZTransitionIdx)))
            }
        }
        val final = finalZone
        if (final != null) {
            var startTime = finalStartMillis.toLong()
            val firstFinalRule: TimeZoneRule?
            if (final.useDaylightTime()) {
                finalZoneWithStartYear = final.clone()
                finalZoneWithStartYear!!.setStartYear(finalStartYear)
                val transition = finalZoneWithStartYear!!.getNextTransition(startTime, false)
                firstFinalRule = transition!!.getTo(); startTime = transition.getTime()
            } else {
                finalZoneWithStartYear = final
                firstFinalRule = TimeArrayTimeZoneRule(final.getID(), final.getRawOffset(), 0, longArrayOf(startTime), DateTimeRule.UTC_TIME)
            }
            var previous: TimeZoneRule? = null
            if (transitionCount > 0) previous = historicRule(typeIndex(transitionCount - 1))
            if (previous == null) previous = initialRule
            firstFinalTZTransition = TimeZoneTransition(startTime, previous, firstFinalRule)
        }
        transitionRulesInitialized = true
    }
    fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition? {
        initTransitionRules()
        val final = finalZone
        if (final != null) {
            if (inclusive && base == firstFinalTZTransition!!.getTime()) return firstFinalTZTransition
            if (base >= firstFinalTZTransition!!.getTime()) return if (final.useDaylightTime()) finalZoneWithStartYear!!.getNextTransition(base, inclusive) else null
        }
        if (historicRules != null) {
            var index = transitionCount - 1
            while (index >= firstTZTransitionIdx) {
                val time = transitionTimes64!![index] * 1000
                if (base > time || !inclusive && base == time) break
                --index
            }
            if (index == transitionCount - 1) return firstFinalTZTransition
            if (index < firstTZTransitionIdx) return firstTZTransition
            val to = historicRule(typeIndex(index + 1)); val from = historicRule(typeIndex(index))
            val start = transitionTimes64!![index + 1] * 1000
            val fromName = from!!.getName(); val toName = to!!.getName()
            if (fromName!!.equals(toName) && from.getRawOffset() == to.getRawOffset() && from.getDSTSavings() == to.getDSTSavings()) return queryNext(start, false)
            return TimeZoneTransition(start, from, to)
        }
        return null
    }
    fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition? {
        initTransitionRules()
        val final = finalZone
        if (final != null) {
            if (inclusive && base == firstFinalTZTransition!!.getTime()) return firstFinalTZTransition
            if (base > firstFinalTZTransition!!.getTime()) return if (final.useDaylightTime()) finalZoneWithStartYear!!.getPreviousTransition(base, inclusive) else firstFinalTZTransition
        }
        if (historicRules != null) {
            var index = transitionCount - 1
            while (index >= firstTZTransitionIdx) {
                val time = transitionTimes64!![index] * 1000
                if (base > time || inclusive && base == time) break
                --index
            }
            if (index < firstTZTransitionIdx) return null
            if (index == firstTZTransitionIdx) return firstTZTransition
            val to = historicRule(typeIndex(index)); val from = historicRule(typeIndex(index - 1))
            val start = transitionTimes64!![index] * 1000
            val fromName = from!!.getName(); val toName = to!!.getName()
            if (fromName!!.equals(toName) && from.getRawOffset() == to.getRawOffset() && from.getDSTSavings() == to.getDSTSavings()) return queryPrevious(start, false)
            return TimeZoneTransition(start, from, to)
        }
        return null
    }
    fun getTimeZoneRules(): Array<TimeZoneRule?> {
        initTransitionRules()
        val result = mutableListOf<TimeZoneRule?>()
        result.add(initialRule)
        historicRules?.forEach { if (it != null) result.add(it) }
        val final = finalZone
        if (final != null) {
            if (final.useDaylightTime()) {
                val rules = finalZoneWithStartYear!!.getTimeZoneRules()
                result.add(rules[1]); result.add(rules[2])
            } else result.add(TimeArrayTimeZoneRule(queryID() + "(STD)", finalZone!!.getRawOffset(), 0, longArrayOf(finalStartMillis.toLong()), DateTimeRule.UTC_TIME))
        }
        return result.toTypedArray()
    }
    fun hasSameRules(other: OlsonTimeZoneCore?, compareBaseRules: Boolean = true): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (compareBaseRules && (queryRaw() != other.queryRaw() || queryUseDST() != other.queryUseDST())) return false
        if (finalZone == null) { if (other.finalZone != null) return false }
        else if (other.finalZone == null || finalStartYear != other.finalStartYear || !finalZone!!.hasSameRules(other.finalZone)) return false
        return transitionCount == other.transitionCount && transitionTimes64.contentEquals(other.transitionTimes64) && typeCount == other.typeCount &&
            typeMapData.contentEquals(other.typeMapData) && typeOffsets.contentEquals(other.typeOffsets)
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OlsonTimeZoneCore || id != other.id) return false
        return typeMapData.contentEquals(other.typeMapData) || (finalStartYear == other.finalStartYear &&
            ((finalZone == null && other.finalZone == null) || (finalZone != null && other.finalZone != null && finalZone == other.finalZone) &&
            transitionCount == other.transitionCount && typeCount == other.typeCount && transitionTimes64.contentEquals(other.transitionTimes64) &&
            typeOffsets.contentEquals(other.typeOffsets) && typeMapData.contentEquals(other.typeMapData)))
    }
    override fun hashCode(): Int {
        var result = (finalStartYear.toLong() xor ((finalStartYear ushr 4) + transitionCount).toLong() xor
            ((transitionCount ushr 6) + typeCount).toLong() xor ((typeCount ushr 8).toLong() + finalStartMillis.toBits() +
            (finalZone?.hashCode() ?: 0) + id.hashCode())).toInt()
        transitionTimes64?.forEach { result = (result.toLong() + (it xor (it ushr 8))).toInt() }
        typeOffsets.forEach { result += it xor (it ushr 8) }
        typeMapData?.forEach { result += it.toInt() and 255 }
        return result
    }
    /** Source dump suffix; the public owner supplies its Object.toString prefix. */
    fun describe(parent: String): String = buildString {
        append(parent); append("[transitionCount="); append(transitionCount); append(",typeCount="); append(typeCount)
        append(",transitionTimes="); append(transitionTimes64?.joinToString(",", "[", "]") ?: "null")
        append(",typeOffsets="); append(typeOffsets.joinToString(",", "[", "]"))
        append(",typeMapData="); append(typeMapData?.joinToString(",", "[") ?: "null")
        append(",finalStartYear="); append(finalStartYear); append(",finalStartMillis="); append(javaCompatibleDoubleString(finalStartMillis))
        append(",finalZone="); append(finalZone); append(']')
    }
    fun isFrozen(): Boolean = frozen
    fun freeze(): OlsonTimeZoneCore { frozen = true; return this }
    fun clone(): OlsonTimeZoneCore = if (queryFrozen()) this else cloneAsThawed()
    fun cloneAsThawed(newOwner: OwnerQueries? = owner): OlsonTimeZoneCore {
        val copy = OlsonTimeZoneCore(id, clock, newOwner, Unit)
        copy.transitionCount = transitionCount; copy.typeCount = typeCount; copy.transitionTimes64 = transitionTimes64
        copy.typeOffsets = typeOffsets; copy.typeMapData = typeMapData; copy.finalStartYear = finalStartYear; copy.finalStartMillis = finalStartMillis
        copy.finalZone = finalZone?.clone(); copy.canonicalID = canonicalID; copy.initialRule = initialRule
        copy.firstTZTransition = firstTZTransition; copy.firstTZTransitionIdx = firstTZTransitionIdx; copy.firstFinalTZTransition = firstFinalTZTransition
        copy.historicRules = historicRules; copy.finalZoneWithStartYear = finalZoneWithStartYear; copy.transitionRulesInitialized = transitionRulesInitialized
        return copy
    }
    companion object { private val assertionsEnabled = olsonTimeZoneAssertionsEnabled() }
}
internal expect fun olsonTimeZoneAssertionsEnabled(): Boolean
