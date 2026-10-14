package io.github.heapy.icu4k.impl.locale

import java.util.Locale

fun LikelySubtags.makeMaximizedLsrFrom(locale: Locale): LSR {
    val tag = locale.toLanguageTag()
    if (tag.startsWith("x-") || tag.startsWith("und-x-")) return LSR(tag, "", "", LSR.EXPLICIT_LSR)
    return makeMaximizedLsr(locale.language, locale.script, locale.country, locale.variant, false)
}
