// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.TimeZone

/** Presents a common ICU timezone to JDK calendar APIs. */
open class TimeZoneAdapter(private val zone: TimeZone?) : java.util.TimeZone() {
    init { super.setID(zone!!.getID()) }
    open fun unwrap(): TimeZone? = zone
    override fun setID(id: String?) { super.setID(id); zone!!.setID(id) }
    override fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, milliseconds: Int): Int = zone!!.getOffset(era, year, month, day, dayOfWeek, milliseconds)
    override fun getRawOffset(): Int = zone!!.getRawOffset()
    override fun setRawOffset(offsetMillis: Int) = zone!!.setRawOffset(offsetMillis)
    override fun useDaylightTime(): Boolean = zone!!.useDaylightTime()
    override fun inDaylightTime(date: java.util.Date?): Boolean = zone!!.inDaylightTime(date)
    override fun hasSameRules(other: java.util.TimeZone?): Boolean = other is TimeZoneAdapter && zone!!.hasSameRules(other.zone)
    public override fun clone(): TimeZoneAdapter = TimeZoneAdapter(zone!!.clone())
    @Synchronized override fun hashCode(): Int = zone!!.hashCode()
    override fun equals(other: Any?): Boolean = this === other || other is TimeZoneAdapter && zone == other.zone
    override fun toString(): String = "TimeZoneAdapter: $zone"
    companion object { fun wrap(zone: TimeZone?): java.util.TimeZone = TimeZoneAdapter(zone) }
}
