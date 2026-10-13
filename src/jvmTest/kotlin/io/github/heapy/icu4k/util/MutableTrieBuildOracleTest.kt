package io.github.heapy.icu4k.util

import com.ibm.icu.util.MutableCodePointTrie as JavaBuilder
import com.ibm.icu.util.CodePointTrie as JavaTrie
import kotlin.random.Random
import kotlin.test.*

class MutableTrieBuildOracleTest {
    @Test fun exceedingDataLimitsClearsBuilderLikeOriginal() {
        val j = JavaBuilder(17, -3); val k = MutableCodePointTrie(17, -3)
        repeat(0x50000) { c -> j.set(c, c); k.set(c, c) }
        assertFailsWith<IndexOutOfBoundsException> { j.buildImmutable(JavaTrie.Type.FAST, JavaTrie.ValueWidth.BITS_32) }
        assertFailsWith<IndexOutOfBoundsException> { k.buildImmutable(CodePointTrie.Type.FAST, CodePointTrie.ValueWidth.BITS_32) }
        for (c in listOf(-1, 0, 65, 0x10000, 0x4ffff, 0x10ffff)) assertEquals(j[c], k[c])
        j.set(65, 99); k.set(65, 99)
        val jt = j.buildImmutable(JavaTrie.Type.FAST, JavaTrie.ValueWidth.BITS_32)
        val kt = k.buildImmutable(CodePointTrie.Type.FAST, CodePointTrie.ValueWidth.BITS_32)
        val out = java.io.ByteArrayOutputStream(); jt.toBinary(out)
        assertContentEquals(out.toByteArray(), kt.toBinary())
    }
    @Test fun binaryAndAllCodePointsMatchOriginal() {
        val random = Random(553)
        for (fixture in 0 until 7) {
            val j = JavaBuilder(0x12345, -7); val k = MutableCodePointTrie(0x12345, -7)
            if (fixture > 0) repeat(500) {
                val start = random.nextInt(if (fixture == 1) 0x1000 else if (fixture == 2) 0x10000 else 0x110000)
                val end = minOf(0x10ffff, start + random.nextInt(1024))
                val value = random.nextInt()
                j.setRange(start, end, value); k.setRange(start, end, value)
            }
            if (fixture == 4) { j.setRange(0x8000, 0x10ffff, 7); k.setRange(0x8000, 0x10ffff, 7) }
            if (fixture == 5) { j.set(0x10ffff, 42); k.set(0x10ffff, 42) }
            if (fixture == 6) repeat(0x20000) { j.set(it, it); k.set(it, it) }
            for (type in CodePointTrie.Type.entries) for (width in CodePointTrie.ValueWidth.entries) {
                val jb = j.clone(); val kb = k.clone()
                val jt = jb.buildImmutable(JavaTrie.Type.valueOf(type.name), JavaTrie.ValueWidth.valueOf(width.name))
                val kt = kb.buildImmutable(type, width)
                val out = java.io.ByteArrayOutputStream()
                jt.toBinary(out)
                assertContentEquals(out.toByteArray(), kt.toBinary(), "$fixture $type $width")
                for (c in 0..0x10ffff) if (jt[c] != kt[c]) fail("$fixture $type $width c=$c")
                assertEquals(jt[-1], kt[-1])
                assertEquals(jb[65], kb[65])
                assertEquals(jb[-1], kb[-1])
                // Reuse after the original build masks the error value but restores initial data.
                jb.set(65, 99); kb.set(65, 99)
                val jr = jb.buildImmutable(JavaTrie.Type.FAST, JavaTrie.ValueWidth.BITS_32)
                val kr = kb.buildImmutable(CodePointTrie.Type.FAST, CodePointTrie.ValueWidth.BITS_32)
                val again = java.io.ByteArrayOutputStream(); jr.toBinary(again)
                assertContentEquals(again.toByteArray(), kr.toBinary())
            }
        }
    }
}
