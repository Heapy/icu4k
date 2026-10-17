// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

internal actual fun calendarClassName(value: Calendar): String = value.javaClass.name
