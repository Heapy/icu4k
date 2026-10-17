// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2007-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.timezone.RuleBasedTimeZoneCore
import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream
import io.github.heapy.icu4k.text.RbnfObjectStreamField

/** A time zone assembled from an initial rule and historical/recurring transitions. */
open class RuleBasedTimeZone(id: String?, initialRule: InitialTimeZoneRule?) : BasicTimeZone(id) {
    private var core = RuleBasedTimeZoneCore(initialRule)
    open fun addTransitionRule(rule: TimeZoneRule?) = core.addTransitionRule(rule, isFrozen())
    override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int): Int = core.getOffset(era, year, month, day, dayOfWeek, milliseconds)
    override fun getOffset(date: Long, local: Boolean, offsets: IntArray?) = core.getOffset(date, local, offsets)
    override fun getOffsetFromLocal(date: Long, nonExistingTimeOpt: LocalOption?, duplicatedTimeOpt: LocalOption?, offsets: IntArray?) = core.getOffsetFromLocal(date, getLocalOptionValue(nonExistingTimeOpt), getLocalOptionValue(duplicatedTimeOpt), offsets)
    override fun getRawOffset(): Int = core.getRawOffset { time, local, offsets -> getOffset(time, local, offsets) }
    override fun inDaylightTime(date: Date?): Boolean = core.inDaylightTime(date) { time, local, offsets -> getOffset(time, local, offsets) }
    override fun setRawOffset(offsetMillis: Int): Unit = core.setRawOffset(offsetMillis)
    override fun useDaylightTime(): Boolean = core.useDaylightTime({ time, local, offsets -> getOffset(time, local, offsets) }, ::getNextTransition)
    override fun observesDaylightTime(): Boolean = core.observesDaylightTime({ time, local, offsets -> getOffset(time, local, offsets) }, ::getNextTransition)
    override fun hasSameRules(other: TimeZone?): Boolean = this === other || other is RuleBasedTimeZone && core.hasSameRules(other.core)
    override fun getTimeZoneRules(): Array<TimeZoneRule?> = core.getTimeZoneRules()
    override fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = core.getNextTransition(base, inclusive, ::getNextTransition)
    override fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = core.getPreviousTransition(base, inclusive, ::getPreviousTransition)
    override fun isFrozen(): Boolean = core.isFrozen()
    override fun freeze(): TimeZone { core.freeze(); return this }
    public override fun clone(): RuleBasedTimeZone = if (isFrozen()) this else cloneAsThawed()
    override fun cloneAsThawed(): RuleBasedTimeZone {
        val copy = cloneHost() as? RuleBasedTimeZone ?: RuleBasedTimeZone(getID(), null)
        copy.core = core.cloneAsThawed()
        return copy
    }
    private fun writeObject(output: PluralObjectOutputStream) = timeStateWriteFields(output, "RuleBasedTimeZone", core.serializationState())
    private fun readObject(input: PluralObjectInputStream) { core = RuleBasedTimeZoneCore.fromSerialized(timeStateReadFields(input, "RuleBasedTimeZone")) }
    private companion object {
        private const val serialVersionUID = 7580833058949327935L
        private val serialPersistentFields: Array<RbnfObjectStreamField> = timeStateSerialFields("RuleBasedTimeZone")
    }
}
