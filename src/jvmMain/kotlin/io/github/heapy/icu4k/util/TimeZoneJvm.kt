// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

@Deprecated("This API is ICU internal only.")
fun TimeZone.Companion.forLocaleOrDefault(locale: java.util.Locale?): TimeZone = forULocaleOrDefault(ULocale.forLocale(locale))
