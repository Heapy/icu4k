package io.github.heapy.icu4k.text

import com.ibm.icu.text.FilteredNormalizer2 as JavaFiltered
import com.ibm.icu.text.Normalizer2 as JavaNormalizer
import com.ibm.icu.text.Normalizer as JavaLegacy
import com.ibm.icu.text.UnicodeSet as JavaSet
import kotlin.random.Random
import kotlin.test.*

class FilteredNormalizer2OracleTest {
    @Test fun spansPropertiesAndAppendMatchOriginal() {
        val random = Random(187)
        val cps = listOf(65, 101, 0xe9, 0x301, 0x323, 0x327, 0x334, 0x1100, 0x1161, 0xac00, 0xad, 0xfb03, 0xdf, 0x1d165, 0x1f600, 0xd800, 0xdc00)
        for (strings in listOf(false, true)) for (frozen in listOf(false, true)) {
            val js = JavaSet().add(0x61, 0x7a).add(0x300, 0x323).add(0xac00, 0xd7a3).add(0x1d165).add(0xdf).add(0xfb03)
            val ks = UnicodeSet().add(0x61, 0x7a).add(0x300, 0x323).add(0xac00, 0xd7a3).add(0x1d165).add(0xdf).add(0xfb03)
            if (strings) { js.add("A\u0301"); ks.add("A\u0301") }
            if (frozen) { js.freeze(); ks.freeze() }
            for (mode in Normalizer2.Mode.entries) {
                val j = JavaFiltered(JavaNormalizer.getInstance(null, "nfkc", JavaNormalizer.Mode.valueOf(mode.name)), js)
                val k = FilteredNormalizer2(Normalizer2.getInstance(null, "nfkc", mode), ks)
                repeat(300) {
                    val text = buildString { repeat(random.nextInt(24)) { append(UTF16.valueOf(cps.random(random))) } }
                    assertEquals(j.normalize(text), k.normalize(text), "$strings $frozen $mode")
                    assertEquals(j.isNormalized(text), k.isNormalized(text))
                    assertEquals(j.spanQuickCheckYes(text), k.spanQuickCheckYes(text))
                    val jq = j.quickCheck(text)
                    assertSame(if (jq === JavaLegacy.YES) Normalizer.YES else if (jq === JavaLegacy.NO) Normalizer.NO else Normalizer.MAYBE, k.quickCheck(text))
                    val jd = StringBuilder("prefix!"); val kd = StringBuilder("prefix!")
                    j.normalize(text, jd as Appendable); k.normalize(text, kd as Appendable)
                    assertEquals(jd.toString(), kd.toString())
                    for (normalize in listOf(false, true)) {
                        val first = j.normalize("A!e")
                        val second = if (normalize) text else j.normalize(text)
                        val ja = StringBuilder(first); val ka = StringBuilder(first)
                        if (normalize) { j.normalizeSecondAndAppend(ja, second); k.normalizeSecondAndAppend(ka, second) }
                        else { j.append(ja, second); k.append(ka, second) }
                        assertEquals(ja.toString(), ka.toString())
                    }
                    val c = cps.random(random)
                    assertEquals(j.getDecomposition(c), k.getDecomposition(c))
                    assertEquals(j.getRawDecomposition(c), k.getRawDecomposition(c))
                    assertEquals(j.getCombiningClass(c), k.getCombiningClass(c))
                    assertEquals(j.hasBoundaryBefore(c), k.hasBoundaryBefore(c))
                    assertEquals(j.hasBoundaryAfter(c), k.hasBoundaryAfter(c))
                    assertEquals(j.isInert(c), k.isInert(c))
                }
            }
        }
    }
}
