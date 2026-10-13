package io.github.heapy.icu4k.text

import com.ibm.icu.text.Normalizer as J
import kotlin.random.Random
import kotlin.test.*

@Suppress("DEPRECATION")
class NormalizerArrayOracleTest {
    private val modes = listOf(J.NONE to Normalizer.NONE, J.NFD to Normalizer.NFD,
        J.NFC to Normalizer.NFC, J.NFKD to Normalizer.NFKD, J.NFKC to Normalizer.NFKC, J.FCD to Normalizer.FCD)
    @Test fun arrayRangesOverflowWritesAndConcatenationMatchOriginal() {
        val random = Random(692103)
        repeat(350) {
            val source = buildString { repeat(random.nextInt(15)) {
                append(listOf("A", "é", "\u030a", "\u0323", "ﬁ", "ｶﾞ", "각", "𝔸", "😀", "\ud800", "\udc00").random(random))
            } }.toCharArray()
            val start = random.nextInt(source.size + 1)
            val limit = random.nextInt(start, source.size + 1)
            for ((jm, km) in modes) for (option in listOf(0, 32)) {
                val jq = J.quickCheck(source, start, limit, jm, option)
                assertSame(if (jq === J.YES) Normalizer.YES else if (jq === J.NO) Normalizer.NO else Normalizer.MAYBE,
                    Normalizer.quickCheck(source, start, limit, km, option))
                assertEquals(J.isNormalized(source, start, limit, jm, option), Normalizer.isNormalized(source, start, limit, km, option))
                for (capacity in listOf(0, 1, 3, 40)) {
                    val jd = CharArray(capacity + 4) { '#' }; val kd = jd.copyOf()
                    val expected = runCatching { J.normalize(source, start, limit, jd, 2, capacity + 2, jm, option) }
                    val actual = runCatching { Normalizer.normalize(source, start, limit, kd, 2, capacity + 2, km, option) }
                    assertEquals(expected.getOrNull(), actual.getOrNull())
                    assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass)
                    assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message)
                    assertContentEquals(jd, kd)
                }
                val left = J.normalize(source.concatToString(0, start), jm, option)
                val right = J.normalize(source.concatToString(start), jm, option)
                assertEquals(J.concatenate(left, right, jm, option), Normalizer.concatenate(left, right, km, option))
                for (capacity in listOf(0, 1, 3, 60)) {
                    val jd = CharArray(capacity + 4) { '#' }; val kd = jd.copyOf()
                    val expected = runCatching { J.concatenate(left.toCharArray(), 0, left.length,
                        right.toCharArray(), 0, right.length, jd, 2, capacity + 2, jm, option) }
                    val actual = runCatching { Normalizer.concatenate(left.toCharArray(), 0, left.length,
                        right.toCharArray(), 0, right.length, kd, 2, capacity + 2, km, option) }
                    assertEquals(expected.getOrNull(), actual.getOrNull())
                    assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass)
                    assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message)
                    assertContentEquals(jd, kd)
                }
            }
        }
    }
    @Test fun compositionDecompositionCodePointsAndAliasing() {
        for (source in listOf("", "A\u030a", "ﬁé", "ｶﾞ𝔸", "각")) for (compat in listOf(false, true)) for (option in listOf(0, 32)) {
            assertEquals(J.compose(source, compat, option), Normalizer.compose(source, compat, option))
            assertEquals(J.decompose(source, compat, option), Normalizer.decompose(source, compat, option))
            val jd = CharArray(50); val kd = CharArray(50)
            assertEquals(J.compose(source.toCharArray(), jd, compat, option), Normalizer.compose(source.toCharArray(), kd, compat, option))
            assertContentEquals(jd, kd)
            assertEquals(J.decompose(source.toCharArray(), jd, compat, option), Normalizer.decompose(source.toCharArray(), kd, compat, option))
            assertContentEquals(jd, kd)
        }
        for (cp in listOf(0, 0xc5, 0xfb01, 0xac01, 0xd800, 0x1d538, 0x10ffff, -1, 0x110000)) for ((jm, km) in modes) {
            val expected = runCatching { J.normalize(cp, jm, 0) }; val actual = runCatching { Normalizer.normalize(cp, km) }
            assertEquals(expected.getOrNull(), actual.getOrNull())
            assertEquals(expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass)
        }
        val jd = "A#########".toCharArray(); val kd = jd.copyOf(); val right = "\u030a".toCharArray()
        assertEquals(J.concatenate(jd, 0, 1, right, 0, 1, jd, 0, 10, J.NFC, 0),
            Normalizer.concatenate(kd, 0, 1, right, 0, 1, kd, 0, 10, Normalizer.NFC, 0))
        assertContentEquals(jd, kd)
        assertFailsWith<IllegalArgumentException> { Normalizer.concatenate(right, 0, 1, kd, 0, 1, kd, 0, 10, Normalizer.NFC, 0) }
        assertFailsWith<IllegalArgumentException> { Normalizer.concatenate(right, 0, 1, kd, 0, 1, null, 0, 10, Normalizer.NFC, 0) }
    }
}
