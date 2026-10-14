// © 2012 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.impl.locale.LocaleReadOnlySet
import io.github.heapy.icu4k.impl.locale.localeReadOnlyList
import io.github.heapy.icu4k.impl.locale.localeReadOnlySet
import kotlin.concurrent.atomics.AtomicReference

/** ICU territory identity, deprecated aliases and geographic/grouping containment. */
class Region private constructor(private val id: String, private var code: Int, private var type: RegionType) : Comparable<Region> {
    enum class RegionType { UNKNOWN, TERRITORY, WORLD, CONTINENT, SUBCONTINENT, GROUPING, DEPRECATED }
    private var containingRegion: Region? = null
    private val containedRegions = HashSet<Region>()
    private var preferredValues: MutableList<Region>? = null
    fun getContainingRegion(): Region? { data(); return containingRegion }
    fun getContainingRegion(type: RegionType?): Region? {
        data()
        val parent = containingRegion ?: return null
        return if (parent.type == type) parent else parent.getContainingRegion(type)
    }
    fun getContainedRegions(): Set<Region> { data(); return readOnlyRegions(containedRegions) }
    fun getContainedRegions(type: RegionType?): Set<Region> {
        data()
        val result = HashSet<Region>()
        for (region in getContainedRegions()) {
            if (region.type == type) result.add(region) else result.addAll(region.getContainedRegions(type))
        }
        return readOnlyRegions(result)
    }
    fun getPreferredValues(): List<Region>? {
        data()
        return if (type == RegionType.DEPRECATED) localeReadOnlyList(preferredValues!!) else null
    }
    fun contains(other: Region?): Boolean {
        data()
        val target = other!!
        if (containedRegions.any { it.compareTo(target) == 0 }) return true
        return readOnlyRegions(containedRegions).any { it.contains(target) }
    }
    fun getNumericCode(): Int = code
    fun getType(): RegionType = type
    override fun toString(): String = id
    override fun compareTo(other: Region): Int = id.compareTo(other.id)

    companion object {
        private class Data(val ids: Map<String, Region>, val numeric: Map<Int, Region>,
            val aliases: Map<String, Region>, val available: List<Set<Region>>)
        private val lock = createAvailabilityLock()
        private val loaded = AtomicReference<Data?>(null)
        private fun data(): Data = lock.withLock {
            loaded.load() ?: loadData().also { loaded.store(it) }
        }
        private fun readOnlyRegions(regions: Set<Region>): Set<Region> = localeReadOnlySet(LocaleReadOnlySet({ regions }, naturalOrder()))
        fun getInstance(id: String?): Region {
            if (id == null) throw NullPointerException()
            val data = data()
            val region = data.ids[id] ?: data.aliases[id] ?: throw IllegalArgumentException("Unknown region id: $id")
            return preferredSingleton(region)
        }
        fun getInstance(code: Int): Region {
            val data = data()
            val pad = if (code < 10) "00" else if (code < 100) "0" else ""
            val region = data.numeric[code] ?: data.aliases[pad + code.toString()]
                ?: throw IllegalArgumentException("Unknown region code: $code")
            return preferredSingleton(region)
        }
        private fun preferredSingleton(region: Region): Region =
            if (region.type == RegionType.DEPRECATED && region.preferredValues!!.size == 1) region.preferredValues!![0] else region
        fun getAvailable(type: RegionType?): Set<Region> {
            val data = data()
            return readOnlyRegions(data.available[type!!.ordinal])
        }
        private fun loadData(): Data {
            val ids = HashMap<String, Region>()
            val numeric = HashMap<Int, Region>()
            val aliases = HashMap<String, Region>()
            val regions = ArrayList<Region>()
            val metadata = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "metadata")
            val territoryAliases = metadata["alias"]["territory"]
            val supplemental = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "supplementalData")
            val mappings = supplemental["codeMappings"]
            val validity = supplemental["idValidity"]["region"]
            val containment = supplemental["territoryContainment"]
            val continents = containment["001"].getStringArray().asList()
            val groupings = containment["grouping"]
            val codes = ArrayList<String>()
            val all = validity["regular"].getStringArray().asList() +
                validity["macroregion"].getStringArray().asList() + validity["unknown"].getString()
            for (range in all) {
                val marker = range.indexOf('~')
                if (marker > 0) {
                    val prefix = range.substring(0, marker - 1)
                    for (last in range[marker - 1]..range[marker + 1]) codes.add(prefix + last)
                } else codes.add(range)
            }
            fun numericId(id: String): Boolean = id.length == 3 && id.all { it in '0'..'9' }
            for (id in codes) {
                val isNumeric = numericId(id)
                val region = Region(id, if (isNumeric) id.toInt() else -1,
                    if (isNumeric) RegionType.SUBCONTINENT else RegionType.TERRITORY)
                ids[id] = region
                if (isNumeric) numeric[region.code] = region
                regions.add(region)
            }
            for (index in 0 until territoryAliases.getSize()) {
                val alias = territoryAliases[index]
                val from = alias.getKey()!!
                val to = alias["replacement"].getString()
                val replacement = ids[to]
                if (replacement != null && from !in ids) aliases[from] = replacement else {
                    val region = ids[from] ?: Region(from, if (numericId(from)) from.toInt() else -1,
                        RegionType.DEPRECATED).also {
                        ids[from] = it
                        if (numericId(from)) numeric[it.code] = it
                        regions.add(it)
                    }
                    region.type = RegionType.DEPRECATED
                    region.preferredValues = to.split(' ').mapNotNullTo(ArrayList()) { ids[it] }
                }
            }
            for (index in 0 until mappings.getSize()) {
                val mapping = mappings[index]
                if (mapping.getType() == UResourceBundle.ARRAY) {
                    val values = mapping.getStringArray()
                    val number = values[1].toInt()
                    ids[values[0]]?.let {
                        it.code = number
                        numeric[number] = it
                        aliases[values[2]] = it
                    }
                }
            }
            ids["001"]?.type = RegionType.WORLD
            ids["ZZ"]?.type = RegionType.UNKNOWN
            for (continent in continents) ids[continent]?.type = RegionType.CONTINENT
            val groupingKeys = groupings.getKeys()
            while (groupingKeys.hasMoreElements()) ids[groupingKeys.nextElement()]?.type = RegionType.GROUPING
            ids["QO"]?.type = RegionType.SUBCONTINENT
            for (index in 0 until containment.getSize()) {
                val mapping = containment[index]
                val parent = mapping.getKey()
                if (parent == "containedGroupings" || parent == "deprecated" || parent == "grouping") continue
                val parentRegion = ids[parent]
                for (childIndex in 0 until mapping.getSize()) {
                    val childRegion = ids[mapping.getString(childIndex)]
                    if (parentRegion != null && childRegion != null) {
                        parentRegion.containedRegions.add(childRegion)
                        if (parentRegion.type != RegionType.GROUPING) childRegion.containingRegion = parentRegion
                    }
                }
            }
            for (index in 0 until groupings.getSize()) {
                val mapping = groupings[index]
                val parent = ids[mapping.getKey()]
                for (childIndex in 0 until mapping.getSize()) {
                    val child = ids[mapping.getString(childIndex)]
                    if (parent != null && child != null) parent.containedRegions.add(child)
                }
            }
            val available = RegionType.entries.map { HashSet<Region>() }
            for (region in regions) {
                val values = available[region.type.ordinal]
                if (values.none { it.compareTo(region) == 0 }) values.add(region)
            }
            return Data(ids, numeric, aliases, available)
        }
    }
}
