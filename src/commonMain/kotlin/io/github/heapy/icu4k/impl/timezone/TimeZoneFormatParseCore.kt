// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2011-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.MatchInfo
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.util.ULocale

/** Full TimeZoneFormat parse selection algorithm; timezone objects and virtual services belong to its owner. */
internal class TimeZoneFormatParseCore<Z>(private val locale: ULocale, private val localized: LocalizedGMTOffsetCore, private val owner: OwnerQueries<Z>) {
    enum class Style(val flag: Int) {
        GENERIC_LOCATION(1), GENERIC_LONG(2), GENERIC_SHORT(4), SPECIFIC_LONG(8), SPECIFIC_SHORT(16),
        LOCALIZED_GMT(32), LOCALIZED_GMT_SHORT(64), ISO_BASIC_SHORT(128), ISO_BASIC_LOCAL_SHORT(256),
        ISO_BASIC_FIXED(128), ISO_BASIC_LOCAL_FIXED(256), ISO_BASIC_FULL(128), ISO_BASIC_LOCAL_FULL(256),
        ISO_EXTENDED_FIXED(128), ISO_EXTENDED_LOCAL_FIXED(256), ISO_EXTENDED_FULL(128), ISO_EXTENDED_LOCAL_FULL(256),
        ZONE_ID(512), ZONE_ID_SHORT(1024), EXEMPLAR_LOCATION(2048)
    }
    enum class TimeType { UNKNOWN, STANDARD, DAYLIGHT }
    enum class ParseOption { ALL_STYLES, TZ_DATABASE_ABBREVIATIONS }
    enum class GenericNameType {
        LOCATION, LONG, SHORT;
        fun isFallbackTypeOf(type: GenericNameType?): Boolean {
            val name = type!!.toString()
            return this == LOCATION && (name == "LONG" || name == "SHORT")
        }
    }
    open class GenericMatchInfo(private val zoneID: String?, private val length: Int, private val type: TimeType?) {
        open fun tzID(): String? = zoneID
        open fun matchLength(): Int = length
        open fun timeType(): TimeType? = type
    }
    interface OwnerQueries<Z> {
        fun findNames(text: String, start: Int, types: Set<NameType>): Collection<MatchInfo>?
        fun findTZDBNames(text: String, start: Int, types: Set<NameType>): Collection<MatchInfo>?
        fun findBestGenericMatch(text: String, start: Int, types: Set<GenericNameType>): GenericMatchInfo?
        fun getReferenceZoneID(metaZone: String?, region: String): String?
        fun getTimeZone(id: String?): Z
        fun getCustomTimeZone(offset: Int): Z
        fun getDefaultParseOptions(): Set<ParseOption>?
        /** Only the two public localized-offset methods are virtual in the source parser. */
        fun parseLocalizedOffset(core: LocalizedGMTOffsetCore, text: String, pos: ParsePosition, short: Boolean): Int = core.parseOffsetLocalizedGMT(text,pos,short)
    }
    private val regionLock = createAvailabilityLock()
    private var region: String? = null
    private fun getTargetRegion(): String = regionLock.withLock {
        region ?: locale.getCountry().ifEmpty { ULocale.addLikelySubtags(locale).getCountry().ifEmpty { "001" } }.also { region = it }
    }
    private fun zoneID(id: String?, meta: String?): String {
        if (id != null) return id
        checkAssertion(meta != null)
        return owner.getReferenceZoneID(meta,getTargetRegion()) ?: throw IllegalArgumentException("Invalid mzID: $meta")
    }
    private fun timeType(type: NameType?): TimeType = when(type!!) {
        NameType.LONG_STANDARD, NameType.SHORT_STANDARD -> TimeType.STANDARD
        NameType.LONG_DAYLIGHT, NameType.SHORT_DAYLIGHT -> TimeType.DAYLIGHT
        else -> TimeType.UNKNOWN
    }
    private fun offsetZone(offset: Int): Z = if (offset == 0) owner.getTimeZone("Etc/GMT") else owner.getCustomTimeZone(offset)
    fun parseExemplarLocation(text: String, pos: ParsePosition): String? {
        val start = pos.index
        var parsed = -1
        var id: String? = null
        val matches = owner.findNames(text,start,linkedSetOf(NameType.EXEMPLAR_LOCATION))
        var best: MatchInfo? = null
        if (matches != null) {
            for (match in matches) if (start+match.matchLength() > parsed) { best = match; parsed = start+match.matchLength() }
            if (best != null) { id = zoneID(best.tzID(),best.mzID()); pos.index = parsed }
        }
        if (id == null) pos.errorIndex = start
        return id
    }
    fun parse(style: Style?, text: String?, pos: ParsePosition?, options: Set<ParseOption>?, timeType: Output<TimeType>?): Z? {
        val output = timeType ?: Output(TimeType.UNKNOWN)
        output.value = TimeType.UNKNOWN
        val start = pos!!.index
        val input = text!!
        val maximum = input.length
        val fallbackLong = style == Style.SPECIFIC_LONG || style == Style.GENERIC_LONG || style == Style.GENERIC_LOCATION
        val fallbackShort = style == Style.SPECIFIC_SHORT || style == Style.GENERIC_SHORT
        var evaluated = 0
        val tmp = ParsePosition(start)
        var parsedOffset = UNKNOWN_OFFSET
        var parsedPos = -1
        if (fallbackLong || fallbackShort) {
            val digits = Output(false)
            val offset = localized.parseOffsetLocalizedGMT(input,tmp,fallbackShort,digits)
            if (tmp.errorIndex == -1) {
                if (tmp.index == maximum || digits.value!!) { pos.index = tmp.index; return offsetZone(offset) }
                parsedOffset = offset; parsedPos = tmp.index
            }
            evaluated = evaluated or (Style.LOCALIZED_GMT.flag or Style.LOCALIZED_GMT_SHORT.flag)
        }
        val tzdb = (options ?: owner.getDefaultParseOptions()!!).contains(ParseOption.TZ_DATABASE_ABBREVIATIONS)
        when(style!!) {
            Style.LOCALIZED_GMT, Style.LOCALIZED_GMT_SHORT -> {
                tmp.index = start; tmp.errorIndex = -1
                val short = style == Style.LOCALIZED_GMT_SHORT
                val offset = owner.parseLocalizedOffset(localized,input,tmp,short)
                if (tmp.errorIndex == -1) { pos.index = tmp.index; return offsetZone(offset) }
                evaluated = evaluated or (if (short) Style.LOCALIZED_GMT.flag else Style.LOCALIZED_GMT_SHORT.flag)
            }
            Style.ISO_BASIC_SHORT, Style.ISO_BASIC_FIXED, Style.ISO_BASIC_FULL, Style.ISO_EXTENDED_FIXED, Style.ISO_EXTENDED_FULL -> {
                tmp.index = start; tmp.errorIndex = -1
                val offset = TimeZoneOffsetCore.parseOffsetISO8601(input,tmp)
                if (tmp.errorIndex == -1) { pos.index = tmp.index; return offsetZone(offset) }
            }
            Style.ISO_BASIC_LOCAL_SHORT, Style.ISO_BASIC_LOCAL_FIXED, Style.ISO_BASIC_LOCAL_FULL, Style.ISO_EXTENDED_LOCAL_FIXED, Style.ISO_EXTENDED_LOCAL_FULL -> {
                tmp.index = start; tmp.errorIndex = -1
                val digits = Output(false)
                val offset = TimeZoneOffsetCore.parseOffsetISO8601(input,tmp,false,digits)
                if (tmp.errorIndex == -1 && digits.value!!) { pos.index = tmp.index; return offsetZone(offset) }
            }
            Style.SPECIFIC_LONG, Style.SPECIFIC_SHORT -> {
                val types = if (style == Style.SPECIFIC_LONG) linkedSetOf(NameType.LONG_STANDARD,NameType.LONG_DAYLIGHT) else linkedSetOf(NameType.SHORT_STANDARD,NameType.SHORT_DAYLIGHT)
                val matches = owner.findNames(input,start,types)
                var best: MatchInfo? = null
                if (matches != null) {
                    for (match in matches) if (start+match.matchLength() > parsedPos) { best = match; parsedPos = start+match.matchLength() }
                    if (best != null) { output.value = timeType(best.nameType()); pos.index = parsedPos; return owner.getTimeZone(zoneID(best.tzID(),best.mzID())) }
                }
                if (tzdb && style == Style.SPECIFIC_SHORT) {
                    checkAssertion(NameType.SHORT_STANDARD in types); checkAssertion(NameType.SHORT_DAYLIGHT in types)
                    val tzdbMatches = owner.findTZDBNames(input,start,types)
                    var tzdbBest: MatchInfo? = null
                    if (tzdbMatches != null) {
                        for (match in tzdbMatches) if (start+match.matchLength() > parsedPos) { tzdbBest = match; parsedPos = start+match.matchLength() }
                        if (tzdbBest != null) { output.value = timeType(tzdbBest.nameType()); pos.index = parsedPos; return owner.getTimeZone(zoneID(tzdbBest.tzID(),tzdbBest.mzID())) }
                    }
                }
            }
            Style.GENERIC_LONG, Style.GENERIC_SHORT, Style.GENERIC_LOCATION -> {
                val types = when(style) {
                    Style.GENERIC_LOCATION -> linkedSetOf(GenericNameType.LOCATION)
                    Style.GENERIC_LONG -> linkedSetOf(GenericNameType.LOCATION,GenericNameType.LONG)
                    else -> linkedSetOf(GenericNameType.LOCATION,GenericNameType.SHORT)
                }
                val best = owner.findBestGenericMatch(input,start,types)
                if (best != null && start+best.matchLength() > parsedPos) {
                    output.value = best.timeType(); pos.index = start+best.matchLength(); return owner.getTimeZone(best.tzID())
                }
            }
            Style.ZONE_ID, Style.ZONE_ID_SHORT, Style.EXEMPLAR_LOCATION -> {
                tmp.index = start; tmp.errorIndex = -1
                val id = when(style) {
                    Style.ZONE_ID -> TimeZoneIDParseCore.parseZoneID(input,tmp)
                    Style.ZONE_ID_SHORT -> TimeZoneIDParseCore.parseShortZoneID(input,tmp)
                    else -> parseExemplarLocation(input,tmp)
                }
                if (tmp.errorIndex == -1) { pos.index = tmp.index; return owner.getTimeZone(id) }
            }
        }
        evaluated = evaluated or style.flag
        if (parsedPos > start) { checkAssertion(parsedOffset != UNKNOWN_OFFSET); pos.index = parsedPos; return offsetZone(parsedOffset) }
        var parsedID: String? = null
        var parsedType: TimeType? = TimeType.UNKNOWN
        checkAssertion(parsedPos < 0); checkAssertion(parsedOffset == UNKNOWN_OFFSET)
        if (parsedPos < maximum && (evaluated and 128 == 0 || evaluated and 256 == 0)) {
            tmp.index = start; tmp.errorIndex = -1
            val digits = Output(false)
            val offset = TimeZoneOffsetCore.parseOffsetISO8601(input,tmp,false,digits)
            if (tmp.errorIndex == -1) {
                if (tmp.index == maximum || digits.value!!) { pos.index = tmp.index; return offsetZone(offset) }
                if (parsedPos < tmp.index) { parsedOffset = offset; parsedID = null; parsedType = TimeType.UNKNOWN; parsedPos = tmp.index; checkAssertion(parsedPos == start+1) }
            }
        }
        for (short in listOf(false,true)) {
            val flag = if (short) Style.LOCALIZED_GMT_SHORT.flag else Style.LOCALIZED_GMT.flag
            if (parsedPos < maximum && evaluated and flag == 0) {
                tmp.index = start; tmp.errorIndex = -1
                val digits = Output(false)
                val offset = localized.parseOffsetLocalizedGMT(input,tmp,short,digits)
                if (tmp.errorIndex == -1) {
                    if (tmp.index == maximum || digits.value!!) { pos.index = tmp.index; return offsetZone(offset) }
                    if (parsedPos < tmp.index) { parsedOffset = offset; parsedID = null; parsedType = TimeType.UNKNOWN; parsedPos = tmp.index }
                }
            }
        }
        val all = (options ?: owner.getDefaultParseOptions()!!).contains(ParseOption.ALL_STYLES)
        if (all) {
            if (parsedPos < maximum) {
                val matches = owner.findNames(input,start,ALL_SIMPLE_TYPES)
                var best: MatchInfo? = null
                var matchPos = -1
                if (matches != null) for (match in matches) if (start+match.matchLength() > matchPos) { best = match; matchPos = start+match.matchLength() }
                if (parsedPos < matchPos) { parsedPos = matchPos; parsedID = zoneID(best!!.tzID(),best.mzID()); parsedType = timeType(best.nameType()); parsedOffset = UNKNOWN_OFFSET }
            }
            if (tzdb && parsedPos < maximum && evaluated and Style.SPECIFIC_SHORT.flag == 0) {
                val matches = owner.findTZDBNames(input,start,ALL_SIMPLE_TYPES)
                var best: MatchInfo? = null
                var matchPos = -1
                if (matches != null) {
                    for (match in matches) if (start+match.matchLength() > matchPos) { best = match; matchPos = start+match.matchLength() }
                    if (parsedPos < matchPos) { parsedPos = matchPos; parsedID = zoneID(best!!.tzID(),best.mzID()); parsedType = timeType(best.nameType()); parsedOffset = UNKNOWN_OFFSET }
                }
            }
            if (parsedPos < maximum) {
                val best = owner.findBestGenericMatch(input,start,ALL_GENERIC_TYPES)
                if (best != null && parsedPos < start+best.matchLength()) { parsedPos = start+best.matchLength(); parsedID = best.tzID(); parsedType = best.timeType(); parsedOffset = UNKNOWN_OFFSET }
            }
            for (short in listOf(false,true)) {
                val flag = if (short) Style.ZONE_ID_SHORT.flag else Style.ZONE_ID.flag
                if (parsedPos < maximum && evaluated and flag == 0) {
                    tmp.index = start; tmp.errorIndex = -1
                    val id = if (short) TimeZoneIDParseCore.parseShortZoneID(input,tmp) else TimeZoneIDParseCore.parseZoneID(input,tmp)
                    if (tmp.errorIndex == -1 && parsedPos < tmp.index) { parsedPos = tmp.index; parsedID = id; parsedType = TimeType.UNKNOWN; parsedOffset = UNKNOWN_OFFSET }
                }
            }
        }
        if (parsedPos > start) {
            val zone = if (parsedID != null) owner.getTimeZone(parsedID) else { checkAssertion(parsedOffset != UNKNOWN_OFFSET); offsetZone(parsedOffset) }
            output.value = parsedType; pos.index = parsedPos
            return zone
        }
        pos.errorIndex = start
        return null
    }
    companion object {
        private const val UNKNOWN_OFFSET = Int.MAX_VALUE
        private val ALL_SIMPLE_TYPES = linkedSetOf(NameType.LONG_STANDARD,NameType.LONG_DAYLIGHT,NameType.SHORT_STANDARD,NameType.SHORT_DAYLIGHT,NameType.EXEMPLAR_LOCATION)
        private val ALL_GENERIC_TYPES = linkedSetOf(GenericNameType.LOCATION,GenericNameType.LONG,GenericNameType.SHORT)
        private fun checkAssertion(value: Boolean) { if (timeZoneOffsetAssertionsEnabled() && !value) throw AssertionError() }
    }
}
