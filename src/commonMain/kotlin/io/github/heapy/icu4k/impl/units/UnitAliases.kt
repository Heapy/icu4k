// © 2025 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.impl.locale.localeReadOnlyList
import io.github.heapy.icu4k.impl.locale.localeReadOnlyMap
import io.github.heapy.icu4k.util.UResourceBundle

open class UnitAliases {
    open class Alias(val alias: String?, val replacement: String?)
    private class AllAliasSink : UResource.Sink() {
        val aliases = HashMap<String, String>()
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val alias = key.toString()
            if (value.getTable().findValue("replacement", value)) aliases[alias] = value.toString()
            else throw IllegalIcuArgumentException("No replacement found for alias: $alias")
        }
    }
    private val aliases: Map<String, String>
    init {
        val metadata = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "metadata") as ICUResourceBundle
        val sink = AllAliasSink()
        metadata.getAllChildrenWithFallback("alias/unit", sink)
        aliases = localeReadOnlyMap(sink.aliases)
    }
    open fun getAliases(): List<Alias> = localeReadOnlyList(aliases.map { Alias(it.key, it.value) })
    open fun getReplacement(alias: String?): String? = aliases[alias]
}
