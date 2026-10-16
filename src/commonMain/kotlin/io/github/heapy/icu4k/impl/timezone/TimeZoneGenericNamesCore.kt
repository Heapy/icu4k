// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2011-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.GenericNameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneFormatParseCore.TimeType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.NameType
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamesDataCore.MatchInfo
import io.github.heapy.icu4k.text.LocaleDisplayNames
import io.github.heapy.icu4k.util.*
import kotlin.concurrent.Volatile

/** Generic location/partial/non-location names and lazy parsing from TimeZoneGenericNames. */
internal class TimeZoneGenericNamesCore<Z> private constructor(private val locale: ULocale?, private val owner: OwnerQueries<Z>) {
    enum class Pattern(val key: String, val defaultValue: String) {
        REGION_FORMAT("regionFormat","({0})"), FALLBACK_FORMAT("fallbackFormat","{1} ({0})")
    }
    interface Names {
        fun getAvailableMetaZoneIDs(id: String): Set<String>?
        fun getMetaZoneID(id: String, date: Long): String?
        fun getReferenceZoneID(id: String?, region: String): String?
        fun getMetaZoneDisplayName(id: String, type: NameType): String?
        fun getTimeZoneDisplayName(id: String, type: NameType): String?
        fun getDisplayName(id: String, type: NameType, date: Long): String?
        fun getExemplarLocationName(id: String): String?
        fun find(text: String, start: Int, types: Set<NameType>): Collection<MatchInfo>?
    }
    fun interface PatternFormatter { fun format(args: Array<out String?>): String }
    interface OwnerQueries<Z> {
        fun getDefaultNames(locale: ULocale?): Names?
        fun getDefaultTimeZone(): Z?
        fun getCanonicalCLDRID(zone: Z?): String?
        fun getOffset(zone: Z?, date: Long, local: Boolean, offsets: IntArray)
        fun getBasicTimeZone(zone: Z?): BasicTimeZoneCore?
        fun getFrozenTimeZone(id: String): Z?
        /** Full JDK MessageFormat semantics, including construction-time validation, are required. */
        fun createPatternFormatter(pattern: String?): PatternFormatter
        fun getGenericLocationName(core: TimeZoneGenericNamesCore<Z>, id: String): String? = core.getGenericLocationName(id)
        fun isFrozen(core: TimeZoneGenericNamesCore<Z>): Boolean = core.isFrozen()
    }
    open class GenericMatchInfo(private val genericType: GenericNameType?, id: String?, length: Int, type: TimeType? = TimeType.UNKNOWN) :
        TimeZoneFormatParseCore.GenericMatchInfo(id,length,type) {
        open fun nameType(): GenericNameType? = genericType
    }
    private class NameInfo(val id: String, val type: GenericNameType)
    private val lock = createAvailabilityLock()
    private var names: Names? = null
    @Volatile private var frozen = false
    private var region: String? = null
    private var displayNamesRef: CacheReference<LocaleDisplayNames>? = null
    private var patternFormatters: Array<PatternFormatter?>? = null
    private var locationNames = createGenericNameMap()
    private var partialNames = createGenericNameMap()
    private var trie: TextTrieMap<NameInfo>? = TextTrieMap(true)
    private var trieFullyLoaded = false
    constructor(locale: ULocale?, names: Names?, owner: OwnerQueries<Z>) : this(locale,owner) {
        this.names = names ?: owner.getDefaultNames(locale)
        val zone = owner.getDefaultTimeZone()
        val id = owner.getCanonicalCLDRID(zone)
        if (id != null) loadStrings(id)
    }
    fun isFrozen(): Boolean = frozen
    fun freeze(): TimeZoneGenericNamesCore<Z> { frozen = true; return this }
    fun cloneAsThawed(): TimeZoneGenericNamesCore<Z> = TimeZoneGenericNamesCore(locale,owner).also {
        it.names = names; it.region = region; it.displayNamesRef = displayNamesRef
        it.patternFormatters = patternFormatters; it.locationNames = locationNames; it.partialNames = partialNames
        it.trie = trie; it.trieFullyLoaded = trieFullyLoaded
    }
    fun getDisplayName(zone: Z?, type: GenericNameType?, date: Long): String? {
        var name: String? = null
        when(type!!) {
            GenericNameType.LOCATION -> {
                val id = owner.getCanonicalCLDRID(zone)
                if (id != null) name = owner.getGenericLocationName(this,id)
            }
            GenericNameType.LONG, GenericNameType.SHORT -> {
                name = nonLocationName(zone,type,date)
                if (name == null) {
                    val id = owner.getCanonicalCLDRID(zone)
                    if (id != null) name = owner.getGenericLocationName(this,id)
                }
            }
        }
        return name
    }
    fun getGenericLocationName(id: String?): String? {
        if (id.isNullOrEmpty()) return null
        locationNames.get(id)?.let { return it.ifEmpty { null } }
        var name: String? = null
        val primary = Output<Boolean>()
        val country = ZoneMetaCore.getCanonicalCountry(id,primary)
        if (country != null) {
            name = if (primary.value!!) formatPattern(Pattern.REGION_FORMAT,localeDisplayNames().regionDisplayName(country))
                else formatPattern(Pattern.REGION_FORMAT,names!!.getExemplarLocationName(id))
        }
        if (name == null) locationNames.putIfAbsent(internTimeZoneNameID(id),"")
        else lock.withLock {
            val internedID = internTimeZoneNameID(id)
            val previous = locationNames.putIfAbsent(internedID,internTimeZoneNameID(name!!))
            if (previous == null) trie!!.put(name,NameInfo(internedID,GenericNameType.LOCATION)) else name = previous
        }
        return name
    }
    fun setFormatPattern(type: Pattern?, pattern: String?): TimeZoneGenericNamesCore<Z> {
        if (owner.isFrozen(this)) throw UnsupportedOperationException("Attempt to modify frozen object")
        if (!locationNames.isEmpty()) locationNames = createGenericNameMap()
        if (!partialNames.isEmpty()) partialNames = createGenericNameMap()
        // Source invalidates the trie by assigning null, without rebuilding it.
        trie = null
        trieFullyLoaded = false
        if (patternFormatters == null) patternFormatters = arrayOfNulls(Pattern.entries.size)
        val index = type!!.ordinal
        patternFormatters!![index] = owner.createPatternFormatter(pattern)
        return this
    }
    private fun nonLocationName(zone: Z?, type: GenericNameType, date: Long): String? {
        val id = owner.getCanonicalCLDRID(zone) ?: return null
        val nameType = if (type == GenericNameType.LONG) NameType.LONG_GENERIC else NameType.SHORT_GENERIC
        var name = names!!.getTimeZoneDisplayName(id,nameType)
        if (name != null) return name
        val meta = names!!.getMetaZoneID(id,date)
        if (meta != null) {
            var useStandard = false
            val offsets = intArrayOf(0,0)
            owner.getOffset(zone,date,false,offsets)
            if (offsets[1] == 0) {
                useStandard = true
                val basic = owner.getBasicTimeZone(zone)
                if (basic != null) {
                    val before = basic.getPreviousTransition(date,true)
                    if (before != null && date-before.getTime() < DST_RANGE && before.getFrom()!!.getDSTSavings() != 0) useStandard = false
                    else {
                        val after = basic.getNextTransition(date,false)
                        if (after != null && after.getTime()-date < DST_RANGE && after.getTo()!!.getDSTSavings() != 0) useStandard = false
                    }
                } else {
                    val other = IntArray(2)
                    owner.getOffset(zone,date-DST_RANGE,false,other)
                    if (other[1] != 0) useStandard = false
                    else { owner.getOffset(zone,date+DST_RANGE,false,other); if (other[1] != 0) useStandard = false }
                }
            }
            if (useStandard) {
                val standardType = if (nameType == NameType.LONG_GENERIC) NameType.LONG_STANDARD else NameType.SHORT_STANDARD
                val standard = names!!.getDisplayName(id,standardType,date)
                if (standard != null) {
                    name = standard
                    val generic = names!!.getMetaZoneDisplayName(meta,nameType)
                    if (generic != null && standard.length == generic.length && localizedGMTRegionMatches(standard,0,generic,0,standard.length)) name = null
                }
            }
            if (name == null) {
                val metaName = names!!.getMetaZoneDisplayName(meta,nameType)
                if (metaName != null) {
                    val goldenID = names!!.getReferenceZoneID(meta,targetRegion())
                    if (goldenID != null && goldenID != id) {
                        val golden = owner.getFrozenTimeZone(goldenID)
                        val goldenOffsets = intArrayOf(0,0)
                        owner.getOffset(golden,date+offsets[0]+offsets[1],true,goldenOffsets)
                        name = if (offsets[0] != goldenOffsets[0] || offsets[1] != goldenOffsets[1]) partialLocationName(id,meta,nameType == NameType.LONG_GENERIC,metaName) else metaName
                    } else name = metaName
                }
            }
        }
        return name
    }
    private fun formatPattern(pattern: Pattern, vararg args: String?): String = lock.withLock {
        if (patternFormatters == null) patternFormatters = arrayOfNulls(Pattern.entries.size)
        val index = pattern.ordinal
        if (patternFormatters!![index] == null) {
            val text = try {
                val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_ZONE_BASE_NAME,locale) as ICUResourceBundle
                bundle.getStringWithFallback("zoneStrings/"+pattern.key)
            } catch (_: MissingResourceException) { pattern.defaultValue }
            patternFormatters!![index] = owner.createPatternFormatter(text)
        }
        patternFormatters!![index]!!.format(args)
    }
    private fun localeDisplayNames(): LocaleDisplayNames = lock.withLock {
        var displayNames = displayNamesRef?.get()
        if (displayNames == null) {
            displayNames = LocaleDisplayNames.getInstance(locale)
            displayNamesRef = createGenericDisplayNamesReference(displayNames)
        }
        displayNames
    }
    private fun targetRegion(): String = lock.withLock {
        if (region == null) {
            region = locale!!.getCountry()
            if (region!!.isEmpty()) {
                region = ULocale.addLikelySubtags(locale).getCountry()
                if (region!!.isEmpty()) region = "001"
            }
        }
        region!!
    }
    private fun loadStrings(id: String?) = lock.withLock {
        if (id.isNullOrEmpty()) return@withLock
        owner.getGenericLocationName(this,id)
        for (meta in names!!.getAvailableMetaZoneIDs(id)!!) {
            val golden = names!!.getReferenceZoneID(meta,targetRegion())
            if (id != golden) for (type in GENERIC_NON_LOCATION_TYPES) {
                val name = names!!.getMetaZoneDisplayName(meta,type)
                if (name != null) partialLocationName(id,meta,type == NameType.LONG_GENERIC,name)
            }
        }
    }
    private fun partialLocationName(id: String, meta: String, long: Boolean, metaName: String): String {
        val key = id+"&"+meta+"#"+if (long) "L" else "S"
        partialNames.get(key)?.let { return it }
        val country = ZoneMetaCore.getCanonicalCountry(id)
        var location: String?
        if (country != null) {
            val golden = names!!.getReferenceZoneID(meta,country)
            location = if (id == golden) localeDisplayNames().regionDisplayName(country) else names!!.getExemplarLocationName(id)
        } else location = names!!.getExemplarLocationName(id) ?: id
        var name = formatPattern(Pattern.FALLBACK_FORMAT,location,metaName)
        lock.withLock {
            val previous = partialNames.putIfAbsent(internTimeZoneNameID(key),internTimeZoneNameID(name))
            if (previous == null) trie!!.put(name,NameInfo(internTimeZoneNameID(id),if (long) GenericNameType.LONG else GenericNameType.SHORT)) else name = previous
        }
        return name
    }
    private class SearchHandler(private val types: Set<GenericNameType>?) : TextTrieMap.ResultHandler<NameInfo> {
        var maximum = 0
        var matches: MutableCollection<GenericMatchInfo>? = null
        override fun handlePrefixMatch(matchLength: Int, values: MutableIterator<NameInfo>): Boolean {
            while (values.hasNext()) {
                val info = values.next()
                if (types != null && info.type !in types) continue
                if (matches == null) matches = createTimeZoneNameMatches()
                matches!!.add(GenericMatchInfo(info.type,info.id,matchLength))
                if (matchLength > maximum) maximum = matchLength
            }
            return true
        }
        fun reset() { matches = null; maximum = 0 }
    }
    fun findBestMatch(text: String?, start: Int, types: Set<GenericNameType>?): GenericMatchInfo? {
        validate(text,start)
        var best: GenericMatchInfo? = null
        val matches = findTimeZoneNames(text!!,start,types)
        if (matches != null) {
            var longest: MatchInfo? = null
            for (match in matches) if (longest == null || match.matchLength() > longest.matchLength()) longest = match
            if (longest != null) {
                best = convert(longest)
                if (best.matchLength() == text.length-start && best.timeType() != TimeType.STANDARD) return best
            }
        }
        val local = findLocal(text,start,types)
        if (local != null) for (match in local) if (best == null || match.matchLength() >= best.matchLength()) best = match
        return best
    }
    fun find(text: String?, start: Int, types: Set<GenericNameType>?): Collection<GenericMatchInfo>? {
        validate(text,start)
        var results = findLocal(text!!,start,types)
        val matches = findTimeZoneNames(text,start,types)
        if (matches != null) for (match in matches) {
            if (results == null) results = createTimeZoneNameMatches()
            results.add(convert(match))
        }
        return results
    }
    private fun convert(match: MatchInfo): GenericMatchInfo {
        val type: GenericNameType
        var timeType = TimeType.UNKNOWN
        when(match.nameType()) {
            NameType.LONG_STANDARD -> { type = GenericNameType.LONG; timeType = TimeType.STANDARD }
            NameType.LONG_GENERIC -> type = GenericNameType.LONG
            NameType.SHORT_STANDARD -> { type = GenericNameType.SHORT; timeType = TimeType.STANDARD }
            NameType.SHORT_GENERIC -> type = GenericNameType.SHORT
            else -> throw IllegalArgumentException("Unexpected MatchInfo name type - "+match.nameType())
        }
        var id = match.tzID()
        if (id == null) {
            val meta = match.mzID()
            checkAssertion(meta != null)
            id = names!!.getReferenceZoneID(meta,targetRegion())
        }
        checkAssertion(id != null)
        return GenericMatchInfo(type,id,match.matchLength(),timeType)
    }
    private fun findTimeZoneNames(text: String, start: Int, types: Set<GenericNameType>?): Collection<MatchInfo>? {
        val nameTypes = linkedSetOf<NameType>()
        if (GenericNameType.LONG in types!!) { nameTypes.add(NameType.LONG_GENERIC); nameTypes.add(NameType.LONG_STANDARD) }
        if (GenericNameType.SHORT in types) { nameTypes.add(NameType.SHORT_GENERIC); nameTypes.add(NameType.SHORT_STANDARD) }
        return if (nameTypes.isEmpty()) null else names!!.find(text,start,nameTypes)
    }
    private fun findLocal(text: String, start: Int, types: Set<GenericNameType>?): MutableCollection<GenericMatchInfo>? = lock.withLock {
        val handler = SearchHandler(types)
        trie!!.find(text,start,handler)
        if (handler.maximum == text.length-start || trieFullyLoaded) return@withLock handler.matches
        for (id in ZoneMetaCore.getAvailableIDs(ZoneMetaCore.SystemTimeZoneType.CANONICAL,null,null)) loadStrings(id)
        trieFullyLoaded = true
        handler.reset()
        trie!!.find(text,start,handler)
        handler.matches
    }
    companion object {
        private const val DST_RANGE = 184L*24*60*60*1000
        private val GENERIC_NON_LOCATION_TYPES = arrayOf(NameType.LONG_GENERIC,NameType.SHORT_GENERIC)
        private fun checkAssertion(value: Boolean) { if (timeZoneGenericNamesAssertionsEnabled() && !value) throw AssertionError() }
        private fun validate(text: String?, start: Int) { if (text == null || text.isEmpty() || start < 0 || start >= text.length) throw IllegalArgumentException("bad input text or range") }
    }
}
internal interface GenericNameMap {
    fun get(key: String): String?
    fun putIfAbsent(key: String, value: String): String?
    fun isEmpty(): Boolean
}
internal class PortableGenericNameMap : GenericNameMap {
    private val lock = createAvailabilityLock()
    private val map = mutableMapOf<String,String>()
    override fun get(key: String): String? = lock.withLock { map[key] }
    override fun putIfAbsent(key: String, value: String): String? = lock.withLock { map[key].also { if (it == null) map[key] = value } }
    override fun isEmpty(): Boolean = lock.withLock { map.isEmpty() }
}
internal expect fun createGenericNameMap(): GenericNameMap
internal expect fun createGenericDisplayNamesReference(value: LocaleDisplayNames): CacheReference<LocaleDisplayNames>
internal expect fun timeZoneGenericNamesAssertionsEnabled(): Boolean
