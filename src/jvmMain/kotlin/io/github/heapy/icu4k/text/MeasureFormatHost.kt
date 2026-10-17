// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*

internal actual typealias MeasureFormatExternalizable = java.io.Externalizable
internal actual typealias MeasureFormatObjectInput = java.io.ObjectInput
internal actual typealias MeasureFormatObjectOutput = java.io.ObjectOutput
internal actual fun invalidMeasureFormatStream(message: String): Nothing = throw java.io.InvalidObjectException(message)
internal actual fun measureFormatOptionalValues(value: Any?): MutableMap<Any?,Any?>? = value as java.util.HashMap<Any?,Any?>?
internal actual fun measureFormatIsMeasureArray(value: Array<*>): Boolean = Measure::class.java.isAssignableFrom(value.javaClass.componentType)
fun MeasureFormat.Companion.getInstance(locale: java.util.Locale?,width: MeasureFormat.FormatWidth?): MeasureFormat = getInstance(ULocale.forLocale(locale),width)
fun MeasureFormat.Companion.getInstance(locale: java.util.Locale?,width: MeasureFormat.FormatWidth?,format: NumberFormat?): MeasureFormat = getInstance(ULocale.forLocale(locale),width,format)
fun MeasureFormat.Companion.getCurrencyFormat(locale: java.util.Locale?): MeasureFormat = getCurrencyFormat(ULocale.forLocale(locale))
fun TimeUnitFormat(locale: java.util.Locale?): TimeUnitFormat = TimeUnitFormat(ULocale.forLocale(locale))
fun TimeUnitFormat(locale: java.util.Locale?,style: Int): TimeUnitFormat = TimeUnitFormat(ULocale.forLocale(locale),style)
fun TimeUnitFormat.setLocale(locale: java.util.Locale?): TimeUnitFormat = setLocale(ULocale.forLocale(locale))
