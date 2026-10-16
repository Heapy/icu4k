// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2011-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.Style
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.TimeType
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.GenericNameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.util.Output

/** TimeZoneFormat's complete style selection and named/numeric display fallback. */
internal class TimeZoneFormatDisplayCore<Z>(private val localized: LocalizedGMTOffsetCore, private val owner: OwnerQueries<Z>) {
    interface Names {
        fun getDisplayName(id: String?, type: NameType, date: Long): String?
        fun getExemplarLocationName(id: String?): String?
    }
    interface GenericNames<Z> {
        fun getGenericLocationName(id: String?): String?
        fun getDisplayName(zone: Z?, type: GenericNameType, date: Long): String?
    }
    interface OwnerQueries<Z> {
        fun getID(zone: Z?): String?
        fun getShortID(zone: Z?): String?
        fun getCanonicalCLDRID(zone: Z?): String?
        fun inDaylightTime(zone: Z?, date: Long): Boolean
        fun getOffset(zone: Z?, date: Long, local: Boolean, offsets: IntArray)
        fun getNames(): Names?
        fun getGenericNames(): GenericNames<Z>
        /** The localized numeric formatting methods are virtual in the public owner. */
        fun formatLocalizedOffset(core: LocalizedGMTOffsetCore, offset: Int, short: Boolean): String? =
            if (short) core.formatOffsetShortLocalizedGMT(offset) else core.formatOffsetLocalizedGMT(offset)
    }
    fun format(style: Style?, zone: Z?, date: Long, timeType: Output<TimeType>? = null): String? {
        var result: String? = null
        if (timeType != null) timeType.value = TimeType.UNKNOWN
        var noOffsetFallback = false
        when(style!!) {
            Style.GENERIC_LOCATION -> result = owner.getGenericNames().getGenericLocationName(owner.getCanonicalCLDRID(zone))
            Style.GENERIC_LONG -> result = owner.getGenericNames().getDisplayName(zone,GenericNameType.LONG,date)
            Style.GENERIC_SHORT -> result = owner.getGenericNames().getDisplayName(zone,GenericNameType.SHORT,date)
            Style.SPECIFIC_LONG -> result = specific(zone,NameType.LONG_STANDARD,NameType.LONG_DAYLIGHT,date,timeType)
            Style.SPECIFIC_SHORT -> result = specific(zone,NameType.SHORT_STANDARD,NameType.SHORT_DAYLIGHT,date,timeType)
            Style.ZONE_ID -> { result = owner.getID(zone); noOffsetFallback = true }
            Style.ZONE_ID_SHORT -> { result = owner.getShortID(zone) ?: "unk"; noOffsetFallback = true }
            Style.EXEMPLAR_LOCATION -> { result = exemplar(zone); noOffsetFallback = true }
            else -> Unit
        }
        if (result == null && !noOffsetFallback) {
            val offsets = intArrayOf(0,0)
            owner.getOffset(zone,date,false,offsets)
            val offset = offsets[0]+offsets[1]
            result = when(style) {
                Style.GENERIC_LOCATION, Style.GENERIC_LONG, Style.SPECIFIC_LONG, Style.LOCALIZED_GMT -> owner.formatLocalizedOffset(localized,offset,false)
                Style.GENERIC_SHORT, Style.SPECIFIC_SHORT, Style.LOCALIZED_GMT_SHORT -> owner.formatLocalizedOffset(localized,offset,true)
                Style.ISO_BASIC_SHORT -> TimeZoneOffsetCore.formatOffsetISO8601Basic(offset,true,true,true)
                Style.ISO_BASIC_LOCAL_SHORT -> TimeZoneOffsetCore.formatOffsetISO8601Basic(offset,false,true,true)
                Style.ISO_BASIC_FIXED -> TimeZoneOffsetCore.formatOffsetISO8601Basic(offset,true,false,true)
                Style.ISO_BASIC_LOCAL_FIXED -> TimeZoneOffsetCore.formatOffsetISO8601Basic(offset,false,false,true)
                Style.ISO_BASIC_FULL -> TimeZoneOffsetCore.formatOffsetISO8601Basic(offset,true,false,false)
                Style.ISO_BASIC_LOCAL_FULL -> TimeZoneOffsetCore.formatOffsetISO8601Basic(offset,false,false,false)
                Style.ISO_EXTENDED_FIXED -> TimeZoneOffsetCore.formatOffsetISO8601Extended(offset,true,false,true)
                Style.ISO_EXTENDED_LOCAL_FIXED -> TimeZoneOffsetCore.formatOffsetISO8601Extended(offset,false,false,true)
                Style.ISO_EXTENDED_FULL -> TimeZoneOffsetCore.formatOffsetISO8601Extended(offset,true,false,false)
                Style.ISO_EXTENDED_LOCAL_FULL -> TimeZoneOffsetCore.formatOffsetISO8601Extended(offset,false,false,false)
                else -> { if (timeZoneOffsetAssertionsEnabled()) throw AssertionError(); null }
            }
            // A virtual formatter may mutate the captured offsets before this classification.
            if (timeType != null) timeType.value = if (offsets[1] != 0) TimeType.DAYLIGHT else TimeType.STANDARD
        }
        if (timeZoneOffsetAssertionsEnabled() && result == null) throw AssertionError()
        return result
    }
    private fun specific(zone: Z?, standard: NameType, daylight: NameType, date: Long, timeType: Output<TimeType>?): String? {
        val dst = owner.inDaylightTime(zone,date)
        val names = owner.getNames()
        val id = owner.getCanonicalCLDRID(zone)
        val name = names!!.getDisplayName(id,if (dst) daylight else standard,date)
        if (name != null && timeType != null) timeType.value = if (dst) TimeType.DAYLIGHT else TimeType.STANDARD
        return name
    }
    private fun exemplar(zone: Z?): String {
        val names = owner.getNames()
        val id = owner.getCanonicalCLDRID(zone)
        var location = names!!.getExemplarLocationName(id)
        if (location == null) {
            location = owner.getNames()!!.getExemplarLocationName("Etc/Unknown")
            if (location == null) location = "Unknown"
        }
        return location
    }
}
