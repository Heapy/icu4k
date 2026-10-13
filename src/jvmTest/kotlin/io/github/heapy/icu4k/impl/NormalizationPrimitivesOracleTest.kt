package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaNormalizer
import kotlin.random.Random
import kotlin.test.*

class NormalizationPrimitivesOracleTest {
    @Test fun hangulAndUtf16PrimitivesMatchOriginal() {
        for (cp in -1..0x110000) {
            assertEquals(JavaNormalizer.Hangul.isHangul(cp), Normalizer2Impl.Hangul.isHangul(cp))
            assertEquals(JavaNormalizer.Hangul.isHangulLV(cp), Normalizer2Impl.Hangul.isHangulLV(cp))
            assertEquals(JavaNormalizer.Hangul.isJamo(cp), Normalizer2Impl.Hangul.isJamo(cp))
            assertEquals(JavaNormalizer.Hangul.isJamoL(cp), Normalizer2Impl.Hangul.isJamoL(cp))
            assertEquals(JavaNormalizer.Hangul.isJamoV(cp), Normalizer2Impl.Hangul.isJamoV(cp))
            assertEquals(JavaNormalizer.Hangul.isJamoT(cp), Normalizer2Impl.Hangul.isJamoT(cp))
            assertEquals(JavaNormalizer.UTF16Plus.isSurrogate(cp), Normalizer2Impl.UTF16Plus.isSurrogate(cp))
            assertEquals(JavaNormalizer.UTF16Plus.isLeadSurrogate(cp), Normalizer2Impl.UTF16Plus.isLeadSurrogate(cp))
            assertEquals(JavaNormalizer.UTF16Plus.isTrailSurrogate(cp), Normalizer2Impl.UTF16Plus.isTrailSurrogate(cp))
        }
        for (cp in 0xac00..0xd7a3) {
            val j = StringBuilder("x"); val k = StringBuilder("x")
            assertEquals(JavaNormalizer.Hangul.decompose(cp, j), Normalizer2Impl.Hangul.decompose(cp, k)); assertEquals(j.toString(), k.toString())
            j.clear(); k.clear(); JavaNormalizer.Hangul.getRawDecomposition(cp, j); Normalizer2Impl.Hangul.getRawDecomposition(cp, k); assertEquals(j.toString(), k.toString())
        }
        val random = Random(716)
        repeat(1000) {
            val a = CharArray(random.nextInt(10)) { random.nextInt(0x10000).toChar() }.concatToString()
            val b = if (random.nextBoolean()) a else "x$a"
            assertEquals(JavaNormalizer.UTF16Plus.equal(a, b), Normalizer2Impl.UTF16Plus.equal(a, b))
            for (start in 0..a.length) for (end in start..a.length) assertEquals(JavaNormalizer.UTF16Plus.equal(a, start, end, b, 0, minOf(end - start, b.length)), Normalizer2Impl.UTF16Plus.equal(a, start, end, b, 0, minOf(end - start, b.length)))
        }
    }
}
