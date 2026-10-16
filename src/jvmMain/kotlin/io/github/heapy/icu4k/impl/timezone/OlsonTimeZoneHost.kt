// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone
internal actual fun olsonTimeZoneAssertionsEnabled(): Boolean = OlsonTimeZoneCore::class.java.desiredAssertionStatus()
