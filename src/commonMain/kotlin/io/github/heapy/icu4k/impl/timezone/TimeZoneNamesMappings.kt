// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2008-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.Grego
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.util.*

/** TimeZoneNamesImpl's locale-independent metazone mapping caches. */
internal object TimeZoneNamesMappings {
    private data class Entry(val id: String, val from: Long, val to: Long)
    private val lock = createAvailabilityLock()
    private var allIDs: Set<String>? = null
    private val toMeta = mutableMapOf<String, List<Entry>>()
    private val toZone = mutableMapOf<String, Map<String, String>>()
    private fun bundle() = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "metaZones")
    fun getAvailableMetaZoneIDs(): Set<String> = lock.withLock {
        if (allIDs == null) {
            val keys = bundle().get("mapTimezones").keySet()
            allIDs = immutableAllMetaZoneIDSet(keys)
        }
        allIDs!!
    }
    fun getAvailableMetaZoneIDs(id: String?): Set<String> {
        if (id.isNullOrEmpty()) return emptyIDs
        val maps = mappings(id)
        if (maps.isEmpty()) return emptyIDs
        return immutableMetaZoneIDSet(maps.map { it.id })
    }
    fun getMetaZoneID(id: String?, date: Long): String? {
        if (id.isNullOrEmpty()) return null
        for (entry in mappings(id)) if (date >= entry.from && date < entry.to) return entry.id
        return null
    }
    fun getReferenceZoneID(id: String?, region: String?): String? {
        if (id.isNullOrEmpty()) return null
        val map = lock.withLock {
            toZone.getOrPut(id) {
                val mappings = bundle().get("mapTimezones")
                try {
                    val regions = mappings.get(id)
                    regions.keySet().associate { internTimeZoneNameID(it) to internTimeZoneNameID(regions.getString(it)) }
                } catch (_: MissingResourceException) { emptyMap() }
            }
        }
        if (map.isEmpty()) return null
        return map[region] ?: map["001"]
    }
    private fun mappings(id: String): List<Entry> = lock.withLock {
        toMeta.getOrPut(id) {
            val info = bundle().get("metazoneInfo")
            val key = id.replace('/', ':')
            try {
                val zone = info.get(key)
                val result = ArrayList<Entry>(zone.getSize())
                for (i in 0 until zone.getSize()) {
                    val mapping = zone.get(i)
                    val mzID = mapping.getString(0)
                    var from = "1970-01-01 00:00"
                    var to = "9999-12-31 23:59"
                    if (mapping.getSize() == 3) { from = mapping.getString(1); to = mapping.getString(2) }
                    result.add(Entry(mzID, parseDate(from), parseDate(to)))
                }
                result
            } catch (_: MissingResourceException) { emptyList() }
        }
    }
    internal fun parseDate(text: String?): Long {
        fun number(first: Int, last: Int, error: String): Int {
            var result = 0
            for (i in first..last) {
                val value = text ?: throw NullPointerException()
                if (i !in value.indices) throw IndexOutOfBoundsException()
                val digit = value[i].code - '0'.code
                if (digit !in 0..9) throw IllegalArgumentException(error)
                result = 10 * result + digit
            }
            return result
        }
        val year = number(0,3,"Bad year")
        val month = number(5,6,"Bad month")
        val day = number(8,9,"Bad day")
        val hour = number(11,12,"Bad hour")
        val minute = number(14,15,"Bad minute")
        return Grego.fieldsToDay(year,month - 1,day) * Grego.MILLIS_PER_DAY + hour.toLong() * Grego.MILLIS_PER_HOUR + minute.toLong() * Grego.MILLIS_PER_MINUTE
    }
    private val emptyIDs: Set<String> = object : AbstractMutableSet<String>() {
        override val size: Int get() = 0
        override fun add(element: String): Nothing = throw UnsupportedOperationException()
        override fun clear() {}
        override fun iterator(): MutableIterator<String> = object : MutableIterator<String> {
            override fun hasNext(): Boolean = false
            override fun next(): String = throw NoSuchElementException()
            override fun remove(): Nothing = throw IllegalStateException()
        }
    }
}

internal expect fun immutableMetaZoneIDSet(values: Collection<String>): Set<String>
internal expect fun immutableAllMetaZoneIDSet(values: Set<String>): Set<String>

/** Portable unmodifiable HashSet view: nullable membership differs from the all-ID sorted set. */
internal class ReadOnlyMetaZoneIDSet(private val values: Set<String>, private val rejectNull: Boolean = false) : AbstractMutableSet<String?>() {
    override val size: Int get() = values.size
    override fun contains(element: String?): Boolean {
        if (rejectNull && element == null) throw NullPointerException()
        return element != null && element in values
    }
    override fun iterator(): MutableIterator<String?> = object : MutableIterator<String?> {
        private val source = (if (rejectNull) values.sorted() else values).iterator()
        override fun hasNext(): Boolean = source.hasNext()
        override fun next(): String = source.next()
        override fun remove(): Nothing = throw UnsupportedOperationException()
    }
    override fun add(element: String?): Nothing = throw UnsupportedOperationException()
    override fun remove(element: String?): Nothing = throw UnsupportedOperationException()
    override fun addAll(elements: Collection<String?>): Nothing = throw UnsupportedOperationException()
    override fun removeAll(elements: Collection<String?>): Nothing = throw UnsupportedOperationException()
    override fun retainAll(elements: Collection<String?>): Nothing = throw UnsupportedOperationException()
    override fun clear(): Nothing = throw UnsupportedOperationException()
}
