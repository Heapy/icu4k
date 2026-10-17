// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.util.TimeZone

internal actual typealias TimeZoneHostToken = PortableTimeZoneHostToken
internal actual fun timeZoneRuntimeHost(): TimeZoneRuntimeCore.Host<TimeZoneHostToken> = PortableTimeZoneRuntimeHost {
    val id: dynamic = js("Intl.DateTimeFormat().resolvedOptions().timeZone")
    check(id != null && (id as String).isNotEmpty()) { "The JavaScript host did not expose its system timezone ID." }
    id as String
}
internal actual fun hostCreateJavaTimeZone(id: String?): TimeZone? = unsupportedJdkTimeZone()
internal actual fun hostCreateDefaultJavaTimeZone(): TimeZone = unsupportedJdkTimeZone()
internal actual fun hostIsJavaTimeZone(zone: TimeZone): Boolean = false
internal actual fun hostUnwrapJavaTimeZone(zone: TimeZone): TimeZoneHostToken? = null
internal actual fun hostWrapTimeZone(zone: TimeZone): TimeZoneHostToken = TimeZoneHostToken(zone.getID(), zone)
internal actual fun hostReportUnknownTimeZoneID(id: String?) { }
