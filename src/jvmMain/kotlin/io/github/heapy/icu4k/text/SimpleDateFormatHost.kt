// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import java.util.Locale

fun SimpleDateFormat(pattern: String?, locale: Locale?): SimpleDateFormat = SimpleDateFormat(pattern, ULocale.forLocale(locale))
internal actual fun simpleDateFormatAssertionsEnabled(): Boolean = SimpleDateFormat::class.java.desiredAssertionStatus()
internal actual fun simpleDateFormatJavaCalendar(value: Any?): Boolean = value is java.util.Calendar
internal actual fun simpleDateFormatSerialFields(): Array<RbnfObjectStreamField> = arrayOf(
    java.io.ObjectStreamField("serialVersionOnStream", Int::class.javaPrimitiveType),
    java.io.ObjectStreamField("pattern", String::class.java),
    java.io.ObjectStreamField("override", String::class.java),
    java.io.ObjectStreamField("numberFormatters", java.util.HashMap::class.java),
    java.io.ObjectStreamField("overrideMap", java.util.HashMap::class.java),
    java.io.ObjectStreamField("formatData", DateFormatSymbols::class.java),
    java.io.ObjectStreamField("defaultCenturyStart", java.util.Date::class.java),
    java.io.ObjectStreamField("tzFormat", TimeZoneFormat::class.java),
)
internal actual fun simpleDateFormatWrite(output: PluralObjectOutputStream, owner: SimpleDateFormat) {
    val state = owner.state
    val fields = output.putFields()
    fields.put("serialVersionOnStream", state.serialVersionOnStream)
    fields.put("pattern", state.pattern); fields.put("override", state.numberingOverride)
    fields.put("numberFormatters", state.numberFormatters)
    fields.put("overrideMap", state.overrideMap)
    fields.put("formatData", state.symbols); fields.put("defaultCenturyStart", state.defaultCenturyStart)
    fields.put("tzFormat", state.zoneFormat)
    output.writeFields()
    output.writeInt(owner.getContext(DisplayContext.Type.CAPITALIZATION).value())
}
internal actual fun simpleDateFormatRead(input: PluralObjectInputStream, owner: SimpleDateFormat) {
    val fields = input.readFields()
    val state = owner.state
    state.serialVersionOnStream = fields.get("serialVersionOnStream", 0)
    state.pattern = fields.get("pattern", null) as String?
    state.numberingOverride = fields.get("override", null) as String?
    state.numberFormatters = fields.get("numberFormatters", null) as MutableMap<String, NumberFormat?>?
    state.overrideMap = fields.get("overrideMap", null) as MutableMap<Char, String?>?
    state.symbols = fields.get("formatData", null) as DateFormatSymbols?
    state.defaultCenturyStart = fields.get("defaultCenturyStart", null) as java.util.Date?
    state.zoneFormat = fields.get("tzFormat", null) as TimeZoneFormat?
    val capitalization = if (state.serialVersionOnStream > 1) input.readInt() else -1
    owner.restoreAfterRead(capitalization)
}
