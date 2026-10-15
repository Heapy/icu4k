package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.getAvailableLocales
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.forLocale
import io.github.heapy.icu4k.util.toLocale
import java.util.Locale

actual abstract class DecimalSymbolsCloneBase actual constructor() : Cloneable, java.io.Serializable {
    protected actual abstract override fun clone(): Any
    protected actual fun shallowSymbolsClone(): Any? = super.clone()
}
internal actual typealias DecimalSymbolsRequestedLocale = Locale
internal actual fun decimalSymbolsRequestedLocale(locale: ULocale): Locale = locale.toLocale()
internal actual fun decimalSymbolsDefaultRequestedLocale(): Locale = Locale.getDefault()
internal actual fun decimalSymbolsULocale(locale: Locale): ULocale = ULocale.forLocale(locale)!!
internal actual fun decimalSymbolsReadFields(stream: PluralObjectInputStream) = stream.defaultReadObject()

fun DecimalFormatSymbols(locale: Locale?): DecimalFormatSymbols = DecimalFormatSymbols(ULocale.forLocale(locale))
fun DecimalFormatSymbols.Companion.getInstance(locale: Locale?): DecimalFormatSymbols = getInstance(ULocale.forLocale(locale))
fun DecimalFormatSymbols.Companion.forNumberingSystem(locale: Locale?, ns: NumberingSystem?): DecimalFormatSymbols = forNumberingSystem(ULocale.forLocale(locale), ns)
fun DecimalFormatSymbols.Companion.getAvailableLocales(): Array<Locale> = ICUResourceBundle.getAvailableLocales()
fun DecimalFormatSymbols.getLocale(): Locale = getRequestedLocale()
