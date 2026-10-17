// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.timezone.*
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.TimeZone
import io.github.heapy.icu4k.util.ULocale

/** ICU's CLDR time-zone name provider. */
open class TimeZoneNamesImpl(locale: ULocale?) : TimeZoneNames() {
    @kotlin.jvm.Transient private var data: TimeZoneNamesDataCore? = null
    init { initialize(locale) }
    internal fun initialize(locale: ULocale?) {
        data = TimeZoneNamesDataCore(locale, {
            val zone = TimeZone.getDefault()
            if (zone is OlsonTimeZone) zone.getCanonicalID() else ZoneMetaCore.getCanonicalCLDRID(zone.getID())
        }, { getAvailableMetaZoneIDs(it) })
    }
    override fun getAvailableMetaZoneIDs(): Set<String> = TimeZoneNamesMappings.getAvailableMetaZoneIDs()
    override fun getAvailableMetaZoneIDs(tzID: String?): Set<String> = TimeZoneNamesMappings.getAvailableMetaZoneIDs(tzID)
    override fun getMetaZoneID(tzID: String?, date: Long): String? = TimeZoneNamesMappings.getMetaZoneID(tzID, date)
    override fun getReferenceZoneID(mzID: String?, region: String?): String? = TimeZoneNamesMappings.getReferenceZoneID(mzID, region)
    override fun getMetaZoneDisplayName(mzID: String?, type: NameType?): String? = data!!.getMetaZoneDisplayName(mzID, type?.toCore())
    override fun getTimeZoneDisplayName(tzID: String?, type: NameType?): String? = data!!.getTimeZoneDisplayName(tzID, type?.toCore())
    override fun getExemplarLocationName(tzID: String?): String? = data!!.getExemplarLocationName(tzID)
    override fun find(text: CharSequence?, start: Int, types: Set<NameType>?): Collection<MatchInfo> =
        data!!.find(text, start, types?.mapTo(mutableSetOf()) { it.toCore() }).publicMatches()
    @Deprecated("This API is ICU internal only.")
    override fun loadAllDisplayNames() { data!!.loadAllDisplayNames() }
    @Deprecated("This API is ICU internal only.")
    override fun getDisplayNames(tzID: String?, types: Array<out NameType?>?, date: Long, dest: Array<String?>?, destOffset: Int) {
        if (tzID.isNullOrEmpty()) return
        data!!.getDisplayNames(tzID, types?.map { it?.toCore() }?.toTypedArray(), date, dest, destOffset, ::getMetaZoneID)
    }
    private fun writeObject(output: PluralObjectOutputStream) = writeTimeZoneNamesLocale(output, data!!.getLocale())
    private fun readObject(input: PluralObjectInputStream) { initialize(readTimeZoneNamesLocale(input)) }
    companion object {
        private const val serialVersionUID = -2179814848495897472L
        @kotlin.jvm.JvmStatic
        fun getDefaultExemplarLocationName(tzID: String?): String? = TimeZoneNamesDataCore.getDefaultExemplarLocationName(tzID)
    }
}
internal expect fun writeTimeZoneNamesLocale(output: PluralObjectOutputStream, locale: ULocale)
internal expect fun readTimeZoneNamesLocale(input: PluralObjectInputStream): ULocale?
