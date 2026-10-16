// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.Style
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.TimeType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.util.ULocale

/** TimeZone's eight display-name styles and requested-daylight fallback policy. */
internal class TimeZoneDisplayNameCore<Z>(private val owner: Owner<Z>, private val assertionsEnabled: Boolean) {
    interface Names {
        fun getDisplayName(id: String?, type: NameType, date: Long): String?
    }
    interface Format<Z> {
        fun format(style: Style, zone: Z, date: Long, timeType: Output<TimeType>): String?
        fun formatOffsetLocalizedGMT(offset: Int): String?
        fun formatOffsetShortLocalizedGMT(offset: Int): String?
        fun formatOffsetISO8601Basic(offset: Int, useUtcIndicator: Boolean, isShort: Boolean, ignoreSeconds: Boolean): String?
    }
    interface Owner<Z> {
        fun getFormat(locale: ULocale): Format<Z>?
        fun getNames(locale: ULocale): Names?
        fun currentTimeMillis(): Long
        fun getRawOffset(zone: Z): Int
        fun getDSTSavings(zone: Z): Int
        fun useDaylightTime(zone: Z): Boolean
        fun getCanonicalCLDRID(zone: Z): String?
    }

    fun getDisplayName(zone: Z, daylight: Boolean, style: Int, locale: ULocale?): String? {
        require(style in SHORT..GENERIC_LOCATION) { "Illegal style: $style" }
        if (locale == null) throw NullPointerException("locale is null")
        var result: String?
        when (style) {
            GENERIC_LOCATION, LONG_GENERIC, SHORT_GENERIC -> {
                val format = owner.getFormat(locale)
                val date = owner.currentTimeMillis()
                val timeType = Output(TimeType.UNKNOWN)
                val formatStyle = when (style) {
                    GENERIC_LOCATION -> Style.GENERIC_LOCATION
                    LONG_GENERIC -> Style.GENERIC_LONG
                    else -> Style.GENERIC_SHORT
                }
                result = format!!.format(formatStyle, zone, date, timeType)
                if ((daylight && timeType.value == TimeType.STANDARD) ||
                    (!daylight && timeType.value == TimeType.DAYLIGHT)) {
                    // Generic fallback deliberately does not consult useDaylightTime().
                    val offset = if (daylight) owner.getRawOffset(zone) + owner.getDSTSavings(zone) else owner.getRawOffset(zone)
                    result = if (style == SHORT_GENERIC) format.formatOffsetShortLocalizedGMT(offset)
                    else format.formatOffsetLocalizedGMT(offset)
                }
            }
            LONG_GMT, SHORT_GMT -> {
                val format = owner.getFormat(locale)
                val offset = requestedOffset(zone, daylight)
                result = if (style == LONG_GMT) format!!.formatOffsetLocalizedGMT(offset)
                else format!!.formatOffsetISO8601Basic(offset, false, false, false)
            }
            else -> {
                val date = owner.currentTimeMillis()
                val names = owner.getNames(locale)
                val nameType = if (style == LONG) {
                    if (daylight) NameType.LONG_DAYLIGHT else NameType.LONG_STANDARD
                } else if (daylight) NameType.SHORT_DAYLIGHT else NameType.SHORT_STANDARD
                val id = owner.getCanonicalCLDRID(zone)
                result = names!!.getDisplayName(id, nameType, date)
                if (result == null) {
                    val format = owner.getFormat(locale)
                    val offset = requestedOffset(zone, daylight)
                    result = if (style == LONG) format!!.formatOffsetLocalizedGMT(offset)
                    else format!!.formatOffsetShortLocalizedGMT(offset)
                }
            }
        }
        if (assertionsEnabled && result == null) throw AssertionError()
        return result
    }

    private fun requestedOffset(zone: Z, daylight: Boolean): Int =
        if (daylight && owner.useDaylightTime(zone)) owner.getRawOffset(zone) + owner.getDSTSavings(zone)
        else owner.getRawOffset(zone)

    companion object {
        const val SHORT = 0
        const val LONG = 1
        const val SHORT_GENERIC = 2
        const val LONG_GENERIC = 3
        const val SHORT_GMT = 4
        const val LONG_GMT = 5
        const val SHORT_COMMONLY_USED = 6
        const val GENERIC_LOCATION = 7
    }
}
