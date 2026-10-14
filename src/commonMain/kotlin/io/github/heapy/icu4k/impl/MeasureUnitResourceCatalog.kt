// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.UResourceBundle

/** MeasureUnit's resource enumeration, ready for its instance-cache population. */
internal object MeasureUnitResourceCatalog {
    fun populate(addUnit: (type: String, subType: String) -> Unit) {
        val units = UResourceBundle.getBundleInstance(ICUData.ICU_UNIT_BASE_NAME, "en") as ICUResourceBundle
        units.getAllItemsWithFallback("units", object : UResource.Sink() {
            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                val types = value.getTable()
                var typeIndex = 0
                while (types.getKeyAndValue(typeIndex++, key, value)) {
                    if (key.contentEquals("compound") || key.contentEquals("coordinate")) continue
                    val type = key.toString()
                    val names = value.getTable()
                    var nameIndex = 0
                    while (names.getKeyAndValue(nameIndex++, key, value)) addUnit(type, key.toString())
                }
            }
        })
        val currencies = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "currencyNumericCodes") as ICUResourceBundle
        currencies.getAllItemsWithFallback("codeMap", object : UResource.Sink() {
            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                val table = value.getTable()
                var index = 0
                while (table.getKeyAndValue(index++, key, value)) addUnit("currency", key.toString())
            }
        })
    }
}
