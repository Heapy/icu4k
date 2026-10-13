package io.github.heapy.icu4k.text

import com.ibm.icu.text.Normalizer as J
import kotlin.random.Random
import kotlin.test.*

@Suppress("DEPRECATION")
class NormalizerCompareOracleTest {
    private val options = buildList {
        for (fold in listOf(0, 0x10000)) for (order in listOf(0, 0x8000))
            for (turkic in listOf(0, 1)) for (version in listOf(0, 32 shl 20)) add(fold or order or turkic or version)
    }
    @Test fun incrementalFoldingDecompositionAndUtf16OrderingMatchOriginal() {
        val random = Random(672104)
        val strings = mutableListOf("", "A", "a", "Å", "A\u030a", "A\u0323\u030a", "A\u030a\u0323",
            "ß", "ss", "İ", "I\u0307", "I", "ı", "ΐ", "ι\u0308\u0301", "각", "각", "𐐀", "𐐨",
            "\ud800\ud800\udc01", "\ud800\udc00", "\ud800", "\udc00", "\ue000", "𝔸", "😀")
        repeat(600) {
            strings += buildString { repeat(random.nextInt(8)) {
                append(listOf("A", "é", "ß", "İ", "I", "\u030a", "\u0323", "ﬁ", "각", "𐐀", "𐐨", "😀", "\ud800", "\udc00", "\ue000").random(random))
            } }
        }
        for (a in strings) {
            val b = strings.random(random)
            for (option in options) {
                assertEquals(J.compare(a, b, option), Normalizer.compare(a, b, option), "$a / $b option=$option")
                val aa = ("!" + a + "?").toCharArray(); val bb = ("!" + b + "?").toCharArray()
                assertEquals(J.compare(aa, 1, aa.size - 1, bb, 1, bb.size - 1, option),
                    Normalizer.compare(aa, 1, aa.size - 1, bb, 1, bb.size - 1, option))
                assertEquals(J.compare(aa, bb, option), Normalizer.compare(aa, bb, option))
            }
        }
        // Compare canonical and full-fold mappings for every code point that changes.
        val nfd = com.ibm.icu.text.Normalizer2.getNFDInstance()
        for (cp in 0..0x10ffff) {
            val source = String(Character.toChars(cp))
            val decomposition = nfd.getDecomposition(cp)
            val fold = com.ibm.icu.lang.UCharacter.foldCase(source, 0)
            if (decomposition != null || fold != source) {
                for (other in listOfNotNull(decomposition, fold)) for (option in listOf(0, 0x10000, 0x10001, 0x18000)) {
                    assertEquals(J.compare(source, other, option), Normalizer.compare(source, other, option), "cp=$cp option=$option")
                    assertEquals(J.compare(cp, other, option), Normalizer.compare(cp, other, option))
                }
            }
        }
        repeat(2000) {
            val a = random.nextInt(0x110000); val b = random.nextInt(0x110000); val option = options.random(random)
            assertEquals(J.compare(a, b, option), Normalizer.compare(a, b, option))
        }
    }
    @Test fun comparisonCoreWithoutCanonicalOptionMatchesOriginal() {
        val method = J::class.java.getDeclaredMethod("cmpEquivFold", CharSequence::class.java, CharSequence::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        val random = Random(672105)
        val atoms = listOf("A", "a", "ß", "ss", "İ", "I", "𐐀", "𐐨", "\ud800", "\udc00", "\ud800\ud800\udc01", "\ue000", "각", "é", "e\u0301", "")
        repeat(8000) {
            val a = atoms.random(random) + atoms.random(random)
            val b = atoms.random(random) + atoms.random(random)
            val option = listOf(0, 1, 0x8000, 0x10000, 0x10001, 0x18000, 0x80000, 0x98001).random(random)
            assertEquals(method.invoke(null, a, b, option), Normalizer.cmpEquivFold(a, b, option), "$a / $b option=$option")
        }
        assertFailsWith<IllegalArgumentException> { Normalizer.compare(null, 0, 0, charArrayOf(), 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { Normalizer.compare(charArrayOf(), -1, 0, charArrayOf(), 0, 0, 0) }
        assertFailsWith<IndexOutOfBoundsException> { Normalizer.compare(charArrayOf(), 0, 1, charArrayOf(), 0, 0, 0) }
    }
}
