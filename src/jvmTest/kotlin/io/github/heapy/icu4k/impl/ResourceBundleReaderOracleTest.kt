package io.github.heapy.icu4k.impl

import kotlin.test.*

class ResourceBundleReaderOracleTest {
    private fun call(target: Any, name: String, vararg args: Any): Any? {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            val method = cls.declaredMethods.firstOrNull { it.name == name && it.parameterCount == args.size }
            if (method != null) { method.isAccessible = true; return method.invoke(target, *args) }
            cls = cls.superclass
        }
        error(name)
    }
    @Test fun japaneseAndRootRawResourceTreesMatchOriginal() {
        val cls = com.ibm.icu.impl.ICUResourceBundleReader::class.java
        val factory = cls.declaredMethods.single { it.name == "getReader" }
        factory.isAccessible = true
        for (locale in listOf("ja", "root", "jaml")) {
            val j = factory.invoke(null, com.ibm.icu.impl.ICUData.ICU_BRKITR_BASE_NAME, locale, cls.classLoader)!!
            val k = ICUResourceBundleReader(ICUBinary.getRequiredData("brkitr/$locale.res"))
            val root = call(j, "getRootResource") as Int
            assertEquals(root, k.rootResource)
            assertEquals(call(j, "getNoFallback"), k.noFallback)
            assertEquals(call(j, "getUsesPoolBundle"), k.usesPoolBundle)
            fun visit(res: Int) {
                when (ICUResourceBundleReader.RES_GET_TYPE(res)) {
                    0, 6 -> assertEquals(call(j, "getString", res), k.getString(res))
                    1 -> assertContentEquals(call(j, "getBinary", res, byteArrayOf()) as ByteArray, k.getBinary(res))
                    3 -> assertEquals(call(j, "getAlias", res), k.getAlias(res))
                    14 -> assertContentEquals(call(j, "getIntVector", res) as IntArray, k.getIntVector(res))
                    2, 4, 5, 8, 9 -> {
                        val table = ICUResourceBundleReader.URES_IS_TABLE(ICUResourceBundleReader.RES_GET_TYPE(res))
                        val jc = call(j, if (table) "getTable" else "getArray", res)!!
                        val kc = (if (table) k.getTable(res) else k.getArray(res))!!
                        assertEquals(call(jc, "getSize"), kc.size)
                        assertEquals(-1, kc.getContainerResource(-1))
                        assertEquals(-1, kc.getContainerResource(kc.size))
                        for (i in 0 until kc.size) {
                            val jr = call(jc, "getContainerResource", j, i) as Int
                            assertEquals(jr, kc.getContainerResource(i))
                            if (table) {
                                val key = call(jc, "getKey", j, i) as String
                                assertEquals(key, kc.getKey(i))
                                assertEquals(i, kc.findTableItem(key))
                                assertEquals(jr, kc.getResource(key))
                            }
                            visit(jr)
                        }
                        if (table) assertEquals(-1, kc.getResource("missing-key-for-test"))
                    }
                    7 -> Unit
                    else -> fail("Unsupported type $res")
                }
            }
            visit(root)
        }
    }
}
