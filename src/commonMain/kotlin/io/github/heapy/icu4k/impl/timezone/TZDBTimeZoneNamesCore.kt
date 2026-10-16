// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.MatchInfo

/** TZDB abbreviations and regional prefix matching from TZDBTimeZoneNames. */
internal class TZDBTimeZoneNamesCore(private val locale: ULocale?) {
    private val regionLock = createAvailabilityLock()
    private var region: String? = null
    fun getAvailableMetaZoneIDs(): Set<String> = TimeZoneNamesMappings.getAvailableMetaZoneIDs()
    fun getAvailableMetaZoneIDs(id: String?): Set<String> = TimeZoneNamesMappings.getAvailableMetaZoneIDs(id)
    fun getMetaZoneID(id: String?, date: Long): String? = TimeZoneNamesMappings.getMetaZoneID(id,date)
    fun getReferenceZoneID(id: String?, region: String?): String? = TimeZoneNamesMappings.getReferenceZoneID(id,region)
    fun getMetaZoneDisplayName(id: String?, type: NameType?): String? {
        if (id.isNullOrEmpty() || (type != NameType.SHORT_STANDARD && type != NameType.SHORT_DAYLIGHT)) return null
        return names(id).get(type)
    }
    @Suppress("UNUSED_PARAMETER")
    fun getTimeZoneDisplayName(id: String?, type: NameType?): String? = null
    fun getExemplarLocationName(id: String?): String? = TimeZoneNamesDataCore.getDefaultExemplarLocationName(id)
    fun getDisplayName(id: String?, type: NameType?, date: Long): String? = getMetaZoneDisplayName(getMetaZoneID(id,date),type)
    fun find(text: CharSequence?, start: Int, types: Set<NameType>?): Collection<MatchInfo> {
        if (text == null || text.length == 0 || start < 0 || start >= text.length) throw IllegalArgumentException("bad input text or range")
        val trie = prepareFind()
        val targetRegion = regionLock.withLock {
            region ?: (locale!!.getCountry().ifEmpty { ULocale.addLikelySubtags(locale).getCountry().ifEmpty { "001" } }).also { region = it }
        }
        val handler = SearchHandler(types,targetRegion)
        trie.find(text,start,handler)
        return handler.matches ?: emptyTimeZoneNameMatches()
    }
    private class SearchHandler(private val types: Set<NameType>?, private val region: String) : TextTrieMap.ResultHandler<NameInfo> {
        var matches: MutableCollection<MatchInfo>? = null
        override fun handlePrefixMatch(matchLength: Int, values: MutableIterator<NameInfo>): Boolean {
            var match: NameInfo? = null
            var defaultMatch: NameInfo? = null
            while (values.hasNext()) {
                val info = values.next()
                if (types != null && info.type !in types) continue
                if (info.regions == null) {
                    if (defaultMatch == null) { match = info; defaultMatch = info }
                } else {
                    if (info.regions.any { region == it }) { match = info; break }
                    if (match == null) match = info
                }
            }
            match?.let {
                var type = it.type
                // Preserve the source's null-filter failure for ambiguous abbreviations.
                if (it.ambiguous && (type == NameType.SHORT_STANDARD || type == NameType.SHORT_DAYLIGHT) &&
                    types!!.contains(NameType.SHORT_STANDARD) && types.contains(NameType.SHORT_DAYLIGHT)) type = NameType.SHORT_GENERIC
                if (matches == null) matches = createTimeZoneNameMatches()
                matches!!.add(MatchInfo(type,null,it.id,matchLength))
            }
            return true
        }
    }
    private class Names(val standard: String?, val daylight: String?, val regions: Array<String>?) {
        fun get(type: NameType): String? = when(type) { NameType.SHORT_STANDARD -> standard; NameType.SHORT_DAYLIGHT -> daylight; else -> null }
    }
    private class NameInfo(val id: String, val type: NameType, val ambiguous: Boolean, val regions: Array<String>?)
    companion object {
        private val zoneStrings = UResourceBundle.getBundleInstance(ICUData.ICU_ZONE_BASE_NAME,"tzdbNames").get("zoneStrings")
        private val lock = createAvailabilityLock()
        private val cache = mutableMapOf<String,Names>()
        private var trie: TextTrieMap<NameInfo>? = null
        private val empty = Names(null,null,null)
        private fun names(id: String): Names = lock.withLock {
            cache[id] ?: load(id).also { cache[internTimeZoneNameID(id)] = it }
        }
        private fun load(id: String): Names {
            val table = try { zoneStrings.get("meta:$id") } catch (_: MissingResourceException) { return empty }
            fun name(key: String): String? = try { table.getString(key) } catch (_: MissingResourceException) { null }
            val standard = name("ss")
            val daylight = name("sd")
            if (standard == null && daylight == null) return empty
            val regions = try {
                val resource = table.get("parseRegions")
                when(resource.getType()) { UResourceBundle.STRING -> arrayOf(resource.getString()); UResourceBundle.ARRAY -> resource.getStringArray(); else -> null }
            } catch (_: MissingResourceException) { null }
            return Names(standard,daylight,regions)
        }
        private fun prepareFind(): TextTrieMap<NameInfo> = lock.withLock {
            trie ?: TextTrieMap<NameInfo>(true).also { result ->
                for (id in TimeZoneNamesMappings.getAvailableMetaZoneIDs()) {
                    val names = names(id)
                    val ambiguous = names.standard != null && names.standard == names.daylight
                    val interned = internTimeZoneNameID(id)
                    names.standard?.let { result.put(it,NameInfo(interned,NameType.SHORT_STANDARD,ambiguous,names.regions)) }
                    names.daylight?.let { result.put(it,NameInfo(interned,NameType.SHORT_DAYLIGHT,ambiguous,names.regions)) }
                }
                trie = result
            }
        }
    }
}
