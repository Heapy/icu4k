// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.util.TimeZone

/** Process-local host equivalent for platforms without java.util.TimeZone. */
internal class PortableTimeZoneHostToken(val id: String?, val wrapped: TimeZone? = null)
internal class PortableTimeZoneRuntimeHost(private val systemID: () -> String) : TimeZoneRuntimeCore.Host<PortableTimeZoneHostToken> {
    private val lock = createAvailabilityLock()
    private var default: PortableTimeZoneHostToken? = null
    private fun copy(zone: PortableTimeZoneHostToken) = PortableTimeZoneHostToken(zone.id, zone.wrapped?.clone())
    override fun <T> withDefaultLock(block: () -> T): T = lock.withLock(block)
    override fun getDefault(): PortableTimeZoneHostToken = lock.withLock { copy(default ?: PortableTimeZoneHostToken(systemID())) }
    override fun getID(zone: PortableTimeZoneHostToken): String? = zone.wrapped?.getID() ?: zone.id
    override fun getTimeZone(id: String?): PortableTimeZoneHostToken {
        val key = id!!
        val normalized = if (ZoneMetaCore.getSystemTimeZoneCore(key) != null) key else ZoneMetaCore.getCustomTimeZoneCore(key)?.getID() ?: "GMT"
        return PortableTimeZoneHostToken(normalized)
    }
    override fun setDefault(zone: PortableTimeZoneHostToken) = lock.withLock { default = copy(zone) }
}
internal fun unsupportedJdkTimeZone(): Nothing = throw UnsupportedOperationException("JDK time zones require the JVM target.")
