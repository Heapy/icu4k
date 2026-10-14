package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale

internal expect fun supplementalRegionCase(value: String, upper: Boolean): String
internal fun portableSupplementalRegionCase(value: String, upper: Boolean): String {
    val language = ULocale.getDefault().getLanguage()
    val adjusted = if (language == "tr" || language == "az") {
        if (upper) value.replace('i', '\u0130') else value.replace('I', '\u0131')
    } else value
    return if (upper) adjusted.uppercase() else adjusted.lowercase()
}
