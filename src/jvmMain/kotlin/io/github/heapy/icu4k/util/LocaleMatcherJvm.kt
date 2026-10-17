// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.locale.LSR
import io.github.heapy.icu4k.impl.locale.LikelySubtags
import io.github.heapy.icu4k.impl.locale.makeMaximizedLsrFrom
import java.util.Locale

internal actual fun localeMatcherToHost(locale: ULocale): Any = locale.toLocale()
internal actual fun localeMatcherFromHost(locale: Any): ULocale = ULocale.forLocale(locale as Locale)!!
@Suppress("DEPRECATION")
internal actual fun localeMatcherHostLsr(locale: Any): LSR {
    val value = locale as Locale
    return if (value == Locale("und") || value == Locale("")) LSR("und", "", "", LSR.EXPLICIT_LSR)
        else LikelySubtags.INSTANCE.makeMaximizedLsrFrom(value)
}
internal actual fun localeMatcherFormatInt(value: Int): String = String.format("%d", value)
fun LocaleMatcher.Builder.setSupportedLocales(locales: Collection<Locale?>?): LocaleMatcher.Builder {
    supportedLocales = ArrayList(locales!!.size)
    for (locale in locales) addSupportedULocale(ULocale.forLocale(locale))
    return this
}
fun LocaleMatcher.Builder.addSupportedLocale(locale: Locale?): LocaleMatcher.Builder = addSupportedULocale(ULocale.forLocale(locale))
fun LocaleMatcher.Builder.setDefaultLocale(locale: Locale?): LocaleMatcher.Builder = setDefaultULocale(ULocale.forLocale(locale))
fun LocaleMatcher.Builder.setMaxDistance(desired: Locale?, supported: Locale?): LocaleMatcher.Builder {
    if (desired == null || supported == null) throw IllegalArgumentException("desired/supported locales must not be null")
    return setMaxDistance(ULocale.forLocale(desired), ULocale.forLocale(supported))
}
fun LocaleMatcher.getBestLocale(locale: Locale?): Locale? = bestHost(locale) as Locale?
fun LocaleMatcher.getBestLocale(locales: Iterable<Locale?>?): Locale? = bestHostList(locales) as Locale?
fun LocaleMatcher.getBestLocaleResult(locale: Locale?): LocaleMatcher.Result = bestHostResult(locale)
fun LocaleMatcher.getBestLocaleResult(locales: Iterable<Locale?>?): LocaleMatcher.Result = bestHostListResult(locales)
fun LocaleMatcher.isMatch(desired: Locale?, supported: Locale?): Boolean = isHostMatch(desired, supported)
fun LocaleMatcher.Result.getDesiredLocale(): Locale? = desiredHost() as Locale?
fun LocaleMatcher.Result.getSupportedLocale(): Locale? = supportedHost() as Locale?
fun LocaleMatcher.Result.makeResolvedLocale(): Locale? = makeResolvedULocale()?.toLocale()
