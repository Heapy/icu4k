package io.github.heapy.icu4k.text

import com.ibm.icu.text.Normalizer as J
import kotlin.random.Random
import kotlin.test.*

@Suppress("DEPRECATION")
class NormalizerIteratorOracleTest {
    private val modes = listOf(J.NONE to Normalizer.NONE, J.NFD to Normalizer.NFD,
        J.NFC to Normalizer.NFC, J.NFKD to Normalizer.NFKD, J.NFKC to Normalizer.NFKC, J.FCD to Normalizer.FCD)

    @Test fun normalizationSegmentsSourcePositionsAndDirectionChangesMatchOriginal() {
        val random = Random(639182)
        val sources = mutableListOf("", "A\u030a\u0323B", "ｶﾞカタカナ", "㍿①ﬁ", "각각", "𝔸😀é", "\u1d2c\u1d2d\u1d2e", "\u0301\u0323A")
        repeat(150) {
            sources += buildString {
                repeat(random.nextInt(1, 30)) {
                    append(listOf("A", "é", "\u030a", "\u0323", "ﬁ", "ｶﾞ", "각", "𝔸", "😀", "\ud800", "\udc00", "\u1d2c").random(random))
                }
            }
        }
        for (source in sources) for ((jm, km) in modes) for (option in listOf(0, 32)) {
            assertEquals(J.normalize(source, jm, option), Normalizer.normalize(source, km, option))
            assertEquals(J.isNormalized(source, jm, option), Normalizer.isNormalized(source, km, option))
            val jq = J.quickCheck(source, jm, option)
            assertSame(if (jq === J.YES) Normalizer.YES else if (jq === J.NO) Normalizer.NO else Normalizer.MAYBE,
                Normalizer.quickCheck(source, km, option))
            val j = J(source, jm, option); val k = Normalizer(source, km, option)
            repeat(source.length * 4 + 5) {
                assertEquals(j.next(), k.next()); assertEquals(j.index, k.getIndex())
            }
            repeat(source.length * 4 + 5) {
                assertEquals(j.previous(), k.previous()); assertEquals(j.index, k.getIndex())
            }
            repeat(80) {
                val result = when (random.nextInt(9)) {
                    0 -> j.next() to k.next()
                    1 -> j.previous() to k.previous()
                    2 -> j.current() to k.current()
                    3 -> j.first() to k.first()
                    4 -> j.last() to k.last()
                    5 -> { val index = random.nextInt(source.length + 1); j.setIndex(index) to k.setIndex(index) }
                    6 -> { val index = random.nextInt(source.length + 1); j.setIndexOnly(index); k.setIndexOnly(index); 0 to 0 }
                    7 -> { val (a, b) = modes.random(random); j.setMode(a); k.setMode(b); 0 to 0 }
                    else -> { val value = random.nextBoolean(); j.setOption(32, value); k.setOption(32, value); 0 to 0 }
                }
                assertEquals(result.first, result.second, "$source option=$option")
                assertEquals(j.index, k.getIndex())
                assertEquals(j.getOption(32), k.getOption(32))
                val jc = j.clone(); val kc = k.clone()
                repeat(3) { assertEquals(jc.next(), kc.next()); assertEquals(jc.index, kc.getIndex()) }
            }
        }
    }

    @Test fun iteratorOwnershipAndTextReplacementMatchOriginal() {
        assertFailsWith<IndexOutOfBoundsException> {
            J(java.text.StringCharacterIterator("XﬁA\u030aY", 1, 5, 2), J.NFKC, 0).next()
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Normalizer(StringCharacterIterator("XﬁA\u030aY", 1, 5, 2), Normalizer.NFKC, 0).next()
        }
        val js = java.text.StringCharacterIterator("XﬁA\u030aY", 0, 5, 2)
        val ks = StringCharacterIterator("XﬁA\u030aY", 0, 5, 2)
        val j = J(js, J.NFKC, 0); val k = Normalizer(ks, Normalizer.NFKC, 0)
        repeat(6) { assertEquals(j.next(), k.next()); assertEquals(j.index, k.getIndex()) }
        assertEquals(js.index, ks.getIndex())
        j.setText("㍿é".toCharArray()); k.setText("㍿é".toCharArray())
        assertEquals(j.text, k.getText()); assertEquals(j.length, k.getLength())
        repeat(8) { assertEquals(j.next(), k.next()); assertEquals(j.index, k.getIndex()) }
        val ju = com.ibm.icu.text.UCharacterIterator.getInstance("ﬁé")
        val ku = UCharacterIterator.getInstance("ﬁé")
        j.setText(ju); k.setText(ku)
        j.next(); k.next()
        assertEquals(ju.index, ku.getIndex())
        j.setText(js); k.setText(ks)
        assertEquals(js.index, ks.getIndex())
        repeat(6) { assertEquals(j.next(), k.next()); assertEquals(j.index, k.getIndex()) }
    }
}
