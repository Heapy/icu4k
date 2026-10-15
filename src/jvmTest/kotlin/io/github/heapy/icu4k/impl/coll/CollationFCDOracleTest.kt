// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.coll.CollationFCD as Source

class CollationFCDOracleTest {
    private val may = Source::class.java.getDeclaredMethod("mayHaveLccc", Int::class.javaPrimitiveType).also { it.isAccessible = true }
    private val tibetan = Source::class.java.getDeclaredMethod("maybeTibetanCompositeVowel", Int::class.javaPrimitiveType).also { it.isAccessible = true }
    private val fcd = Source::class.java.getDeclaredMethod("isFCD16OfTibetanCompositeVowel", Int::class.javaPrimitiveType).also { it.isAccessible = true }
    @Test fun everyPinnedBitmapEntryAndEveryUnicodeInput() {
        fun sourceTable(name: String): Any = Source::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(null)
        assertContentEquals(sourceTable("lcccIndex") as ByteArray, CollationFCDData.lcccIndex)
        assertContentEquals(sourceTable("lcccBits") as IntArray, CollationFCDData.lcccBits)
        assertContentEquals(sourceTable("tcccIndex") as ByteArray, CollationFCDData.tcccIndex)
        assertContentEquals(sourceTable("tcccBits") as IntArray, CollationFCDData.tcccBits)
        assertEquals(Source::class.java.desiredAssertionStatus(), CollationFCD::class.java.desiredAssertionStatus())
        for (c in 0..0xffff) {
            if (Source.hasLccc(c) != CollationFCD.hasLccc(c)) fail("hasLccc/$c")
            if (Source.hasTccc(c) != CollationFCD.hasTccc(c)) fail("hasTccc/$c")
            if (fcd.invoke(null, c) != CollationFCD.isFCD16OfTibetanCompositeVowel(c)) fail("FCD16/$c")
        }
        for (c in 0..0x10ffff) {
            if (may.invoke(null, c) != CollationFCD.mayHaveLccc(c)) fail("mayHaveLccc/$c")
            if (tibetan.invoke(null, c) != CollationFCD.maybeTibetanCompositeVowel(c)) fail("maybeTibetan/$c")
        }
    }
    @Test fun negativeSentinelsRawIntsAndOversizedCodeUnits() {
        val random = Random(7803)
        val inputs = listOf(Int.MIN_VALUE, -1, 0, 0xbf, 0xc0, 0x2ff, 0x300, 0xffff, 0x10000, 0x110000, 0x200000, Int.MAX_VALUE) + List(1000) { random.nextInt() }
        for (c in inputs) {
            assertEquals(outcome { Source.hasLccc(c) }, outcome { CollationFCD.hasLccc(c) }, "hasLccc/$c")
            assertEquals(outcome { Source.hasTccc(c) }, outcome { CollationFCD.hasTccc(c) }, "hasTccc/$c")
            assertEquals(outcome { may.invoke(null, c) }, outcome { CollationFCD.mayHaveLccc(c) }, "mayHaveLccc/$c")
            assertEquals(outcome { tibetan.invoke(null, c) }, outcome { CollationFCD.maybeTibetanCompositeVowel(c) }, "maybeTibetan/$c")
            assertEquals(outcome { fcd.invoke(null, c) }, outcome { CollationFCD.isFCD16OfTibetanCompositeVowel(c) }, "FCD16/$c")
        }
    }
    @Test fun disabledAssertionBoundsAndInitializationSnapshot() {
        val urls = arrayOf(Source::class.java.protectionDomain.codeSource.location, CollationFCD::class.java.protectionDomain.codeSource.location, Unit::class.java.protectionDomain.codeSource.location)
        URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
            loader.setDefaultAssertionStatus(false)
            val source = loader.loadClass(Source::class.java.name)
            val target = loader.loadClass(CollationFCD::class.java.name)
            val owner = target.getField("INSTANCE").get(null)
            for (name in listOf("hasLccc", "hasTccc")) {
                val sm = source.getDeclaredMethod(name, Int::class.javaPrimitiveType)
                val tm = target.getDeclaredMethod(name, Int::class.javaPrimitiveType)
                for (c in listOf(-1, 0x301, 0x10000, Int.MAX_VALUE)) {
                    assertEquals(outcome { sm.invoke(null, c) }, outcome { tm.invoke(owner, c) }, "$name/$c")
                }
                loader.setClassAssertionStatus(source.name, true)
                loader.setClassAssertionStatus(target.name, true)
                assertEquals("bounds", outcome { sm.invoke(null, 0x10000) })
                assertEquals("bounds", outcome { tm.invoke(owner, 0x10000) })
            }
        }
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        val cause = if (e is InvocationTargetException) e.targetException else e
        when (cause) { is IndexOutOfBoundsException -> "bounds"; else -> "${cause.javaClass.simpleName}:${cause.message}" }
    }
}
