// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
import kotlin.reflect.KClass
internal actual fun dateSymbolsClassName(type: KClass<*>): String = type.java.name

private val dateSymbolArrayFields = arrayOf(
    "eras",
    "eraNames",
    "narrowEras",
    "months",
    "shortMonths",
    "narrowMonths",
    "standaloneMonths",
    "standaloneShortMonths",
    "standaloneNarrowMonths",
    "weekdays",
    "shortWeekdays",
    "shorterWeekdays",
    "narrowWeekdays",
    "standaloneWeekdays",
    "standaloneShortWeekdays",
    "standaloneShorterWeekdays",
    "standaloneNarrowWeekdays",
    "ampms",
    "ampmsWide",
    "ampmsNarrow",
    "shortQuarters",
    "narrowQuarters",
    "quarters",
    "standaloneShortQuarters",
    "standaloneNarrowQuarters",
    "standaloneQuarters",
    "leapMonthPatterns",
    "shortYearNames",
    "shortZodiacNames",
    "abbreviatedDayPeriods",
    "wideDayPeriods",
    "narrowDayPeriods",
    "standaloneAbbreviatedDayPeriods",
    "standaloneWideDayPeriods",
    "standaloneNarrowDayPeriods",
)
internal actual fun dateSymbolsSerializationFields(): Array<RbnfObjectStreamField> =
    (dateSymbolArrayFields.map { java.io.ObjectStreamField(it,Array<String>::class.java) } + listOf(
        java.io.ObjectStreamField("zoneStrings",Array<Array<String>>::class.java),
        java.io.ObjectStreamField("localPatternChars",String::class.java),
        java.io.ObjectStreamField("timeSeparator",String::class.java),
        java.io.ObjectStreamField("capitalization",Map::class.java),
        java.io.ObjectStreamField("requestedLocale",io.github.heapy.icu4k.util.ULocale::class.java),
        java.io.ObjectStreamField("validLocale",io.github.heapy.icu4k.util.ULocale::class.java),
        java.io.ObjectStreamField("actualLocale",io.github.heapy.icu4k.util.ULocale::class.java)
    )).toTypedArray()
internal actual fun dateSymbolsWrite(output: PluralObjectOutputStream,owner: DateFormatSymbols) {
    val state=owner.state; val data=state.data; val fields=output.putFields()
    for(name in dateSymbolArrayFields) fields.put(name,data.fields[name])
    fields.put("zoneStrings",state.storedZoneStrings)
    fields.put("localPatternChars",data.localPatternChars); fields.put("timeSeparator",data.timeSeparator)
    fields.put("capitalization",data.capitalization)
    fields.put("requestedLocale",data.requestedLocale); fields.put("validLocale",data.validLocale); fields.put("actualLocale",data.actualLocale)
    output.writeFields()
}
@Suppress("UNCHECKED_CAST")
internal actual fun dateSymbolsRead(input: PluralObjectInputStream,owner: DateFormatSymbols) {
    val fields=input.readFields(); val state=owner.state; val data=state.data
    for(name in dateSymbolArrayFields) data.fields[name]=fields.get(name,null) as Array<String?>?
    state.storedZoneStrings=fields.get("zoneStrings",null) as Array<Array<String?>?>?
    data.localPatternChars=fields.get("localPatternChars",null) as String?
    data.timeSeparator=fields.get("timeSeparator",null) as String?
    data.capitalization=fields.get("capitalization",null) as MutableMap<DateFormatSymbols.CapitalizationContextUsage,BooleanArray>?
    data.requestedLocale=fields.get("requestedLocale",null) as io.github.heapy.icu4k.util.ULocale?
    data.validLocale=fields.get("validLocale",null) as io.github.heapy.icu4k.util.ULocale?
    data.actualLocale=fields.get("actualLocale",null) as io.github.heapy.icu4k.util.ULocale?
}
