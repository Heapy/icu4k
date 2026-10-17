// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.locale.LSR
import io.github.heapy.icu4k.impl.locale.LikelySubtags

internal actual fun localeMatcherToHost(locale: ULocale): Any = locale
internal actual fun localeMatcherFromHost(locale: Any): ULocale = locale as ULocale
internal actual fun localeMatcherHostLsr(locale: Any): LSR = LikelySubtags.INSTANCE.makeMaximizedLsrFrom(locale as ULocale, false)
internal actual fun localeMatcherFormatInt(value: Int): String = localeMatcherPortableInt(value)

internal actual fun localeMatcherSetFallback(fallback: BooleanArray, value: Boolean) {
    if (fallback.isEmpty()) throw IndexOutOfBoundsException("Index 0 out of bounds for length 0")
    fallback[0] = value
}
