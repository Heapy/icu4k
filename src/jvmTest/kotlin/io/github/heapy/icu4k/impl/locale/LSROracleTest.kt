package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.lang.UScript
import kotlin.test.*
import com.ibm.icu.impl.locale.LSR as Original

class LSROracleTest {
    @Test fun fullPinnedResourceDecodeMatchesOriginal() {
        val original = com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_BASE_NAME, "langInfo",
            com.ibm.icu.impl.ICUResourceBundle.ICU_DATA_CLASS_LOADER, com.ibm.icu.impl.ICUResourceBundle.OpenType.DIRECT).get("likely")
        val common = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "langInfo",
            ICUResourceBundleReader.embeddedLoader, ICUResourceBundle.OpenType.DIRECT)["likely"]
        val nums = original.get("lsrnum").intVector
        val m49 = original.get("m49").stringArray
        assertContentEquals(nums, common["lsrnum"].getIntVector())
        assertContentEquals(m49, common["m49"].getStringArray())
        val expected = Original.decodeInts(nums, m49)
        val actual = LSR.decodeInts(nums, m49)
        assertEquals(expected.size, actual.size)
        for (index in expected.indices) {
            val e = expected[index]
            val a = actual[index]
            assertEquals(listOf(e.language, e.script, e.region, e.flags), listOf(a.language, a.script, a.region, a.flags), "$index")
            assertEquals(e.toString(), a.toString())
            assertEquals(e.hashCode(), a.hashCode())
        }
    }
    @Test fun unusedNullNumericTableMatchesOriginal() {
        val expected = Original("en", "Latn", "US", 0).encodeToIntForResource(null)
        val actual = LSR("en", "Latn", "US", 0).encodeToIntForResource(null)
        assertEquals(expected, actual)
        val nums = intArrayOf(0, 1, expected)
        assertEquals(Original.decodeInts(nums, null).map { it.toString() }, LSR.decodeInts(nums, null).map { it.toString() })
        assertFailsWith<NullPointerException> { Original("en", "Latn", "001", 0).encodeToIntForResource(null) }
        assertFailsWith<NullPointerException> { LSR("en", "Latn", "001", 0).encodeToIntForResource(null) }
    }
    @Test fun indexEncodingEqualityAndHashMatchAcrossScriptsAndRegions() {
        for (region in listOf("", "US", "AA", "ZZ", "us", "USA", "000", "001", "419", "999", "12A", "1234")) {
            assertEquals(Original.indexForRegion(region), LSR.indexForRegion(region))
        }
        val m49 = listOf("001", "019", "419")
        for (language in listOf("en", "zh", "fil", "aaa", "zzz")) for (region in listOf("US", "AA", "ZZ", "001", "419"))
            for (code in 0 until UScript.CODE_LIMIT) {
                val script = UScript.getShortName(code) ?: continue
                val original = Original(language, script, region, 7)
                val common = LSR(language, script, region, 7)
                assertEquals(original.encodeToIntForResource(m49), common.encodeToIntForResource(m49))
                assertEquals(original.hashCode(), common.hashCode())
                assertEquals(original.toString(), common.toString())
                assertEquals(original.isEquivalentTo(Original(language, script, region, 0)), common.isEquivalentTo(LSR(language, script, region, 0)))
            }
    }
}
