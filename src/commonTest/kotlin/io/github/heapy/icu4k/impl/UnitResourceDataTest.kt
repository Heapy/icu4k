package io.github.heapy.icu4k.impl

import kotlin.test.*

class UnitResourceDataTest {
    @Test fun everyPinnedUnitBundleIsEmbeddedAndMatchesSourceFingerprint() {
        val names=mutableListOf<String>()
        ICUData.visitResources(ICUData.ICU_UNIT_BASE_NAME+"/") { names.add(it) }
        names.sort()
        assertEquals(535,names.size)
        var hash=-3750763034362895579L
        fun emit(value:Int) { hash=(hash xor (value and 255).toLong())*1099511628211L }
        for(name in names) {
            val path=ICUData.ICU_UNIT_BASE_NAME+"/"+name
            assertTrue(ICUData.exists(path),name)
            val bytes=assertNotNull(ICUData.getBytes(path),name)
            for(c in name) emit(c.code)
            emit(0)
            for(b in bytes) emit(b.toInt())
        }
        // Captured from every raw unit/*.res in pinned ICU4J release-78.3.
        assertEquals(935249912425336580L,hash)
        val first=ICUData.getBytes("unit/fi.res")!!
        val second=ICUData.getBytes("unit/fi.res")!!
        assertNotSame(first,second)
        first[0]=(first[0].toInt() xor 255).toByte()
        assertContentEquals(second,ICUData.getBytes("unit/fi.res"))
    }
}
