// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import io.github.heapy.icu4k.util.VersionInfo

/** TimeZone's static ID policies, distinct from ZoneMeta's lower-level nullable lookups. */
internal object TimeZoneMetadataCore {
    const val UNKNOWN_ZONE_ID = "Etc/Unknown"
    const val GMT_ZONE_ID = "Etc/GMT"

    fun getTZDataVersion(): String = VersionInfo.getTZDataVersion()
    fun getCanonicalID(id: String?, isSystemID: BooleanArray? = null): String? {
        var canonical: String? = null
        var system = false
        if (id != null && id.isNotEmpty()) {
            if (id == UNKNOWN_ZONE_ID) canonical = UNKNOWN_ZONE_ID
            else {
                canonical = ZoneMetaCore.getCanonicalCLDRID(id)
                if (canonical != null) system = true else canonical = ZoneMetaCore.getCustomID(id)
            }
        }
        if (isSystemID != null) {
            // Java performs this write after the lookup even for a zero-length output array.
            if (isSystemID.isEmpty()) throw IndexOutOfBoundsException()
            isSystemID[0] = system
        }
        return canonical
    }
    fun getIanaID(id: String?): String {
        if (id == null || id.isEmpty() || id == UNKNOWN_ZONE_ID) return UNKNOWN_ZONE_ID
        return ZoneMetaCore.getIanaID(id) ?: UNKNOWN_ZONE_ID
    }
    fun getRegion(id: String?): String {
        var region: String? = null
        if (id!! != UNKNOWN_ZONE_ID) region = ZoneMetaCore.getRegion(id)
        return region ?: throw IllegalArgumentException("Unknown system zone id: $id")
    }
    fun getAvailableIDs(type: ZoneMetaCore.SystemTimeZoneType?, region: String?, rawOffset: Int?): Set<String> =
        ZoneMetaCore.getAvailableIDs(type, region, rawOffset)
    fun getAvailableIDs(): Array<String> = getAvailableIDs(ZoneMetaCore.SystemTimeZoneType.ANY, null, null).toTypedArray()
    fun getAvailableIDs(rawOffset: Int): Array<String> = getAvailableIDs(ZoneMetaCore.SystemTimeZoneType.ANY, null, rawOffset).toTypedArray()
    fun getAvailableIDs(country: String?): Array<String> = getAvailableIDs(ZoneMetaCore.SystemTimeZoneType.ANY, country, null).toTypedArray()
    fun countEquivalentIDs(id: String?): Int = ZoneMetaCore.countEquivalentIDs(id)
    fun getEquivalentID(id: String?, index: Int): String = ZoneMetaCore.getEquivalentID(id, index)

    fun getWindowsID(input: String?): String? {
        val isSystem = booleanArrayOf(false)
        val id = getCanonicalID(input, isSystem)
        if (!isSystem[0]) return null
        val mappings = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "windowsZones").get("mapTimezones")
        val iterator = mappings.getIterator()
        while (iterator.hasNext()) {
            val windowsZone = iterator.next()
            if (windowsZone.getType() != UResourceBundle.TABLE) continue
            val regions = windowsZone.getIterator()
            while (regions.hasNext()) {
                val data = regions.next()
                if (data.getType() != UResourceBundle.STRING) continue
                // Java split(" ") drops trailing empties; only a nonempty canonical ID can match.
                for (candidate in data.getString().split(' ')) if (candidate == id) return windowsZone.getKey()
            }
        }
        return null
    }
    fun getIDForWindowsID(windowsID: String?, region: String?): String? {
        var id: String? = null
        val mappings = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "windowsZones").get("mapTimezones")
        try {
            val zones = mappings.get(windowsID!!)
            if (region != null) {
                try {
                    id = zones.getString(region)
                    val end = id.indexOf(' ')
                    if (end > 0) id = id.substring(0, end)
                } catch (_: MissingResourceException) { }
            }
            if (id == null) id = zones.getString("001")
        } catch (_: MissingResourceException) { }
        return id
    }
    /** The public owner supplies default lookup and mutable zone construction. */
    fun <T> forULocaleOrDefault(locale: ULocale?, getDefault: () -> T, getTimeZone: (String) -> T): T {
        val zone = locale!!.getKeywordValue("timezone")
        return if (zone == null) getDefault() else getTimeZone(zone)
    }
}
