package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.test.*

class ResourceScalarOracleTest {
    private class Root(reader: ICUResourceBundleReader, locale: String) : ICUResourceBundleImpl(WholeBundle(ICUData.ICU_BRKITR_BASE_NAME, locale, reader))
    @Test fun originalScalarResourcesAndStringRetentionMatchOriginal() {
        try {
            CacheValue.setStrength(CacheValue.Strength.SOFT)
            com.ibm.icu.impl.CacheValue.setStrength(com.ibm.icu.impl.CacheValue.Strength.SOFT)
            val counts = mutableMapOf<Int, Int>()
            for (locale in listOf("root", "jaml", "Thai_graphclust_model4_heavy")) {
                val reader = ICUResourceBundleReader.getReader(ICUData.ICU_BRKITR_BASE_NAME, locale)!!
                val root = Root(reader, locale)
                val originalRoot = com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(com.ibm.icu.impl.ICUData.ICU_BRKITR_BASE_NAME,
                    locale, com.ibm.icu.impl.ICUResourceBundle.ICU_DATA_CLASS_LOADER, true)
                fun visit(res: Int) {
                    val type = ICUResourceBundleReader.RES_GET_TYPE(res)
                    if (ICUResourceBundleReader.URES_IS_ARRAY(type) || ICUResourceBundleReader.URES_IS_TABLE(type)) {
                        val container = (reader.getArray(res) ?: reader.getTable(res))!!
                        for (i in 0 until container.size) visit(container.getContainerResource(i))
                        return
                    }
                    val name = when (type) { 0, 6 -> "ResourceString"; 1 -> "ResourceBinary"; 7 -> "ResourceInt"; 14 -> "ResourceIntVector"; else -> return }
                    if (counts.getOrElse(type) { 0 } >= 30) return
                    counts[type] = counts.getOrElse(type) { 0 } + 1
                    val originalClass = Class.forName("com.ibm.icu.impl.ICUResourceBundleImpl\$$name")
                    val original = originalClass.declaredConstructors.single().also { it.isAccessible = true }
                        .newInstance(originalRoot, "sample", res) as com.ibm.icu.util.UResourceBundle
                    val port: UResourceBundle = when (type) {
                        0, 6 -> ICUResourceBundleImpl.ResourceString(root, "sample", res)
                        1 -> ICUResourceBundleImpl.ResourceBinary(root, "sample", res)
                        7 -> ICUResourceBundleImpl.ResourceInt(root, "sample", res)
                        else -> ICUResourceBundleImpl.ResourceIntVector(root, "sample", res)
                    }
                    assertEquals(original.type, port.getType()); assertEquals(original.size, port.getSize())
                    assertEquals(original.key, port.getKey()); assertEquals(original.uLocale.name, port.getULocale().getName())
                    when (type) {
                        0, 6 -> {
                            assertEquals(original.string, port.getString())
                            val jf = originalClass.getDeclaredField("value").also { it.isAccessible = true }
                            val kf = port.javaClass.getDeclaredField("value").also { it.isAccessible = true }
                            assertEquals(jf.get(original) == null, kf.get(port) == null)
                        }
                        1 -> {
                            assertContentEquals(original.getBinary(null), port.getBinary(null))
                            assertEquals(original.binary.isReadOnly, port.getBinary().isReadOnly)
                            val j = ByteArray(original.binary.remaining()); val k = ByteArray(j.size)
                            assertSame(j, original.getBinary(j)); assertSame(k, port.getBinary(k))
                            assertContentEquals(j, k)
                        }
                        7 -> { assertEquals(original.int, port.getInt()); assertEquals(original.uInt, port.getUInt()) }
                        14 -> assertContentEquals(original.intVector, port.getIntVector())
                    }
                }
                visit(reader.rootResource)
            }
            assertTrue(counts[6]!! > 0); assertTrue(counts[7]!! > 0); assertTrue(counts[14]!! > 0)
        } finally {
            CacheValue.setStrength(CacheValue.Strength.SOFT)
            com.ibm.icu.impl.CacheValue.setStrength(com.ibm.icu.impl.CacheValue.Strength.SOFT)
        }
    }
}
