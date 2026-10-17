// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.timezone.OlsonTimeZoneCore
import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream
import io.github.heapy.icu4k.text.RbnfObjectStreamField

import io.github.heapy.icu4k.util.*

/** ICU resource-backed historical time zone with recurring final rules. */
open class OlsonTimeZone : BasicTimeZone {
    private var core: OlsonTimeZoneCore
    constructor(id: String?) : super(id) { core = OlsonTimeZoneCore(id, owner = owner()) }
    constructor(top: UResourceBundle?, resource: UResourceBundle?, id: String?) : super(id) {
        core = OlsonTimeZoneCore(top, resource, id, owner = owner())
    }
    internal constructor(prototype: OlsonTimeZoneCore) : super(prototype.getID()) {
        core = prototype.cloneAsThawed(owner())
    }
    /** Native copy path without resource loading, clock queries or virtual getters. */
    protected constructor(other: OlsonTimeZone, copyMarker: Unit) : super(other, copyMarker) {
        core = other.core.cloneAsThawed(owner())
    }
    override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int): Int =
        core.getOffset(era, year, month, day, dayOfWeek, milliseconds)
    open fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int, monthLength: Int): Int =
        core.getOffset(era, year, month, day, dayOfWeek, milliseconds, monthLength)
    override fun getOffset(date: Long, local: Boolean, offsets: IntArray?) = core.getOffset(date, local, offsets)
    override fun getOffsetFromLocal(date: Long, nonExistingTimeOpt: LocalOption?, duplicatedTimeOpt: LocalOption?, offsets: IntArray?) =
        core.getOffsetFromLocal(date, getLocalOptionValue(nonExistingTimeOpt), getLocalOptionValue(duplicatedTimeOpt), offsets)
    override fun setRawOffset(offsetMillis: Int) = core.setRawOffset(offsetMillis)
    override fun getRawOffset(): Int = core.getRawOffset()
    override fun useDaylightTime(): Boolean = core.useDaylightTime()
    override fun observesDaylightTime(): Boolean = core.observesDaylightTime()
    override fun getDSTSavings(): Int = core.getDSTSavings()
    override fun inDaylightTime(date: Date?): Boolean = core.inDaylightTime(date)
    override fun setID(value: String?) = core.setID(value, baseSetID = { super.setID(it) })
    open fun getCanonicalID(): String? = core.getCanonicalID()
    override fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = core.getNextTransition(base, inclusive)
    override fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = core.getPreviousTransition(base, inclusive)
    override fun getTimeZoneRules(): Array<TimeZoneRule?> = core.getTimeZoneRules()
    override fun hasSameRules(other: TimeZone?): Boolean {
        if (this === other) return true
        if (!super.hasSameRules(other)) return false
        return other is OlsonTimeZone && core.hasSameRules(other.core, compareBaseRules = false)
    }
    override fun equals(other: Any?): Boolean = super.equals(other) && core == (other as OlsonTimeZone).core
    override fun hashCode(): Int = core.hashCode()
    override fun toString(): String = core.describe(super.toString())
    override fun isFrozen(): Boolean = core.isFrozen()
    override fun freeze(): TimeZone { core.freeze(); return this }
    public override fun clone(): OlsonTimeZone = if (isFrozen()) this else cloneAsThawed()
    override fun cloneAsThawed(): OlsonTimeZone {
        val copy = cloneHost() as? OlsonTimeZone ?: cloneNative()
        copy.core = core.cloneAsThawed(copy.owner())
        return copy
    }
    /** Native subclasses override this hook to copy their additional fields. */
    protected open fun cloneNative(): OlsonTimeZone = OlsonTimeZone(this, Unit)
    private fun owner() = object : OlsonTimeZoneCore.OwnerQueries {
        private val p get() = this@OlsonTimeZone
        override fun getID(): String? = p.getID()
        override fun isFrozen(): Boolean = p.isFrozen()
        override fun getRawOffset(): Int = p.getRawOffset()
        override fun useDaylightTime(): Boolean = p.useDaylightTime()
        override fun getOffset(date: Long, local: Boolean, offsets: IntArray?) = p.getOffset(date, local, offsets)
        override fun getOffset(era: Int, year: Int, month: Int, day: Int, dow: Int, millis: Int, monthLength: Int): Int =
            p.getOffset(era, year, month, day, dow, millis, monthLength)
        override fun getNextTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = p.getNextTransition(base, inclusive)
        override fun getPreviousTransition(base: Long, inclusive: Boolean): TimeZoneTransition? = p.getPreviousTransition(base, inclusive)
        override fun getSimpleTimeZoneRulesNear(date: Long): Array<TimeZoneRule?> = p.getSimpleTimeZoneRulesNear(date)
    }
    private fun writeObject(output: PluralObjectOutputStream) = timeStateWriteFields(output, "OlsonTimeZone", core.serializationState())
    private fun readObject(input: PluralObjectInputStream) { core = OlsonTimeZoneCore.fromSerialized(getID(), timeStateReadFields(input, "OlsonTimeZone"), owner()) }
    private companion object {
        private const val serialVersionUID = -6281977362477515376L
        private val serialPersistentFields: Array<RbnfObjectStreamField> = timeStateSerialFields("OlsonTimeZone")
    }
}
