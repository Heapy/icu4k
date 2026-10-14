// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Resource table lookup with deprecated-code and explicit locale fallback. */
object ICUResourceTableAccess {
    fun getTableString(path: String?, locale: ULocale?, tableName: String?, itemName: String?, defaultValue: String?): String? {
        val bundle = UResourceBundle.getBundleInstance(path, locale!!.getBaseName()) as ICUResourceBundle
        return getTableString(bundle, tableName, null, itemName, defaultValue)
    }
    fun getTableString(bundle: ICUResourceBundle?, tableName: String?, subtableName: String?, item: String?, defaultValue: String?): String? {
        var current = bundle
        var result: String? = null
        try {
            while (true) {
                val table = current!!.findWithFallback(tableName!!) ?: return defaultValue
                val subtable = if (subtableName == null) table else table.findWithFallback(subtableName)
                if (subtable != null) {
                    result = subtable.findStringWithFallback(item!!)
                    if (result != null) break
                }
                if (subtableName == null) {
                    val replacement = when (tableName) {
                        "Countries" -> LocaleIDs.getCurrentCountryID(item!!)
                        "Languages" -> LocaleIDs.getCurrentLanguageID(item!!)
                        else -> null
                    }
                    if (replacement != null) {
                        result = table.findStringWithFallback(replacement)
                        if (result != null) break
                    }
                }
                var fallback = table.findStringWithFallback("Fallback") ?: return defaultValue
                if (fallback.isEmpty()) fallback = "root"
                if (fallback == table.getULocale().getName()) return defaultValue
                current = UResourceBundle.getBundleInstance(current.resourceBaseName(), fallback) as ICUResourceBundle
            }
        } catch (_: Exception) { }
        return if (result != null && result.isNotEmpty()) result else defaultValue
    }
}
