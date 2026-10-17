// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util
import io.github.heapy.icu4k.text.PluralObjectInputStream
import io.github.heapy.icu4k.text.PluralObjectOutputStream
import io.github.heapy.icu4k.text.RbnfObjectStreamField
import java.io.ObjectStreamField
internal actual fun timeStateSerialFields(kind: String): Array<RbnfObjectStreamField> = when (kind) {
    "Calendar" -> arrayOf(
        ObjectStreamField("time", Long::class.javaPrimitiveType), ObjectStreamField("lenient", Boolean::class.javaPrimitiveType),
        ObjectStreamField("zone", TimeZone::class.java), ObjectStreamField("validLocale", ULocale::class.java), ObjectStreamField("actualLocale", ULocale::class.java),
        *arrayOf("firstDayOfWeek", "minimalDaysInFirstWeek", "repeatedWallTime", "skippedWallTime", "weekendOnset", "weekendOnsetMillis", "weekendCease", "weekendCeaseMillis").map { ObjectStreamField(it, Int::class.javaPrimitiveType) }.toTypedArray(),
    )
    "GregorianCalendar" -> arrayOf(ObjectStreamField("gregorianCutover", Long::class.javaPrimitiveType))
    "ChineseCalendar" -> arrayOf(ObjectStreamField("zoneAstro", TimeZone::class.java))
    "EthiopicCalendar" -> arrayOf(ObjectStreamField("eraType", Int::class.javaPrimitiveType))
    "IslamicCalendar" -> arrayOf(ObjectStreamField("civil", Boolean::class.javaPrimitiveType), ObjectStreamField("cType", IslamicCalendar.CalculationType::class.java))
    "DateFormat" -> arrayOf(
        ObjectStreamField("calendar", Calendar::class.java), ObjectStreamField("numberFormat", io.github.heapy.icu4k.text.NumberFormat::class.java),
        ObjectStreamField("booleanAttributes", java.util.EnumSet::class.java), ObjectStreamField("capitalizationSetting", io.github.heapy.icu4k.text.DisplayContext::class.java),
        ObjectStreamField("serialVersionOnStream", Int::class.javaPrimitiveType),
    )
    "TimeZone" -> arrayOf(ObjectStreamField("ID", String::class.java))
    "RuleBasedTimeZone" -> arrayOf(ObjectStreamField("initialRule", InitialTimeZoneRule::class.java), ObjectStreamField("historicRules", java.util.List::class.java), ObjectStreamField("finalRules", Array<AnnualTimeZoneRule>::class.java))
    "SimpleTimeZone" -> arrayOf(
        *arrayOf("raw", "dst", "startMonth", "startDay", "startDayOfWeek", "startTime", "startTimeMode", "endMonth", "endDay", "endDayOfWeek", "endTime", "endTimeMode", "startYear", "startMode", "endMode").map { ObjectStreamField(it, Int::class.javaPrimitiveType) }.toTypedArray(),
        ObjectStreamField("useDaylight", Boolean::class.javaPrimitiveType), ObjectStreamField("xinfo", STZInfo::class.java),
    )
    "OlsonTimeZone" -> arrayOf(
        *arrayOf("transitionCount", "typeCount", "finalStartYear", "serialVersionOnStream").map { ObjectStreamField(it, Int::class.javaPrimitiveType) }.toTypedArray(),
        ObjectStreamField("transitionTimes64", LongArray::class.java), ObjectStreamField("typeOffsets", IntArray::class.java), ObjectStreamField("typeMapData", ByteArray::class.java),
        ObjectStreamField("finalStartMillis", Double::class.javaPrimitiveType), ObjectStreamField("canonicalID", String::class.java), ObjectStreamField("finalZone", SimpleTimeZone::class.java),
    )
    else -> emptyArray()
}
internal actual fun timeStateWriteFields(output: PluralObjectOutputStream, kind: String, values: Map<String, Any?>) {
    val fields = output.putFields()
    for (field in timeStateSerialFields(kind)) when (field.typeCode) {
        'I' -> fields.put(field.name, values[field.name] as Int)
        'D' -> fields.put(field.name, values[field.name] as Double)
        'J' -> fields.put(field.name, values[field.name] as Long)
        'Z' -> fields.put(field.name, values[field.name] as Boolean)
        else -> fields.put(field.name, values[field.name])
    }
    output.writeFields()
}
internal actual fun timeStateReadFields(input: PluralObjectInputStream, kind: String): Map<String, Any?> {
    val fields = input.readFields()
    return timeStateSerialFields(kind).associate { field -> field.name to when (field.typeCode) {
        'I' -> fields.get(field.name, 0)
        'D' -> fields.get(field.name, 0.0)
        'J' -> fields.get(field.name, 0L)
        'Z' -> fields.get(field.name, false)
        else -> fields.get(field.name, null)
    } }
}
