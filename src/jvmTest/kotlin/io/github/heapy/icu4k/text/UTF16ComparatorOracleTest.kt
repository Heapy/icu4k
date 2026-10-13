package io.github.heapy.icu4k.text

import com.ibm.icu.text.UTF16.StringComparator as J
import kotlin.random.Random
import kotlin.test.*

class UTF16ComparatorOracleTest {
    @Test fun allOptionsExactResultsNullAndMalformedTextMatchOriginal() {
        val random = Random(710662)
        val atoms = listOf("", "A", "a", "ß", "ss", "İ", "I", "ı", "é", "e\u0301", "𐐀", "𐐨", "\ud800", "\udc00", "😀", "\ue000")
        val sources = mutableListOf<String?>(null, "", "\ud800\ud800\udc01", "\ud800\udc00")
        sources.addAll(atoms)
        repeat(500) { sources += buildString { repeat(random.nextInt(1, 10)) { append(atoms.random(random)) } } }
        val j = J(); val k = UTF16.StringComparator()
        for (codePoint in listOf(false, true)) for (ignore in listOf(false, true)) for (fold in 0..1) {
            j.setCodePointCompare(codePoint); k.setCodePointCompare(codePoint)
            j.setIgnoreCase(ignore, fold); k.setIgnoreCase(ignore, fold)
            assertEquals(j.codePointCompare, k.getCodePointCompare())
            assertEquals(j.ignoreCase, k.getIgnoreCase())
            assertEquals(j.ignoreCaseOption, k.getIgnoreCaseOption())
            val jc = J(codePoint, ignore, fold); val kc = UTF16.StringComparator(codePoint, ignore, fold)
            for (a in sources) for (b in sources.take(20) + sources.random(random)) {
                assertEquals(j.compare(a, b), k.compare(a, b), "$a / $b codePoint=$codePoint ignore=$ignore fold=$fold")
                assertEquals(jc.compare(a, b), kc.compare(a, b))
            }
        }
        for (invalid in listOf(-1, 2, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val je = assertFailsWith<IllegalArgumentException> { J(false, false, invalid) }
            val ke = assertFailsWith<IllegalArgumentException> { UTF16.StringComparator(false, false, invalid) }
            assertEquals(je.message, ke.message)
            for (ignore in listOf(false, true)) {
                assertFailsWith<IllegalArgumentException> { j.setIgnoreCase(ignore, invalid) }
                assertFailsWith<IllegalArgumentException> { k.setIgnoreCase(ignore, invalid) }
                assertEquals(j.ignoreCase, k.getIgnoreCase())
                assertEquals(j.ignoreCaseOption, k.getIgnoreCaseOption())
            }
        }
    }
}
