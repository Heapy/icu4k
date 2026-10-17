// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.impl.timezone.TimeZoneGenericNamesCore
import io.github.heapy.icu4k.impl.timezone.TimeZoneNamePatternCore
internal actual fun timeZoneParseOptions(all: Boolean, tzdb: Boolean): MutableSet<TimeZoneFormat.ParseOption> = mutableSetOf<TimeZoneFormat.ParseOption>().also {
    if (all) it.add(TimeZoneFormat.ParseOption.ALL_STYLES)
    if (tzdb) it.add(TimeZoneFormat.ParseOption.TZ_DATABASE_ABBREVIATIONS)
}
internal actual fun timeZoneGenericPattern(pattern: String?): TimeZoneGenericNamesCore.PatternFormatter {
    val format = TimeZoneNamePatternCore(pattern)
    return TimeZoneGenericNamesCore.PatternFormatter { format.format(it) }
}
internal actual fun timeZoneFormatSerialFields(): Array<RbnfObjectStreamField> = emptyArray()
internal actual fun timeZoneFormatWrite(output: PluralObjectOutputStream, owner: TimeZoneFormat): Unit = throw UnsupportedOperationException("Java serialization is only available on JVM")
internal actual fun timeZoneFormatRead(input: PluralObjectInputStream, owner: TimeZoneFormat): Unit = throw UnsupportedOperationException("Java serialization is only available on JVM")
