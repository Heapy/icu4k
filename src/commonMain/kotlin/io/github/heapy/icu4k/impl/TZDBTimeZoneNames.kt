// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.timezone.*
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.ULocale

/** IANA abbreviations with locale-region disambiguation. */
open class TZDBTimeZoneNames(private val _locale: ULocale?) : TimeZoneNames() {
    @kotlin.jvm.Transient @kotlin.concurrent.Volatile private var data: TZDBTimeZoneNamesCore? = null
    private fun core(): TZDBTimeZoneNamesCore = data ?: TZDBTimeZoneNamesCore(_locale).also { data = it }
    override fun getAvailableMetaZoneIDs(): Set<String> = TimeZoneNamesMappings.getAvailableMetaZoneIDs()
    override fun getAvailableMetaZoneIDs(tzID: String?): Set<String> = TimeZoneNamesMappings.getAvailableMetaZoneIDs(tzID)
    override fun getMetaZoneID(tzID: String?, date: Long): String? = TimeZoneNamesMappings.getMetaZoneID(tzID, date)
    override fun getReferenceZoneID(mzID: String?, region: String?): String? = TimeZoneNamesMappings.getReferenceZoneID(mzID, region)
    override fun getMetaZoneDisplayName(mzID: String?, type: NameType?): String? = core().getMetaZoneDisplayName(mzID, type?.toCore())
    override fun getTimeZoneDisplayName(tzID: String?, type: NameType?): String? = null
    override fun find(text: CharSequence?, start: Int, types: Set<NameType>?): Collection<MatchInfo> =
        core().find(text, start, types?.mapTo(mutableSetOf()) { it.toCore() }).publicMatches()
    companion object { private const val serialVersionUID = 1L }
}
