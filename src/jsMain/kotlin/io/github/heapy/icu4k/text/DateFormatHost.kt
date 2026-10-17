// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
import io.github.heapy.icu4k.util.Calendar
internal actual fun dateFormatCalendarInput(input: Any): Calendar? = null
internal actual fun dateFormatClassName(input: Any): String = input::class.simpleName ?: input::class.toString()
internal actual fun invalidDateFormatField(message: String): Nothing=throw IllegalStateException(message)

internal actual fun dateFormatBooleanAttributes(): MutableSet<DateFormat.BooleanAttribute> = DateFormat.BooleanAttribute.entries.toMutableSet()
