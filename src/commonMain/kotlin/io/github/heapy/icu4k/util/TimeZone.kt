// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.timezone.TimeZoneBaseCore
import io.github.heapy.icu4k.impl.timezone.TimeZoneMetadataCore
import io.github.heapy.icu4k.impl.timezone.ZoneMetaCore

/** Base contract for mutable and frozen ICU time zones. */
abstract class TimeZone : ICUCloneableBase, Freezable<TimeZone> {
    private var id: String? = null
    constructor() : super()
    protected constructor(id: String?) : super() { this.id = id!! }
    abstract fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int): Int
    abstract fun setRawOffset(offsetMillis: Int)
    abstract fun getRawOffset(): Int
    abstract fun useDaylightTime(): Boolean
    abstract fun inDaylightTime(date: Date?): Boolean
    open fun getID(): String? = id
    open fun setID(value: String?) {
        if (value == null) throw NullPointerException()
        if (isFrozen()) throw UnsupportedOperationException("Attempt to modify a frozen TimeZone instance.")
        id = value
    }
    private fun base(): TimeZoneBaseCore<TimeZone> = TimeZoneBaseCore(this, object : TimeZoneBaseCore.Owner<TimeZone> {
        override fun getRawOffset(zone: TimeZone) = zone.getRawOffset()
        override fun getOffset(zone: TimeZone, era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millis: Int) = zone.getOffset(era, year, month, day, dayOfWeek, millis)
        override fun getOffset(zone: TimeZone, date: Long, local: Boolean, offsets: IntArray) = zone.getOffset(date, local, offsets)
        override fun useDaylightTime(zone: TimeZone) = zone.useDaylightTime()
        override fun inDaylightTime(zone: TimeZone, date: Date) = zone.inDaylightTime(date)
        override fun isFrozen(zone: TimeZone) = zone.isFrozen()
        override fun cloneAsThawed(zone: TimeZone) = zone.cloneAsThawed()
        override fun hasSameClass(other: Any) = this@TimeZone::class == other::class
        override fun storedID(other: Any) = (other as TimeZone).id
    })
    open fun getOffset(date: Long): Int = base().getOffset(date)
    open fun getOffset(date: Long, local: Boolean, offsets: IntArray?) = base().getOffset(date, local, offsets)
    open fun getDSTSavings(): Int = base().getDSTSavings()
    open fun observesDaylightTime(): Boolean = base().observesDaylightTime()
    open fun hasSameRules(other: TimeZone?): Boolean = base().hasSameRules(other)
    public override fun clone(): TimeZone = if (isFrozen()) this else cloneAsThawed()
    override fun isFrozen(): Boolean = false
    override fun freeze(): TimeZone = throw UnsupportedOperationException("Needs to be implemented by the subclass.")
    override fun cloneAsThawed(): TimeZone = cloneHost() as? TimeZone ?: throw ICUCloneNotSupportedException()
    override fun equals(other: Any?): Boolean = this === other || other != null && this::class == other::class && id!! == (other as TimeZone).id
    override fun hashCode(): Int = id!!.hashCode()

    enum class SystemTimeZoneType { ANY, CANONICAL, CANONICAL_LOCATION }

    companion object {
        const val UNKNOWN_ZONE_ID = TimeZoneMetadataCore.UNKNOWN_ZONE_ID
        const val GMT_ZONE_ID = TimeZoneMetadataCore.GMT_ZONE_ID
        fun getAvailableIDs(): Array<String> = TimeZoneMetadataCore.getAvailableIDs()
        fun getAvailableIDs(rawOffset: Int): Array<String> = TimeZoneMetadataCore.getAvailableIDs(rawOffset)
        fun getAvailableIDs(country: String?): Array<String> = TimeZoneMetadataCore.getAvailableIDs(country)
        fun getAvailableIDs(zoneType: SystemTimeZoneType?, region: String?, rawOffset: Int?): Set<String> =
            TimeZoneMetadataCore.getAvailableIDs(zoneType?.let { ZoneMetaCore.SystemTimeZoneType.valueOf(it.name) }, region, rawOffset)
        fun getCanonicalID(id: String?): String? = TimeZoneMetadataCore.getCanonicalID(id)
        fun getCanonicalID(id: String?, isSystemID: BooleanArray?): String? = TimeZoneMetadataCore.getCanonicalID(id, isSystemID)
        fun getIanaID(id: String?): String = TimeZoneMetadataCore.getIanaID(id)
        fun getRegion(id: String?): String = TimeZoneMetadataCore.getRegion(id)
        fun getTZDataVersion(): String = TimeZoneMetadataCore.getTZDataVersion()
        fun countEquivalentIDs(id: String?): Int = TimeZoneMetadataCore.countEquivalentIDs(id)
        fun getEquivalentID(id: String?, index: Int): String = TimeZoneMetadataCore.getEquivalentID(id, index)
        fun getWindowsID(id: String?): String? = TimeZoneMetadataCore.getWindowsID(id)
        fun getIDForWindowsID(id: String?, region: String?): String? = TimeZoneMetadataCore.getIDForWindowsID(id, region)
    }
}
