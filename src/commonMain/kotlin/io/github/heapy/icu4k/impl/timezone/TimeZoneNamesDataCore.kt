// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2008-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.*

/** Localized TimeZoneNamesImpl formatting and parsing engine; public factories are separate. */
internal class TimeZoneNamesDataCore(
    locale: ULocale?,
    defaultCanonicalZoneID: () -> String?,
    availableMetaZones: (String) -> Set<String>? = TimeZoneNamesMappings::getAvailableMetaZoneIDs,
) {
    enum class NameType { LONG_GENERIC, LONG_STANDARD, LONG_DAYLIGHT, SHORT_GENERIC, SHORT_STANDARD, SHORT_DAYLIGHT, EXEMPLAR_LOCATION }
    private val zoneStrings = UResourceBundle.getBundleInstance(ICUData.ICU_ZONE_BASE_NAME, locale).get("zoneStrings") as ICUResourceBundle
    private val lock = createAvailabilityLock()
    private val metaNames = createTimeZoneNamesMap<Names>()
    private val zoneNames = createTimeZoneNamesMap<Names>()
    private var namesFullyLoaded = false
    private val namesTrie = TextTrieMap<NameInfo>(true)
    private var namesTrieFullyLoaded = false
    init {
        val id = defaultCanonicalZoneID()
        if (!id.isNullOrEmpty()) lock.withLock {
            loadTimeZoneNames(id)
            for (mz in availableMetaZones(id)!!) loadMetaZoneNames(mz)
        }
    }
    fun getLocale(): ULocale = zoneStrings.getULocale()
    fun getAvailableMetaZoneIDs(): Set<String> = TimeZoneNamesMappings.getAvailableMetaZoneIDs()
    fun getAvailableMetaZoneIDs(id: String?): Set<String> = TimeZoneNamesMappings.getAvailableMetaZoneIDs(id)
    fun getMetaZoneID(id: String?, date: Long): String? = TimeZoneNamesMappings.getMetaZoneID(id,date)
    fun getReferenceZoneID(id: String?, region: String?): String? = TimeZoneNamesMappings.getReferenceZoneID(id,region)
    fun getMetaZoneDisplayName(id: String?, type: NameType?): String? = if (id.isNullOrEmpty()) null else loadMetaZoneNames(id).get(type)
    fun getTimeZoneDisplayName(id: String?, type: NameType?): String? = if (id.isNullOrEmpty()) null else loadTimeZoneNames(id).get(type)
    fun getExemplarLocationName(id: String?): String? = if (id.isNullOrEmpty()) null else loadTimeZoneNames(id).get(NameType.EXEMPLAR_LOCATION)
    /** Concrete formatting path; the public base class will supply virtual getter dispatch. */
    fun getDisplayName(id: String?, type: NameType?, date: Long): String? =
        getTimeZoneDisplayName(id,type) ?: getMetaZoneDisplayName(getMetaZoneID(id,date),type)
    fun getDisplayNames(id: String?, types: Array<out NameType?>?, date: Long, dest: Array<String?>?, destOffset: Int,
        metaZoneID: (String,Long) -> String? = TimeZoneNamesMappings::getMetaZoneID) {
        if (id.isNullOrEmpty()) return
        val names = loadTimeZoneNames(id)
        var meta: Names? = null
        val length = types!!.size
        for (i in 0 until length) {
            val type = types[i]
            var name = names.get(type)
            if (name == null) {
                if (meta == null) {
                    val mz = metaZoneID(id,date)
                    meta = if (mz.isNullOrEmpty()) EMPTY else loadMetaZoneNames(mz)
                }
                name = meta.get(type)
            }
            val target = dest ?: throw NullPointerException()
            val index = destOffset + i
            if (index !in target.indices) throw IndexOutOfBoundsException()
            target[index] = name
        }
    }
    open class MatchInfo(type: NameType?, private val zoneID: String?, private val metaID: String?, private val length: Int) {
        private val type: NameType
        init {
            if (type == null) throw IllegalArgumentException("nameType is null")
            if (zoneID == null && metaID == null) throw IllegalArgumentException("Either tzID or mzID must be available")
            if (length <= 0) throw IllegalArgumentException("matchLength must be positive value")
            this.type = type
        }
        open fun tzID(): String? = zoneID
        open fun mzID(): String? = metaID
        open fun nameType(): NameType = type
        open fun matchLength(): Int = length
    }
    private class NameInfo(val zoneID: String?, val metaID: String?, val type: NameType)
    private class NameSearchHandler(private val types: Set<NameType>?) : TextTrieMap.ResultHandler<NameInfo> {
        var maxMatchLength = 0
        private var matches: MutableCollection<MatchInfo>? = null
        override fun handlePrefixMatch(matchLength: Int, values: MutableIterator<NameInfo>): Boolean {
            while (values.hasNext()) {
                val info = values.next()
                if (types != null && info.type !in types) continue
                if (info.zoneID == null && assertionsEnabled && info.metaID == null) throw AssertionError()
                val match = if (info.zoneID != null) MatchInfo(info.type,info.zoneID,null,matchLength)
                    else MatchInfo(info.type,null,info.metaID,matchLength)
                if (matches == null) matches = createTimeZoneNameMatches()
                matches!!.add(match)
                if (matchLength > maxMatchLength) maxMatchLength = matchLength
            }
            return true
        }
        fun resetResults() { matches = null; maxMatchLength = 0 }
        fun getMatches(): Collection<MatchInfo> = matches ?: emptyTimeZoneNameMatches()
    }
    fun find(text: CharSequence?, start: Int, types: Set<NameType>?): Collection<MatchInfo> = lock.withLock {
        if (text == null || text.length == 0 || start < 0 || start >= text.length) throw IllegalArgumentException("bad input text or range")
        val handler = NameSearchHandler(types)
        doFind(handler,text,start)?.let { return@withLock it }
        addAllNamesIntoTrie()
        doFind(handler,text,start)?.let { return@withLock it }
        loadAllDisplayNames()
        for (id in ZoneMetaCore.getAvailableIDs(ZoneMetaCore.SystemTimeZoneType.CANONICAL)) {
            if (!zoneNames.containsKey(id)) storeZone(id,null)
        }
        addAllNamesIntoTrie()
        namesTrieFullyLoaded = true
        doFind(handler,text,start)!!
    }
    private fun doFind(handler: NameSearchHandler, text: CharSequence, start: Int): Collection<MatchInfo>? {
        handler.resetResults()
        namesTrie.find(text,start,handler)
        return if (handler.maxMatchLength == text.length - start || namesTrieFullyLoaded) handler.getMatches() else null
    }
    private fun addAllNamesIntoTrie() {
        for ((id,names) in zoneNames) names.addIntoTrie(null,id,namesTrie)
        for ((id,names) in metaNames) names.addIntoTrie(id,null,namesTrie)
    }
    fun loadAllDisplayNames() = lock.withLock {
        if (!namesFullyLoaded) {
            namesFullyLoaded = true
            val loaders = createTimeZoneNamesLoaderMap<NamesLoader?>()
            zoneStrings.getAllItemsWithFallback("", object : UResource.Sink() {
                override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                    val table = value.getTable()
                    var i = 0
                    while (table.getKeyAndValue(i++,key,value)) {
                        if (assertionsEnabled && value.isNoInheritanceMarker()) throw AssertionError()
                        if (value.getType() != UResourceBundle.TABLE) continue
                        val savedKey = key.toString()
                        val loader = if (loaders.containsKey(key)) loaders[key] else {
                            val present = if (savedKey.startsWith("meta:")) metaNames.containsKey(savedKey.substring(5))
                                else zoneNames.containsKey(savedKey.replace(':','/'))
                            (if (present) null else NamesLoader()).also { loaders[key.clone()] = it }
                        }
                        loader?.put(key,value,noFallback)
                    }
                }
            })
            for ((key,loader) in loaders) {
                if (loader == null) continue
                val names = loader.getNames()
                val nameKey = key.toString()
                if (nameKey.startsWith("meta:")) storeMeta(nameKey.substring(5),names)
                else storeZone(nameKey.replace(':','/'),names)
            }
        }
    }
    private fun loadMetaZoneNames(id: String): Names = lock.withLock {
        metaNames[id] ?: run {
            val loader = NamesLoader()
            loader.load(zoneStrings,"meta:$id")
            storeMeta(id,loader.getNames())
        }
    }
    private fun loadTimeZoneNames(id: String): Names = lock.withLock {
        zoneNames[id] ?: run {
            val loader = NamesLoader()
            loader.load(zoneStrings,id.replace('/',':'))
            storeZone(id,loader.getNames())
        }
    }
    private fun storeMeta(id: String, names: Array<String?>?): Names = (if (names == null) EMPTY else Names(names)).also { metaNames[internTimeZoneNameID(id)] = it }
    private fun storeZone(id: String, names: Array<String?>?): Names {
        val actual = names ?: arrayOfNulls(1)
        if (actual[0] == null) actual[0] = getDefaultExemplarLocationName(id)
        return Names(actual).also { zoneNames[internTimeZoneNameID(id)] = it }
    }
    private class Names(private val names: Array<String?>?) {
        private var didAddIntoTrie = names == null
        fun addIntoTrie(metaID: String?, zoneID: String?, trie: TextTrieMap<NameInfo>) {
            if (names == null || didAddIntoTrie) return
            didAddIntoTrie = true
            for (i in names.indices) {
                val name = names[i] ?: continue
                trie.put(name,NameInfo(zoneID,metaID,indexTypes[i]))
            }
        }
        fun get(type: NameType?): String? {
            val index = when(type!!) {
                NameType.EXEMPLAR_LOCATION -> 0
                NameType.LONG_GENERIC -> 1
                NameType.LONG_STANDARD -> 2
                NameType.LONG_DAYLIGHT -> 3
                NameType.SHORT_GENERIC -> 4
                NameType.SHORT_STANDARD -> 5
                NameType.SHORT_DAYLIGHT -> 6
            }
            return if (names != null && index < names.size) names[index] else null
        }
    }
    private class NamesLoader : UResource.Sink() {
        private var names: Array<String?>? = null
        fun load(bundle: ICUResourceBundle, key: String) {
            names = null
            try { bundle.getAllItemsWithFallback(key,this) } catch (_: MissingResourceException) { }
        }
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val table = value.getTable()
            var i = 0
            while (table.getKeyAndValue(i++,key,value)) {
                if (assertionsEnabled && value.getType() != UResourceBundle.STRING) throw AssertionError()
                if (names == null) names = arrayOfNulls(7)
                val index = if (key.length != 2) -1 else when(key[0]) {
                    'e' -> if (key[1] == 'c') 0 else -1
                    'l' -> when(key[1]) { 'g' -> 1; 's' -> 2; 'd' -> 3; else -> -1 }
                    's' -> when(key[1]) { 'g' -> 4; 's' -> 5; 'd' -> 6; else -> -1 }
                    else -> -1
                }
                if (index >= 0 && names!![index] == null) names!![index] = value.getString()
            }
        }
        fun getNames(): Array<String?>? {
            val actual = names ?: return null
            var length = 0
            for (i in actual.indices) {
                val name = actual[i]
                if (name != null) {
                    if (name == ICUResourceBundle.NO_INHERITANCE_MARKER) actual[i] = null else length = i + 1
                }
            }
            return when(length) { 7 -> actual; 0 -> null; else -> actual.copyOf(length) }
        }
    }
    companion object {
        private val indexTypes = arrayOf(NameType.EXEMPLAR_LOCATION,NameType.LONG_GENERIC,NameType.LONG_STANDARD,
            NameType.LONG_DAYLIGHT,NameType.SHORT_GENERIC,NameType.SHORT_STANDARD,NameType.SHORT_DAYLIGHT)
        private val EMPTY = Names(null)
        private val assertionsEnabled = timeZoneNamesAssertionsEnabled()
        fun getDefaultExemplarLocationName(id: String?): String? {
            if (id.isNullOrEmpty()) return null
            // Pattern.matches("Etc/.*|SystemV/.*|.*/Riyadh8[7-9]") uses Java's five line terminators.
            val singleLine = id.none { it == '\n' || it == '\r' || it == '\u0085' || it == '\u2028' || it == '\u2029' }
            if (singleLine && (id.startsWith("Etc/") || id.startsWith("SystemV/") ||
                    (id.endsWith("/Riyadh87") || id.endsWith("/Riyadh88") || id.endsWith("/Riyadh89")))) return null
            val slash = id.lastIndexOf('/')
            return if (slash > 0 && slash + 1 < id.length) id.substring(slash + 1).replace('_',' ') else null
        }
    }
}
internal expect fun timeZoneNamesAssertionsEnabled(): Boolean

internal expect fun internTimeZoneNameID(value: String): String

internal expect fun <V> createTimeZoneNamesMap(): MutableMap<String,V>
internal expect fun <V> createTimeZoneNamesLoaderMap(): MutableMap<UResource.Key,V>
internal expect fun <T> createTimeZoneNameMatches(): MutableCollection<T>
internal expect fun <T> emptyTimeZoneNameMatches(): MutableCollection<T>

internal object EmptyTimeZoneNameMatches : AbstractMutableList<Any?>() {
    override val size: Int get() = 0
    override fun get(index: Int): Nothing = throw IndexOutOfBoundsException()
    override fun set(index: Int, element: Any?): Nothing = throw UnsupportedOperationException()
    override fun add(index: Int, element: Any?): Nothing = throw UnsupportedOperationException()
    override fun removeAt(index: Int): Nothing = throw UnsupportedOperationException()
    override fun clear() {}
}
