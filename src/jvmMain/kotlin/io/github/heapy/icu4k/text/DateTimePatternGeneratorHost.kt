// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.util.*
internal actual fun dateTimePatternGeneratorAssertionsEnabled(): Boolean = DateTimePatternGenerator::class.java.desiredAssertionStatus()
fun DateTimePatternGenerator.Companion.getInstance(locale: java.util.Locale?): DateTimePatternGenerator = getInstance(ULocale.forLocale(locale))

internal actual fun dateTimePatternFirstChar(value: String): Char = value[0]
