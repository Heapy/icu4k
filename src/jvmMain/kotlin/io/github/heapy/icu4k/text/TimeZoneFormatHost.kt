// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.timezone.LocalizedGMTOffsetCore
import io.github.heapy.icu4k.impl.timezone.TimeZoneGenericNamesCore
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import java.util.Locale

fun TimeZoneFormat.Companion.getInstance(locale: Locale?): TimeZoneFormat = getInstance(ULocale.forLocale(locale))
internal actual fun timeZoneParseOptions(all: Boolean, tzdb: Boolean): MutableSet<TimeZoneFormat.ParseOption> =
    java.util.EnumSet.noneOf(TimeZoneFormat.ParseOption::class.java).also {
        if (all) it.add(TimeZoneFormat.ParseOption.ALL_STYLES)
        if (tzdb) it.add(TimeZoneFormat.ParseOption.TZ_DATABASE_ABBREVIATIONS)
    }
internal actual fun timeZoneGenericPattern(pattern: String?): TimeZoneGenericNamesCore.PatternFormatter {
    val format = java.text.MessageFormat(pattern)
    return TimeZoneGenericNamesCore.PatternFormatter { format.format(it) }
}
internal actual fun timeZoneFormatSerialFields(): Array<RbnfObjectStreamField> = arrayOf(
    java.io.ObjectStreamField("_locale", ULocale::class.java),
    java.io.ObjectStreamField("_tznames", TimeZoneNames::class.java),
    java.io.ObjectStreamField("_gmtPattern", String::class.java),
    java.io.ObjectStreamField("_gmtOffsetPatterns", Array<String>::class.java),
    java.io.ObjectStreamField("_gmtOffsetDigits", Array<String>::class.java),
    java.io.ObjectStreamField("_gmtZeroFormat", String::class.java),
    java.io.ObjectStreamField("_parseAllStyles", Boolean::class.javaPrimitiveType)
)
internal actual fun timeZoneFormatWrite(output: PluralObjectOutputStream, owner: TimeZoneFormat) {
    val fields = output.putFields()
    fields.put("_locale", owner.locale); fields.put("_tznames", owner.names)
    fields.put("_gmtPattern", owner.localized!!.getGMTPattern())
    fields.put("_gmtOffsetPatterns", owner.localized!!.serializationPatterns())
    fields.put("_gmtOffsetDigits", owner.localized!!.serializationDigits())
    fields.put("_gmtZeroFormat", owner.localized!!.getGMTZeroFormat())
    fields.put("_parseAllStyles", owner.parseAllStyles)
    output.writeFields()
}
@Suppress("UNCHECKED_CAST")
internal actual fun timeZoneFormatRead(input: PluralObjectInputStream, owner: TimeZoneFormat) {
    val fields = input.readFields()
    fun missing(name: String): Nothing = throw java.io.InvalidObjectException("Missing field: $name")
    owner.locale = fields.get("_locale", null) as ULocale? ?: missing("locale")
    owner.names = fields.get("_tznames", null) as TimeZoneNames? ?: missing("tznames")
    val pattern = fields.get("_gmtPattern", null) as String? ?: missing("gmtPattern")
    val rawPatterns = fields.get("_gmtOffsetPatterns", null) as Array<String>? ?: missing("gmtOffsetPatterns")
    if (rawPatterns.size < 4) throw java.io.InvalidObjectException("Incompatible field: gmtOffsetPatterns")
    val patterns = LocalizedGMTOffsetCore.expandLegacyPatterns(rawPatterns)
    val digits = fields.get("_gmtOffsetDigits", null) as Array<String>? ?: missing("gmtOffsetDigits")
    if (digits.size != 10) throw java.io.InvalidObjectException("Incompatible field: gmtOffsetDigits")
    val zero = fields.get("_gmtZeroFormat", null) as String? ?: missing("gmtZeroFormat")
    owner.parseAllStyles = fields.get("_parseAllStyles", false)
    if (fields.defaulted("_parseAllStyles")) missing("parseAllStyles")
    owner.restoreDerived(pattern, patterns, digits, zero)
}
