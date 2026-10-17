// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class BidiSupportTypesOracleTest {
    @Test fun rawRunsAndCopyMatchSourceForAllByteLevels() {
        val clazz = com.ibm.icu.text.BidiRun::class.java
        val ctor = clazz.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Byte::class.javaPrimitiveType).apply { isAccessible = true }
        val copy = clazz.getDeclaredMethod("copyFrom", clazz).apply { isAccessible = true }
        val insert = clazz.getDeclaredField("insertRemove").apply { isAccessible = true }
        fun source(r: com.ibm.icu.text.BidiRun) = listOf(r.start, r.limit, r.length, r.embeddingLevel, r.direction, r.isEvenRun, r.isOddRun, r.toString(), insert.getInt(r))
        fun port(r: BidiRun) = listOf(r.getStart(), r.getLimit(), r.getLength(), r.getEmbeddingLevel(), r.getDirection(), r.isEvenRun(), r.isOddRun(), r.toString(), r.insertRemove)
        for (level in -128..127) for ((start, limit) in listOf(0 to 0, 4 to 9, 9 to 4, -1 to 3, Int.MIN_VALUE to Int.MAX_VALUE)) {
            val s = ctor.newInstance(start, limit, level.toByte())
            val p = BidiRun(start, limit, level.toByte())
            insert.setInt(s, level); p.insertRemove = level
            assertEquals(source(s), port(p))
            val sc = ctor.newInstance(0, 0, 0.toByte()); val pc = BidiRun()
            copy.invoke(sc, s); pc.copyFrom(p)
            assertEquals(source(sc), port(pc))
        }
    }
    @Test fun classifierDefaultAndVirtualContextMatchSource() {
        val context = mutableMapOf(65 to 1)
        val s = object : com.ibm.icu.text.BidiClassifier(context) {
            override fun classify(c: Int): Int = (getContext() as? Map<*, *>)?.get(c) as Int? ?: super.classify(c)
        }
        val p = object : BidiClassifier(context) {
            override fun classify(c: Int): Int = (getContext() as? Map<*, *>)?.get(c) as Int? ?: super.classify(c)
        }
        for (c in listOf(-1, 0, 65, 66, 0x10ffff, Int.MAX_VALUE)) assertEquals(s.classify(c), p.classify(c))
        assertSame(s.getContext(), p.getContext())
        s.setContext(null); p.setContext(null)
        assertEquals(s.getContext(), p.getContext()); assertEquals(s.classify(65), p.classify(65))
    }
}
