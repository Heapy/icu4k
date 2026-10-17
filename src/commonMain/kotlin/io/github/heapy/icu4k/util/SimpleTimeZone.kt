// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.timezone.SimpleTimeZoneCore
import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream
import io.github.heapy.icu4k.text.RbnfObjectStreamField


/** A fixed offset or annually recurring daylight-saving rules. */
open class SimpleTimeZone : BasicTimeZone {
    private var core: SimpleTimeZoneCore
    constructor(rawOffset: Int, id: String?) : super(id) { core = SimpleTimeZoneCore(rawOffset, id, owner = owner()) }
    internal constructor(prototype: SimpleTimeZoneCore) : super(prototype.getID()) { core = prototype.cloneAsThawed(owner()) }
    constructor(rawOffset: Int, id: String?, startMonth: Int, startDay: Int, startDayOfWeek: Int, startTime: Int,
        endMonth: Int, endDay: Int, endDayOfWeek: Int, endTime: Int) :
        this(rawOffset, id, startMonth, startDay, startDayOfWeek, startTime, endMonth, endDay, endDayOfWeek, endTime, 3600000)
    constructor(rawOffset: Int, id: String?, startMonth: Int, startDay: Int, startDayOfWeek: Int, startTime: Int,
        endMonth: Int, endDay: Int, endDayOfWeek: Int, endTime: Int, dstSavings: Int) :
        this(rawOffset, id, startMonth, startDay, startDayOfWeek, startTime, WALL_TIME, endMonth, endDay, endDayOfWeek, endTime, WALL_TIME, dstSavings)
    constructor(rawOffset: Int, id: String?, startMonth: Int, startDay: Int, startDayOfWeek: Int, startTime: Int, startTimeMode: Int,
        endMonth: Int, endDay: Int, endDayOfWeek: Int, endTime: Int, endTimeMode: Int, dstSavings: Int) : super(id) {
        core = SimpleTimeZoneCore(rawOffset, id, startMonth, startDay, startDayOfWeek, startTime, startTimeMode,
            endMonth, endDay, endDayOfWeek, endTime, endTimeMode, dstSavings, owner())
    }
    private fun owner() = object : SimpleTimeZoneCore.OwnerQueries {
        override fun getID() = this@SimpleTimeZone.getID()
        override fun getRawOffset() = this@SimpleTimeZone.getRawOffset()
        override fun getDSTSavings() = this@SimpleTimeZone.getDSTSavings()
        override fun isFrozen() = this@SimpleTimeZone.isFrozen()
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int) =
            this@SimpleTimeZone.getOffset(era, year, month, day, dayOfWeek, millis)
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int, monthLength: Int) =
            this@SimpleTimeZone.getOffset(era, year, month, day, dayOfWeek, millis, monthLength)
    }
    override fun setID(value: String?) = core.setID(value, baseSetID = { super.setID(it) })
    override fun setRawOffset(offsetMillis: Int) = core.setRawOffset(offsetMillis)
    override fun getRawOffset(): Int = core.getRawOffset()
    open fun setStartYear(year: Int) = core.setStartYear(year)
    open fun setStartRule(month: Int, dayOfWeekInMonth: Int, dayOfWeek: Int, time: Int) = core.setStartRule(month, dayOfWeekInMonth, dayOfWeek, time)
    open fun setStartRule(month: Int, dayOfMonth: Int, time: Int) = core.setStartRule(month, dayOfMonth, time)
    open fun setStartRule(month: Int, dayOfMonth: Int, dayOfWeek: Int, time: Int, after: Boolean) = core.setStartRule(month, dayOfMonth, dayOfWeek, time, after)
    open fun setEndRule(month: Int, dayOfWeekInMonth: Int, dayOfWeek: Int, time: Int) = core.setEndRule(month, dayOfWeekInMonth, dayOfWeek, time)
    open fun setEndRule(month: Int, dayOfMonth: Int, time: Int) = core.setEndRule(month, dayOfMonth, time)
    open fun setEndRule(month: Int, dayOfMonth: Int, dayOfWeek: Int, time: Int, after: Boolean) = core.setEndRule(month, dayOfMonth, dayOfWeek, time, after)
    open fun setDSTSavings(millisSavedDuringDST: Int) = core.setDSTSavings(millisSavedDuringDST)
    override fun getDSTSavings(): Int = core.getDSTSavings()
    override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int): Int = core.getOffset(era, year, month, day, dayOfWeek, milliseconds)
    open fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int, monthLength: Int): Int = core.getOffset(era, year, month, day, dayOfWeek, milliseconds, monthLength)
    override fun getOffsetFromLocal(date: Long, nonExistingTimeOpt: LocalOption?, duplicatedTimeOpt: LocalOption?, offsets: IntArray?) =
        core.getOffsetFromLocal(date, getLocalOptionValue(nonExistingTimeOpt), getLocalOptionValue(duplicatedTimeOpt), offsets)
    override fun useDaylightTime(): Boolean = core.useDaylightTime()
    override fun observesDaylightTime(): Boolean = core.observesDaylightTime()
    override fun inDaylightTime(date: Date?): Boolean = GregorianCalendar(this).run { setTime(date); inDaylightTime() }
    override fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = core.getNextTransition(base, inclusive)
    override fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = core.getPreviousTransition(base, inclusive)
    @Suppress("UNCHECKED_CAST")
    override fun getTimeZoneRules(): Array<TimeZoneRule?> = core.getTimeZoneRules() as Array<TimeZoneRule?>
    override fun hasSameRules(other: TimeZone?): Boolean = this === other || other is SimpleTimeZone && core.hasSameRules(other.core)
    override fun equals(other: Any?): Boolean = this === other || other != null && this::class == other::class && core == (other as SimpleTimeZone).core
    override fun hashCode(): Int = core.hashCode()
    override fun toString(): String = core.toString()
    override fun isFrozen(): Boolean = core.isFrozen()
    override fun freeze(): TimeZone { core.freeze(); return this }
    public override fun clone(): SimpleTimeZone = if (isFrozen()) this else cloneAsThawed()
    override fun cloneAsThawed(): SimpleTimeZone {
        val copy = cloneHost() as? SimpleTimeZone ?: SimpleTimeZone(core.getRawOffset(), core.getID())
        copy.core = core.cloneAsThawed(copy.owner())
        return copy
    }
    internal fun serializationCore(): SimpleTimeZoneCore = core
    private fun writeObject(output: PluralObjectOutputStream) = timeStateWriteFields(output, "SimpleTimeZone", core.serializationState())
    private fun readObject(input: PluralObjectInputStream) {
        val data = timeStateReadFields(input, "SimpleTimeZone")
        core = SimpleTimeZoneCore(0, getID() ?: "", owner = owner())
        core.restoreSerialized(data)
        (data["xinfo"] as STZInfo?)?.applyTo(this)
    }
    companion object {
        private const val serialVersionUID = -7034676239311322769L
        private val serialPersistentFields: Array<RbnfObjectStreamField> = timeStateSerialFields("SimpleTimeZone")
        const val WALL_TIME = 0
        const val STANDARD_TIME = 1
        const val UTC_TIME = 2
    }
}
