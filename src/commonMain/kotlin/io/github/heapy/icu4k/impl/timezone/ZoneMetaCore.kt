// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.impl.locale.LocaleReadOnlySet
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.util.UResourceBundle

/** ZoneMeta's ID/resource algorithms; zone construction belongs to the TimeZone owner. */
internal object ZoneMetaCore {
    enum class SystemTimeZoneType { ANY, CANONICAL, CANONICAL_LOCATION }
    private val lock = createAvailabilityLock()
    private var zoneIDs: Array<String>? = null
    private val sets = mutableMapOf<SystemTimeZoneType, Set<String>>()
    private val canonicalIDs = mutableMapOf<String?, String>()
    private val regions = mutableMapOf<String?, String>()
    private val singleCountries = mutableMapOf<String?, Boolean>()

    private fun bundle(name: String): UResourceBundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, name)
    private fun getZoneIDs(): Array<String> = lock.withLock {
        if (zoneIDs == null) {
            try { zoneIDs = bundle("zoneinfo64").getStringArray("Names") }
            catch (_: MissingResourceException) { }
        }
        if (zoneIDs == null) zoneIDs = emptyArray()
        zoneIDs!!
    }
    private fun getZoneID(index: Int): String? {
        if (index >= 0) {
            val ids = getZoneIDs()
            if (index < ids.size) return ids[index]
        }
        return null
    }
    private fun getZoneIndex(id: String?): Int {
        val all = getZoneIDs()
        if (all.isNotEmpty()) {
            var start = 0
            var limit = all.size
            var lastMid = Int.MAX_VALUE
            while (true) {
                val mid = (start + limit) / 2
                if (lastMid == mid) break
                lastMid = mid
                val comparison = id!!.compareTo(all[mid])
                if (comparison == 0) return mid
                if (comparison < 0) limit = mid else start = mid
            }
        }
        return -1
    }
    private fun systemIDs(type: SystemTimeZoneType?): Set<String> = lock.withLock {
        sets[type]?.let { return@withLock it }
        val ids = linkedSetOf<String>()
        // Force the switch's null failure before loading any zone IDs.
        val selected = type!!
        for (id in getZoneIDs()) {
            if (id == "Etc/Unknown") continue
            when (selected) {
                SystemTimeZoneType.ANY -> ids.add(id)
                SystemTimeZoneType.CANONICAL -> if (id == getCanonicalCLDRID(id)) ids.add(id)
                SystemTimeZoneType.CANONICAL_LOCATION -> if (id == getCanonicalCLDRID(id)) {
                    val region = getRegion(id)
                    if (region != null && region != "001") ids.add(id)
                }
            }
        }
        LocaleReadOnlySet({ ids }, null).also { sets[selected] = it }
    }
    fun getAvailableIDs(type: SystemTimeZoneType?, region: String? = null): Set<String> =
        filterIDs(type, region, null, null)

    /** The owner supplies the system-zone raw offset, returning null when construction fails. */
    fun getAvailableIDs(type: SystemTimeZoneType?, region: String?, rawOffset: Int?, rawOffsetForID: (String) -> Int?): Set<String> =
        filterIDs(type, region, rawOffset, rawOffsetForID)

    private fun filterIDs(type: SystemTimeZoneType?, region: String?, rawOffset: Int?, rawOffsetForID: ((String) -> Int?)?): Set<String> {
        val base = systemIDs(type)
        if (region == null && rawOffset == null) return base
        val normalized = region?.uppercase()
        val result = linkedSetOf<String>()
        for (id in base) {
            if (normalized != null && normalized != getRegion(id)) continue
            if (rawOffset != null && rawOffsetForID!!(id) != rawOffset) continue
            result.add(id)
        }
        return if (result.isEmpty()) emptyIDs else LocaleReadOnlySet({ result }, null)
    }
    // Collections.emptySet permits no-op removals/clear, unlike unmodifiableSet.
    private val emptyIDs: Set<String> = object : AbstractMutableSet<String>() {
        override val size: Int get() = 0
        override fun iterator(): MutableIterator<String> = object : MutableIterator<String> {
            override fun hasNext(): Boolean = false
            override fun next(): String = throw NoSuchElementException()
            override fun remove(): Nothing = throw IllegalStateException()
        }
        override fun add(element: String): Nothing = throw UnsupportedOperationException()
        override fun clear() {}
    }
    fun countEquivalentIDs(id: String?): Int = lock.withLock {
        val resource = openOlsonResource(null, id)
        if (resource != null) {
            try { return@withLock resource.get("links").getIntVector().size }
            catch (_: MissingResourceException) { }
        }
        0
    }
    fun getEquivalentID(id: String?, index: Int): String = lock.withLock {
        var result = ""
        if (index >= 0) {
            val resource = openOlsonResource(null, id)
            if (resource != null) {
                var zoneIndex = -1
                try {
                    val links = resource.get("links").getIntVector()
                    if (index < links.size) zoneIndex = links[index]
                } catch (_: MissingResourceException) { }
                if (zoneIndex >= 0) getZoneID(zoneIndex)?.let { result = it }
            }
        }
        result
    }
    fun openOlsonResource(top: UResourceBundle?, id: String?): UResourceBundle? {
        val index = getZoneIndex(id)
        if (index >= 0) {
            try {
                val zones = (top ?: bundle("zoneinfo64")).get("Zones")
                var zone = zones.get(index)
                if (zone.getType() == UResourceBundle.INT) zone = zones.get(zone.getInt())
                return zone
            } catch (_: MissingResourceException) { }
        }
        return null
    }
    fun getCanonicalCLDRID(originalID: String?): String? {
        var id = originalID
        var canonical = lock.withLock { canonicalIDs[id] }
        if (canonical == null) {
            canonical = findCLDRCanonicalID(id)
            if (canonical == null) {
                try {
                    val index = getZoneIndex(id)
                    if (index >= 0) {
                        val zone = bundle("zoneinfo64").get("Zones").get(index)
                        if (zone.getType() == UResourceBundle.INT) {
                            id = getZoneID(zone.getInt())
                            canonical = findCLDRCanonicalID(id)
                        }
                        if (canonical == null) canonical = id
                    }
                } catch (_: MissingResourceException) { }
            }
            if (canonical != null) lock.withLock { canonicalIDs[id] = canonical }
        }
        return canonical
    }
    private fun findCLDRCanonicalID(id: String?): String? {
        val key = id!!.replace('/', ':')
        try {
            val data = bundle("keyTypeData")
            val types = data.get("typeMap").get("timezone")
            try { types.get(key); return id } catch (_: MissingResourceException) { }
            return data.get("typeAlias").get("timezone").getString(key)
        } catch (_: MissingResourceException) { }
        return null
    }
    fun getIanaID(id: String?): String? {
        val canonical = getCanonicalCLDRID(id) ?: return null
        val types = bundle("keyTypeData").get("ianaMap").get("timezone")
        return try { types.getString(canonical.replace('/', ':')) }
        catch (_: MissingResourceException) { canonical }
    }
    fun getRegion(id: String?): String? {
        var region = lock.withLock { regions[id] }
        if (region == null) {
            val index = getZoneIndex(id)
            if (index >= 0) {
                try {
                    val table = bundle("zoneinfo64").get("Regions")
                    if (index < table.getSize()) region = table.getString(index)
                } catch (_: MissingResourceException) { }
                if (region != null) lock.withLock { regions[id] = region }
            }
        }
        return region
    }
    fun getCanonicalCountry(id: String?): String? = getRegion(id).let { if (it == "001") null else it }
    fun getCanonicalCountry(id: String?, isPrimary: Output<Boolean>?): String? {
        isPrimary!!.value = false
        val country = getRegion(id)
        if (country == "001") return null
        var single = lock.withLock { singleCountries[id] }
        if (single == null) {
            val ids = getAvailableIDs(SystemTimeZoneType.CANONICAL_LOCATION, country)
            single = ids.size <= 1
            lock.withLock { singleCountries[id] = single }
        }
        if (single) isPrimary.value = true
        else {
            try {
                val primaryZones = bundle("metaZones").get("primaryZones")
                val primary = primaryZones.getString(country!!)
                if (id == primary) isPrimary.value = true
                else {
                    val canonical = getCanonicalCLDRID(id)
                    if (canonical != null && canonical == primary) isPrimary.value = true
                }
            } catch (_: MissingResourceException) { }
        }
        return country
    }
    fun getShortID(id: String?): String? {
        val canonical = getCanonicalCLDRID(id) ?: return null
        val key = canonical.replace('/', ':')
        return try { bundle("keyTypeData").get("typeMap").get("timezone").getString(key) }
        catch (_: MissingResourceException) { null }
    }
    fun getCustomID(id: String?): String? {
        val fields = IntArray(4)
        return if (parseCustomID(id, fields)) formatCustomID(fields[1], fields[2], fields[3], fields[0] < 0) else null
    }
    fun parseCustomID(id: String?, fields: IntArray?): Boolean {
        if (id != null && id.length > 3 && id.substring(0, 3).equals("GMT", ignoreCase = true)) {
            var sign = 1
            var minute = 0
            var second = 0
            val pos = intArrayOf(3)
            if (id[pos[0]] == '-') sign = -1 else if (id[pos[0]] != '+') return false
            pos[0]++
            var start = pos[0]
            var hour = Utility.parseNumber(id, pos, 10)
            if (pos[0] == id.length) {
                when (pos[0] - start) {
                    1, 2 -> {}
                    3, 4 -> { minute = hour % 100; hour /= 100 }
                    5, 6 -> { second = hour % 100; minute = (hour / 100) % 100; hour /= 10000 }
                    else -> return false
                }
            } else {
                if (pos[0] - start < 1 || pos[0] - start > 2 || id[pos[0]] != ':') return false
                pos[0]++
                if (id.length == pos[0]) return false
                start = pos[0]
                minute = Utility.parseNumber(id, pos, 10)
                if (pos[0] - start != 2) return false
                if (id.length > pos[0]) {
                    if (id[pos[0]] != ':') return false
                    pos[0]++
                    start = pos[0]
                    second = Utility.parseNumber(id, pos, 10)
                    if (pos[0] - start != 2 || id.length > pos[0]) return false
                }
            }
            if (hour <= 23 && minute <= 59 && second <= 59) {
                if (fields != null) {
                    if (fields.size >= 1) fields[0] = sign
                    if (fields.size >= 2) fields[1] = hour
                    if (fields.size >= 3) fields[2] = minute
                    if (fields.size >= 4) fields[3] = second
                }
                return true
            }
        }
        return false
    }
    fun formatCustomID(hour: Int, minute: Int, second: Int, negative: Boolean): String = buildString {
        append("GMT")
        if (hour != 0 || minute != 0) {
            append(if (negative) '-' else '+')
            if (hour < 10) append('0')
            append(hour); append(':')
            if (minute < 10) append('0')
            append(minute)
            if (second != 0) {
                append(':')
                if (second < 10) append('0')
                append(second)
            }
        }
    }
    /** ID calculation from getCustomTimeZone(int), retaining Int.MIN_VALUE negation overflow. */
    fun customIDForOffset(offset: Int): String {
        val negative = offset < 0
        var remaining = if (negative) -offset else offset
        remaining /= 1000
        val second = remaining % 60
        remaining /= 60
        val minute = remaining % 60
        val hour = remaining / 60
        return formatCustomID(hour, minute, second, negative)
    }
}
