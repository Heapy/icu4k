// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.impl.locale.AsciiUtil
import java.util.Locale

private val javaLocaleCache = object : SoftCache<Locale, ULocale, Unit?>() {
    override fun createInstance(key: Locale, data: Unit?): ULocale = fromJavaLocale(key)
}

fun ULocale.Companion.forLocale(locale: Locale?): ULocale? =
    locale?.let { javaLocaleCache.getInstance(it, null) }

@Suppress("DEPRECATION")
fun ULocale.toLocale(): Locale {
    val cached = platformLocale as Locale?
    if (cached != null) return cached
    val result = if (getScript().isNotEmpty() || '@' in getName())
        Locale.forLanguageTag(AsciiUtil.toUpperString(toLanguageTag()))
    else Locale(getLanguage(), getCountry(), getVariant())
    platformLocale = result
    return result
}

private fun fromJavaLocale(locale: Locale): ULocale {
    var language = locale.language
    var variant = locale.variant
    val keywords = sortedMapOf<String, String>()
    for (key in locale.extensionKeys) {
        if (key == 'u') {
            val attributes = locale.unicodeLocaleAttributes.sorted()
            if (attributes.isNotEmpty()) keywords["attribute"] = attributes.joinToString("-")
            for (unicodeKey in locale.unicodeLocaleKeys) {
                val value = locale.getUnicodeLocaleType(unicodeKey) ?: continue
                if (unicodeKey == "va") variant = if (variant.isEmpty()) value else value + "_" + variant
                else keywords[unicodeKey] = value
            }
        } else locale.getExtension(key)?.let { keywords[key.toString()] = it }
    }
    if (language == "no" && locale.country == "NO" && variant == "NY") {
        language = "nn"
        variant = ""
    }
    val id = buildString {
        append(language)
        if (locale.script.isNotEmpty()) append('_').append(locale.script)
        if (locale.country.isNotEmpty()) append('_').append(locale.country)
        if (variant.isNotEmpty()) {
            if (locale.country.isEmpty()) append('_')
            append('_').append(variant)
        }
        if (keywords.isNotEmpty()) {
            append('@')
            append(keywords.entries.joinToString(";") { (key, value) ->
                if (key.length == 1) "$key=$value" else {
                    val legacyKey = ULocale.toLegacyKey(key)
                    val legacyValue = ULocale.toLegacyType(legacyKey!!, value.ifEmpty { "yes" })
                    "$legacyKey=$legacyValue"
                }
            })
        }
    }
    return ULocale(id).also { it.platformLocale = locale }
}
