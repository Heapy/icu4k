// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.JavaTimeConverters
import io.github.heapy.icu4k.util.*
import java.time.temporal.Temporal

internal actual fun dateIntervalFormatRead(input: PluralObjectInputStream) = input.defaultReadObject()
internal actual fun dateIntervalCharAt(text: String, index: Int): Char = text[index]
internal actual fun dateIntervalSubSequence(text: String, start: Int, end: Int): CharSequence = text.subSequence(start, end)
fun DateIntervalFormat.Companion.getInstance(skeleton: String?, locale: java.util.Locale?): DateIntervalFormat = getInstance(skeleton, ULocale.forLocale(locale))
fun DateIntervalFormat.Companion.getInstance(skeleton: String?, locale: java.util.Locale?, info: DateIntervalInfo?): DateIntervalFormat = getInstance(skeleton, ULocale.forLocale(locale), info)
fun DateIntervalFormat.format(from: Temporal?, to: Temporal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder =
    format(JavaTimeConverters.temporalToCalendar(from!!), JavaTimeConverters.temporalToCalendar(to!!), output, position)
fun DateIntervalFormat.formatToValue(from: Temporal?, to: Temporal?): DateIntervalFormat.FormattedDateInterval {
    val first = JavaTimeConverters.temporalToCalendar(from!!)
    val second = JavaTimeConverters.temporalToCalendar(to!!)
    return formatToValue(first, second)
}
