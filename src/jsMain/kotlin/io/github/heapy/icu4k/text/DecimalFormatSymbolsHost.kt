package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ICUSerializable
import io.github.heapy.icu4k.util.ULocale

actual abstract class DecimalSymbolsCloneBase actual constructor() : ICUSerializable {
    protected actual abstract fun clone(): Any
    protected actual fun shallowSymbolsClone(): Any? = js("Object.create(Object.getPrototypeOf(this), Object.getOwnPropertyDescriptors(this))")
}
internal actual class DecimalSymbolsRequestedLocale(val locale: ULocale)
internal actual fun decimalSymbolsRequestedLocale(locale: ULocale): DecimalSymbolsRequestedLocale = DecimalSymbolsRequestedLocale(locale)
internal actual fun decimalSymbolsDefaultRequestedLocale(): DecimalSymbolsRequestedLocale = DecimalSymbolsRequestedLocale(ULocale.getDefault())
internal actual fun decimalSymbolsULocale(locale: DecimalSymbolsRequestedLocale): ULocale = locale.locale
internal actual fun decimalSymbolsReadFields(stream: PluralObjectInputStream): Unit = throw UnsupportedOperationException("Java object serialization is unavailable")
