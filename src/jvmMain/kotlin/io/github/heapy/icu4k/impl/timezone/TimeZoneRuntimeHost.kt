// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.JavaTimeZone
import io.github.heapy.icu4k.impl.TimeZoneAdapter
import io.github.heapy.icu4k.util.TimeZone

internal actual class TimeZoneHostToken(val zone: java.util.TimeZone)
internal actual fun timeZoneRuntimeHost(): TimeZoneRuntimeCore.Host<TimeZoneHostToken> = object : TimeZoneRuntimeCore.Host<TimeZoneHostToken> {
    override fun <T> withDefaultLock(block: () -> T): T = synchronized(java.util.TimeZone::class.java, block)
    override fun getDefault(): TimeZoneHostToken = TimeZoneHostToken(java.util.TimeZone.getDefault())
    override fun getID(zone: TimeZoneHostToken): String? = zone.zone.id
    override fun getTimeZone(id: String?): TimeZoneHostToken = TimeZoneHostToken(java.util.TimeZone.getTimeZone(id))
    override fun setDefault(zone: TimeZoneHostToken) = java.util.TimeZone.setDefault(zone.zone)
}
internal actual fun hostCreateJavaTimeZone(id: String?): TimeZone? = JavaTimeZone.createTimeZone(id)
internal actual fun hostCreateDefaultJavaTimeZone(): TimeZone = JavaTimeZone()
internal actual fun hostIsJavaTimeZone(zone: TimeZone): Boolean = zone is JavaTimeZone
internal actual fun hostUnwrapJavaTimeZone(zone: TimeZone): TimeZoneHostToken? = (zone as? JavaTimeZone)?.unwrap()?.let { TimeZoneHostToken(it) }
internal actual fun hostWrapTimeZone(zone: TimeZone): TimeZoneHostToken = TimeZoneHostToken(TimeZoneAdapter.wrap(zone))
private val logger = java.util.logging.Logger.getLogger(TimeZone::class.java.name)
internal actual fun hostReportUnknownTimeZoneID(id: String?) {
    logger.fine("\"$id\" is a bogus id so timezone is falling back to Etc/Unknown(GMT).")
}
