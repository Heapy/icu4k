// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone

internal actual fun nativeSystemTimeZoneID(): String = NSTimeZone.defaultTimeZone.name
