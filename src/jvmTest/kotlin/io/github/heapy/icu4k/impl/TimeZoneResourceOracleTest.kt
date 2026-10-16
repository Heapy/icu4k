package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class TimeZoneResourceOracleTest {
    @Test fun fullZoneAndMetazoneTreesMatchPinnedIcu4j() {
        fun compare(expected: com.ibm.icu.util.UResourceBundle, actual: UResourceBundle, path: String) {
            assertEquals(expected.type, actual.getType(), path)
            assertEquals(expected.size, actual.getSize(), path)
            assertEquals(expected.key, actual.getKey(), path)
            when (expected.type) {
                UResourceBundle.STRING -> assertEquals(expected.string, actual.getString(), path)
                UResourceBundle.INT -> assertEquals(expected.int, actual.getInt(), path)
                UResourceBundle.INT_VECTOR -> assertContentEquals(expected.intVector, actual.getIntVector(), path)
                UResourceBundle.BINARY -> assertContentEquals(expected.getBinary(null), actual.getBinary(null), path)
                UResourceBundle.TABLE, UResourceBundle.ARRAY -> {
                    assertEquals(expected.keySet(), actual.keySet(), path)
                    for (i in 0 until expected.size) compare(expected.get(i), actual.get(i), "$path/$i")
                }
                else -> error("Unexpected resource type ${expected.type} at $path")
            }
        }
        for (name in listOf("zoneinfo64", "metaZones")) {
            compare(
                com.ibm.icu.util.UResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_BASE_NAME, name),
                UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, name), name,
            )
            val bytes = assertNotNull(ICUData.getBytes("$name.res"))
            val original = com.ibm.icu.impl.ICUData.getRequiredStream("/" + com.ibm.icu.impl.ICUData.ICU_BASE_NAME + "/$name.res").use { it.readBytes() }
            assertContentEquals(original, bytes, name)
            bytes.fill(0)
            assertContentEquals(original, ICUData.getBytes("$name.res"), "$name defensive copy")
        }
    }
}
