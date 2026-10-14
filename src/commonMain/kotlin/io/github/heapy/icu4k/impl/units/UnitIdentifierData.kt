// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.UResourceBundle

/** Resource-backed identifier/category portion of UnitsData, separated from conversion objects. */
internal object UnitIdentifierData {
    object Constants {
        const val kAliasOffset = 51200
        const val kSimpleUnitOffset = 512
        const val kPowerPartOffset = 256
        const val kInitialCompoundPartOffset = 192
        const val kCompoundPartOffset = 128
        const val kPrefixOffset = 64
        const val CONVERSION_UNIT_TABLE_NAME = "convertUnits"
        const val UNIT_PREFERENCE_TABLE_NAME = "unitPreferenceData"
        const val CATEGORY_TABLE_NAME = "unitQuantities"
        const val DEFAULT_REGION = "001"
        const val DEFAULT_USAGE = "default"
    }
    private val resource = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "units") as ICUResourceBundle
    private val categories = CategoriesSink().also { resource.getAllItemsWithFallback(Constants.CATEGORY_TABLE_NAME, it) }
    private val identifiers = SimpleUnitIdentifiersSink().also { resource.getAllItemsWithFallback(Constants.CONVERSION_UNIT_TABLE_NAME, it) }
    private val aliases = UnitAliases().getAliases()
    fun getSimpleUnits(): Array<String> = identifiers.simpleUnits!!
    fun getCategoryIndexOfSimpleUnit(index: Int): Int = checkedArrayElement(identifiers.simpleUnitCategories!!, index)
    fun getAliases(): List<UnitAliases.Alias> = aliases
    fun getReplacementFromAliasIndex(index: Int): String? = aliases[index].replacement
    fun getCategoryNames(): Array<String> = categories.names.toTypedArray()
    fun getBaseUnitToCategoryIndex(): Map<String, Int> = categories.indices
    class CategoriesSink : UResource.Sink() {
        val indices = HashMap<String, Int>()
        val names = ArrayList<String>()
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val array = value.getArray(); var i = 0
            while (array.getValue(i++, value)) {
                value.getTable().getKeyAndValue(0, key, value)
                indices[key.toString()] = names.size
                names.add(value.toString())
            }
        }
    }
    class SimpleUnitIdentifiersSink : UResource.Sink() {
        var simpleUnits: Array<String>? = null
        var simpleUnitCategories: IntArray? = null
        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
            val table = value.getTable()
            val names = ArrayList<String>(); val indexes = ArrayList<Int?>(); var i = 0
            while (table.getKeyAndValue(i++, key, value)) {
                if (key.toString() == "kilogram") continue
                if (!value.getTable().findValue("target", value)) continue
                val target = value.getString()
                names.add(key.toString()); indexes.add(categories.indices[target])
            }
            simpleUnits = names.toTypedArray()
            simpleUnitCategories = indexes.map { it!! }.toIntArray()
        }
    }
}
