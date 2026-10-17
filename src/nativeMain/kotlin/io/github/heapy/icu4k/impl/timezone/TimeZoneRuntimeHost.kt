// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.util.TimeZone

internal actual typealias TimeZoneHostToken = PortableTimeZoneHostToken
internal actual fun timeZoneRuntimeHost(): TimeZoneRuntimeCore.Host<TimeZoneHostToken> = PortableTimeZoneRuntimeHost { nativeSystemTimeZoneID() }
internal actual fun hostCreateJavaTimeZone(id: String?): TimeZone? = unsupportedJdkTimeZone()
internal actual fun hostCreateDefaultJavaTimeZone(): TimeZone = unsupportedJdkTimeZone()
internal actual fun hostIsJavaTimeZone(zone: TimeZone): Boolean = false
internal actual fun hostUnwrapJavaTimeZone(zone: TimeZone): TimeZoneHostToken? = null
internal actual fun hostWrapTimeZone(zone: TimeZone): TimeZoneHostToken = TimeZoneHostToken(zone.getID(), zone)
internal actual fun hostReportUnknownTimeZoneID(id: String?) { }
internal expect fun nativeSystemTimeZoneID(): String
