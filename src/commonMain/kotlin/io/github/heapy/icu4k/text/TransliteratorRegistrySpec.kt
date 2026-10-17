// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.locale.AsciiUtil
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** LocaleUtility's legacy three-component Locale conversion, followed by ULocale.forLocale. */
internal fun transliterationLocaleFromName(name: String): ULocale {
    val first = name.indexOf('_')
    val second = if (first < 0) -1 else name.indexOf('_', first + 1)
    var language = AsciiUtil.toLowerString(if (first < 0) name else name.substring(0, first))
    val country = AsciiUtil.toUpperString(if (first < 0) "" else if (second < 0) name.substring(first + 1) else name.substring(first + 1, second))
    var variant = if (second < 0) "" else name.substring(second + 1)
    language = when (language) { "iw" -> "he"; "ji" -> "yi"; "in" -> "id"; else -> language }
    if (language == "no" && country == "NO" && variant == "NY") { language = "nn"; variant = "" }
    return ULocale(buildString {
        append(language)
        if (country.isNotEmpty()) append('_').append(country)
        if (variant.isNotEmpty()) {
            if (country.isEmpty()) append('_')
            append('_').append(variant)
        }
        if (language == "ja" && country == "JP" && variant == "JP") append("@calendar=japanese")
        if (language == "th" && country == "TH" && variant == "TH") append("@numbers=thai")
    })
}

/** Stateful source/target fallback cursor, including ICU4J's identity-sensitive reset. */
internal class TransliteratorRegistrySpec(private val top: String, bundle: (ULocale) -> ICUResourceBundle = {
    UResourceBundle.getBundleInstance(ICUData.ICU_TRANSLIT_BASE_NAME, it) as ICUResourceBundle
}) {
    private var spec: String? = null
    private var nextSpec: String? = null
    private var scriptName: String? = null
    private var specLocale = false
    private var nextLocale = false
    private var resource: ICUResourceBundle? = null

    init {
        try {
            val script = UScript.getCodeFromName(top)
            UScript.getCode(top)?.let {
                scriptName = UScript.getName(it[0])
                if (scriptName!!.equals(top, true)) scriptName = null
            }
            if (script == UScript.INVALID_CODE) {
                resource = bundle(transliterationLocaleFromName(top))
                val parent = resource!!.getULocale().toString()
                if (top.startsWith(parent) && (top.length == parent.length || top[parent.length] == '_')) specLocale = true
            }
        } catch (_: MissingResourceException) { scriptName = null }
        reset()
    }
    fun getTop(): String = top
    fun get(): String? = spec
    fun isLocale(): Boolean = specLocale
    fun hasFallback(): Boolean = nextSpec != null
    fun getBundle(): ICUResourceBundle? = resource?.takeIf { it.getULocale().toString() == spec }
    fun reset() {
        if (spec !== top) {
            spec = top
            specLocale = resource != null
            setupNext()
        }
    }
    fun next(): String? {
        spec = nextSpec
        specLocale = nextLocale
        setupNext()
        return spec
    }
    private fun setupNext() {
        nextLocale = false
        if (specLocale) {
            nextSpec = spec
            val separator = nextSpec!!.lastIndexOf('_')
            if (separator > 0) { nextSpec = spec!!.substring(0, separator); nextLocale = true }
            else nextSpec = scriptName
        } else nextSpec = if (nextSpec !== scriptName) scriptName else null
    }
}
