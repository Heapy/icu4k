// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.OlsonTimeZone
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.util.SimpleTimeZone
import io.github.heapy.icu4k.util.TimeZone

/** Public wrappers around the cached ICU resource prototypes and host runtime. */
internal object TimeZoneFactory {
    private val lock = createAvailabilityLock()
    private val systemZones = mutableMapOf<String?, TimeZone?>()
    private val customZones = mutableMapOf<String, TimeZone>()
    val runtime = TimeZoneRuntimeCore(object : TimeZoneRuntimeCore.Owner<TimeZone, TimeZoneHostToken> {
        override fun createJavaTimeZone(id: String?): TimeZone? = hostCreateJavaTimeZone(id)
        override fun createDefaultJavaTimeZone(): TimeZone = hostCreateDefaultJavaTimeZone()
        override fun getSystemTimeZone(id: String?): TimeZone? = lock.withLock {
            if (systemZones.containsKey(id)) systemZones[id]
            else ZoneMetaCore.getSystemTimeZoneCore(id)?.let { OlsonTimeZone(it).freeze() }.also { systemZones[id] = it }
        }
        override fun getCustomTimeZone(id: String?): TimeZone? {
            val prototype = ZoneMetaCore.getCustomTimeZoneCore(id) ?: return null
            return lock.withLock { customZones.getOrPut(prototype.getID()) { SimpleTimeZone(prototype).freeze() } }
        }
        override fun unknownZone(): TimeZone = TimeZone.UNKNOWN_ZONE
        override fun reportUnknownID(id: String?) = hostReportUnknownTimeZoneID(id)
        override fun isFrozen(zone: TimeZone): Boolean = zone.isFrozen()
        override fun clone(zone: TimeZone): TimeZone = zone.clone()
        override fun freeze(zone: TimeZone): TimeZone = zone.freeze()
        override fun cloneAsThawed(zone: TimeZone): TimeZone = zone.cloneAsThawed()
        override fun isJavaTimeZone(zone: TimeZone): Boolean = hostIsJavaTimeZone(zone)
        override fun unwrapJavaTimeZone(zone: TimeZone): TimeZoneHostToken? = hostUnwrapJavaTimeZone(zone)
        override fun isOlsonTimeZone(zone: TimeZone): Boolean = zone is OlsonTimeZone
        override fun getID(zone: TimeZone): String? = zone.getID()
        override fun getCanonicalID(id: String?): String? = TimeZone.getCanonicalID(id)
        override fun wrap(zone: TimeZone): TimeZoneHostToken = hostWrapTimeZone(zone)
    }, timeZoneRuntimeHost())
}

internal expect class TimeZoneHostToken
internal expect fun timeZoneRuntimeHost(): TimeZoneRuntimeCore.Host<TimeZoneHostToken>
internal expect fun hostCreateJavaTimeZone(id: String?): TimeZone?
internal expect fun hostCreateDefaultJavaTimeZone(): TimeZone
internal expect fun hostIsJavaTimeZone(zone: TimeZone): Boolean
internal expect fun hostUnwrapJavaTimeZone(zone: TimeZone): TimeZoneHostToken?
internal expect fun hostWrapTimeZone(zone: TimeZone): TimeZoneHostToken
internal expect fun hostReportUnknownTimeZoneID(id: String?)
