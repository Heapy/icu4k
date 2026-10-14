package io.github.heapy.icu4k.impl

import kotlin.test.*

class ICUServiceOracleTest {
    @Test fun registryAndFallbackCacheMatchSource() {
        val j = object : com.ibm.icu.impl.ICUService("test") {
            override fun createKey(id: String?): Key? = id?.let { object : Key(it) {
                var current = it
                override fun currentID(): String = current
                override fun fallback(): Boolean { val end = current.lastIndexOf('_'); if (end < 0) return false; current = current.substring(0, end); return true }
                override fun isFallbackOf(idToCheck: String): Boolean = idToCheck == it || idToCheck.startsWith(it + "_")
            } }
        }
        val k = object : ICUService("test") {
            override fun createKey(id: String?): Key? = id?.let { object : Key(it) {
                var current = it
                override fun currentID(): String = current
                override fun fallback(): Boolean { val end = current.lastIndexOf('_'); if (end < 0) return false; current = current.substring(0, end); return true }
                override fun isFallbackOf(idToCheck: String?): Boolean = idToCheck == it || idToCheck?.startsWith(it + "_") == true
            } }
        }
        val jf = mutableListOf<com.ibm.icu.impl.ICUService.Factory>(); val kf = mutableListOf<ICUService.Factory>()
        for ((id, value, visible) in listOf(Triple("a", "base", true), Triple("a_b", "middle", true), Triple("a_b", "hidden", false), Triple("c", "other", true))) {
            jf.add(j.registerObject(value, id, visible)); kf.add(k.registerObject(value, id, visible))
            repeat(2) {
                for (query in listOf("a", "a_b", "a_b_c", "a_x", "missing")) {
                    val a = arrayOf<String?>("untouched"); val b = a.copyOf()
                    assertEquals(j.get(query, a), k.get(query, b)); assertContentEquals(a, b)
                    assertEquals(j.getDisplayName(query), k.getDisplayName(query))
                }
            }
            assertEquals(j.visibleIDs, k.getVisibleIDs()); assertEquals(j.getVisibleIDs("a"), k.getVisibleIDs("a"))
        }
        for (i in jf.indices.reversed()) { assertEquals(j.unregisterFactory(jf[i]), k.unregisterFactory(kf[i])); assertEquals(j.get("a_b_c"), k.get("a_b_c")) }
        assertEquals(j.isDefault, k.isDefault()); assertEquals(j.stats(), k.stats()); assertEquals(j.stats(), k.stats())
    }
    @Test fun delegatedLookupTracesAndActualDescriptorsMatch() {
        val j = com.ibm.icu.impl.ICUService(); val k = ICUService()
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        j.registerObject("base", "a"); k.registerObject("base", "a")
        val jf = object : com.ibm.icu.impl.ICUService.SimpleFactory("unused", "a") {
            override fun create(key: com.ibm.icu.impl.ICUService.Key?, service: com.ibm.icu.impl.ICUService?): Any? {
                jt.add(key!!.currentDescriptor())
                return "wrapper:" + service!!.getKey(key, null, this)
            }
        }
        val kf = object : ICUService.SimpleFactory("unused", "a") {
            override fun create(key: ICUService.Key?, service: ICUService?): Any? {
                kt.add(key!!.currentDescriptor()!!)
                return "wrapper:" + service!!.getKey(key, null, this)
            }
        }
        j.registerFactory(jf); k.registerFactory(kf)
        repeat(3) {
            val a = arrayOf<String?>(null); val b = a.copyOf()
            assertEquals(j.get("a", a), k.get("a", b)); assertContentEquals(a, b); assertEquals(jt, kt)
        }
        // Delegation still consults an existing descriptor cache before trying older factories.
        assertEquals(j.getKey(com.ibm.icu.impl.ICUService.Key("a"), null, jf), k.getKey(ICUService.Key("a"), null, kf))
        j.unregisterFactory(jf); k.unregisterFactory(kf)
        assertEquals(j.get("a"), k.get("a"))
        j.reset(); k.reset(); assertEquals(j.get("a"), k.get("a"))
        assertEquals(j.isDefault, k.isDefault())
    }

    @Test fun javaFactoryConstructorsAndProtectedFieldsAreRetained() {
        val constructor = ICUService.SimpleFactory::class.java.getConstructor(Any::class.java, String::class.java)
        val factory = constructor.newInstance("value", "id")
        val service = ICUService(); service.registerFactory(factory)
        assertEquals("value", service.get("id"))
        for (field in listOf("instance", "id", "visible")) {
            val source = com.ibm.icu.impl.ICUService.SimpleFactory::class.java.getDeclaredField(field)
            val port = ICUService.SimpleFactory::class.java.getDeclaredField(field)
            assertEquals(source.type, port.type); assertEquals(source.modifiers, port.modifiers)
        }
        assertEquals(com.ibm.icu.impl.ICUService::class.java.getDeclaredField("name").modifiers, ICUService::class.java.getDeclaredField("name").modifiers)
    }

}
