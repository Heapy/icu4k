// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.OlsonTimeZone
import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.impl.timezone.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.GenericNameType
import io.github.heapy.icu4k.util.*

private fun canonical(zone: TimeZone?): String? = if (zone is OlsonTimeZone) zone.getCanonicalID() else ZoneMetaCore.getCanonicalCLDRID(zone!!.getID())
private fun NameType.publicType(): TimeZoneNames.NameType = TimeZoneNames.NameType.valueOf(name)
private fun Set<NameType>.publicTypes(): Set<TimeZoneNames.NameType> = mapTo(linkedSetOf()) { it.publicType() }
private fun TimeZoneNames.MatchInfo.coreMatch(): TimeZoneNamesDataCore.MatchInfo {
    val source = this
    return object : TimeZoneNamesDataCore.MatchInfo(NameType.LONG_GENERIC, "", null, 1) {
        override fun nameType() = NameType.valueOf(source.nameType().name)
        override fun tzID() = source.tzID()
        override fun mzID() = source.mzID()
        override fun matchLength() = source.matchLength()
    }
}
private fun TimeZoneNames.findCore(text: String, start: Int, types: Set<NameType>): Collection<TimeZoneNamesDataCore.MatchInfo>? {
    val source = find(text, start, types.publicTypes()) ?: return null
    return object : AbstractCollection<TimeZoneNamesDataCore.MatchInfo>() {
        override val size: Int get() = source.size
        override fun iterator(): Iterator<TimeZoneNamesDataCore.MatchInfo> {
            val values = source.iterator()
            return object : Iterator<TimeZoneNamesDataCore.MatchInfo> {
                override fun hasNext() = values.hasNext()
                override fun next() = values.next().coreMatch()
            }
        }
    }
}
private fun genericNamesAdapter(value: TimeZoneNames?): TimeZoneGenericNamesCore.Names? = value?.let { names ->
    object : TimeZoneGenericNamesCore.Names {
        override fun getAvailableMetaZoneIDs(id: String) = names.getAvailableMetaZoneIDs(id)
        override fun getMetaZoneID(id: String, date: Long) = names.getMetaZoneID(id, date)
        override fun getReferenceZoneID(id: String?, region: String) = names.getReferenceZoneID(id, region)
        override fun getMetaZoneDisplayName(id: String, type: NameType) = names.getMetaZoneDisplayName(id, type.publicType())
        override fun getTimeZoneDisplayName(id: String, type: NameType) = names.getTimeZoneDisplayName(id, type.publicType())
        override fun getDisplayName(id: String, type: NameType, date: Long) = names.getDisplayName(id, type.publicType(), date)
        override fun getExemplarLocationName(id: String) = names.getExemplarLocationName(id)
        override fun find(text: String, start: Int, types: Set<NameType>) = names.findCore(text, start, types)
    }
}
internal object TimeZoneFormatOwners {
    private val genericOwner = object : TimeZoneGenericNamesCore.OwnerQueries<TimeZone> {
        override fun getDefaultNames(locale: ULocale?) = genericNamesAdapter(TimeZoneNames.getInstance(locale))
        override fun getDefaultTimeZone() = TimeZone.getDefault()
        override fun getCanonicalCLDRID(zone: TimeZone?) = canonical(zone)
        override fun getOffset(zone: TimeZone?, date: Long, local: Boolean, offsets: IntArray) = zone!!.getOffset(date, local, offsets)
        override fun getBasicTimeZone(zone: TimeZone?): BasicTimeZoneCore? {
            if (zone !is BasicTimeZone) return null
            return object : BasicTimeZoneCore() {
                override fun getID() = zone.getID()
                override fun getOffset(date: Long, local: Boolean, offsets: IntArray) = zone.getOffset(date, local, offsets)
                override fun getNextTransition(base: Long, inclusive: Boolean) = zone.getNextTransition(base, inclusive)
                override fun getPreviousTransition(base: Long, inclusive: Boolean) = zone.getPreviousTransition(base, inclusive)
                override fun getTimeZoneRules() = zone.getTimeZoneRules()
            }
        }
        override fun getFrozenTimeZone(id: String) = TimeZone.getFrozenTimeZone(id)
        override fun createPatternFormatter(pattern: String?) = timeZoneGenericPattern(pattern)
    }
    private val genericCache = object : SoftCache<String, TimeZoneGenericNamesCore<TimeZone>, ULocale>() {
        override fun createInstance(key: String, data: ULocale) = newGeneric(data, null).freeze()
    }
    fun cachedGeneric(locale: ULocale): TimeZoneGenericNamesCore<TimeZone> = genericCache.getInstance(locale.getBaseName(), locale)!!
    fun newGeneric(locale: ULocale?, names: TimeZoneNames?): TimeZoneGenericNamesCore<TimeZone> =
        TimeZoneGenericNamesCore(locale, genericNamesAdapter(names), genericOwner)
}
internal fun TimeZoneFormat.displayOwner(): TimeZoneFormatDisplayCore.OwnerQueries<TimeZone> = object : TimeZoneFormatDisplayCore.OwnerQueries<TimeZone> {
    override fun getID(zone: TimeZone?) = zone!!.getID()
    override fun getShortID(zone: TimeZone?) = canonical(zone)?.let { ZoneMetaCore.getShortID(it) }
    override fun getCanonicalCLDRID(zone: TimeZone?) = canonical(zone)
    override fun inDaylightTime(zone: TimeZone?, date: Long) = zone!!.inDaylightTime(Date(date))
    override fun getOffset(zone: TimeZone?, date: Long, local: Boolean, offsets: IntArray) = zone!!.getOffset(date, local, offsets)
    override fun getNames(): TimeZoneFormatDisplayCore.Names? = getTimeZoneNames()?.let { source -> object : TimeZoneFormatDisplayCore.Names {
        override fun getDisplayName(id: String?, type: NameType, date: Long) = source.getDisplayName(id, type.publicType(), date)
        override fun getExemplarLocationName(id: String?) = source.getExemplarLocationName(id)
    } }
    override fun getGenericNames(): TimeZoneFormatDisplayCore.GenericNames<TimeZone> {
        val core = genericNames()
        return object : TimeZoneFormatDisplayCore.GenericNames<TimeZone> {
            override fun getGenericLocationName(id: String?) = core.getGenericLocationName(id)
            override fun getDisplayName(zone: TimeZone?, type: GenericNameType, date: Long) = core.getDisplayName(zone, type, date)
        }
    }
    override fun formatLocalizedOffset(core: LocalizedGMTOffsetCore, offset: Int, short: Boolean): String? =
        if (short) formatOffsetShortLocalizedGMT(offset) else formatOffsetLocalizedGMT(offset)
}
internal fun TimeZoneFormat.parseOwner(): TimeZoneFormatParseCore.OwnerQueries<TimeZone> = object : TimeZoneFormatParseCore.OwnerQueries<TimeZone> {
    override fun findNames(text: String, start: Int, types: Set<NameType>) = names!!.findCore(text, start, types)
    override fun findTZDBNames(text: String, start: Int, types: Set<NameType>) = tzdbNames().findCore(text, start, types)
    override fun findBestGenericMatch(text: String, start: Int, types: Set<GenericNameType>) = genericNames().findBestMatch(text, start, types)
    override fun getReferenceZoneID(metaZone: String?, region: String) = names!!.getReferenceZoneID(metaZone, region)
    override fun getTimeZone(id: String?) = TimeZone.getTimeZone(id)
    override fun getCustomTimeZone(offset: Int) = SimpleTimeZone(offset, ZoneMetaCore.customIDForOffset(offset))
    override fun getDefaultParseOptions(): Set<TimeZoneFormat.ParseOption>? = this@parseOwner.getDefaultParseOptions()
    override fun parseLocalizedOffset(core: LocalizedGMTOffsetCore, text: String, pos: ParsePosition, short: Boolean): Int =
        if (short) parseOffsetShortLocalizedGMT(text, pos) else parseOffsetLocalizedGMT(text, pos)
}
internal expect fun timeZoneGenericPattern(pattern: String?): TimeZoneGenericNamesCore.PatternFormatter
