package io.github.heapy.icu4k.impl

import kotlin.test.*
import com.ibm.icu.impl.UResource as OriginalResource
import com.ibm.icu.impl.ICUResourceBundle as OriginalBundle

class MeasureUnitResourceCatalogOracleTest {
    @Test fun completeOrderedEnumerationMatchesSourceSinks() {
        val expected = mutableListOf<Pair<String, String>>()
        val units = OriginalBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_UNIT_BASE_NAME, "en") as OriginalBundle
        units.getAllItemsWithFallback("units", object : OriginalResource.Sink() {
            override fun put(key: OriginalResource.Key, value: OriginalResource.Value, noFallback: Boolean) {
                val types = value.table
                var i = 0
                while (types.getKeyAndValue(i++, key, value)) {
                    if (key.contentEquals("compound") || key.contentEquals("coordinate")) continue
                    val type = key.toString(); val names = value.table
                    var j = 0
                    while (names.getKeyAndValue(j++, key, value)) expected.add(type to key.toString())
                }
            }
        })
        val currencies = OriginalBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_BASE_NAME, "currencyNumericCodes") as OriginalBundle
        currencies.getAllItemsWithFallback("codeMap", object : OriginalResource.Sink() {
            override fun put(key: OriginalResource.Key, value: OriginalResource.Value, noFallback: Boolean) {
                val table = value.table; var i = 0
                while (table.getKeyAndValue(i++, key, value)) expected.add("currency" to key.toString())
            }
        })
        val actual = mutableListOf<Pair<String, String>>()
        MeasureUnitResourceCatalog.populate { type, name -> actual.add(type to name) }
        assertEquals(expected, actual)
        val available = com.ibm.icu.util.MeasureUnit.getAvailable().map { it.type to it.subtype }.toSet()
        assertTrue(actual.all { it in available })
    }
}
