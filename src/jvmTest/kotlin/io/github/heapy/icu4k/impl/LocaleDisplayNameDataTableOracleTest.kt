package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleDisplayNameDataTableOracleTest {
    @Test fun resourceLookupsAndDeprecatedCodesMatch() {
        for (path in listOf(ICUData.ICU_LANG_BASE_NAME, ICUData.ICU_REGION_BASE_NAME)) for (id in listOf("en", "en_US", "root")) {
            val j = com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(path, id) as com.ibm.icu.impl.ICUResourceBundle
            val k = ICUResourceBundle.getBundleInstance(path, id)
            val tables = if (path == ICUData.ICU_LANG_BASE_NAME) listOf("Languages", "Scripts", "Variants", "Keys", "Types", "Languages%short", "localeDisplayPattern") else listOf("Countries", "Countries%short", "Countries%variant")
            for (table in tables + "missing") {
                val codes = listOf("missing", "", "iw", "in", "ji", "mo", "sh", "BU", "DD", "TP", "ZR", "US", "separator", "pattern", "calendar", "gregorian") + (j.findWithFallback(table)?.keySet()?.toList() ?: emptyList())
                for (code in codes) for (subtable in listOf(null, "missing", "calendar")) for (default in listOf(null, "fallback")) {
                    assertEquals(com.ibm.icu.impl.ICUResourceTableAccess.getTableString(j, table, subtable, code, default), ICUResourceTableAccess.getTableString(k, table, subtable, code, default), "$path/$id/$table/$subtable/$code")
                }
            }
            val data = ResourceLocaleDisplayNameDataTable(path, ULocale(id), false)
            assertEquals(j.uLocale.name, data.getLocale().getName())
        }
    }
    @Test fun displayContextEnumsMatchSource() {
        assertEquals(com.ibm.icu.text.DisplayContext.values().map { it.name }, io.github.heapy.icu4k.text.DisplayContext.entries.map { it.name })
        for (context in io.github.heapy.icu4k.text.DisplayContext.entries) {
            val original = com.ibm.icu.text.DisplayContext.valueOf(context.name)
            assertEquals(original.type().name, context.type().name); assertEquals(original.value(), context.value()); assertEquals(original.ordinal, context.ordinal)
        }
    }
}
