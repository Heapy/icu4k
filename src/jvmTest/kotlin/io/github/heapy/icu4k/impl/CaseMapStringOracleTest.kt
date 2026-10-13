package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.CaseMapImpl as JavaCaseMap
import com.ibm.icu.text.Edits as JavaEdits
import io.github.heapy.icu4k.text.Edits
import kotlin.random.Random
import kotlin.test.*

class CaseMapStringOracleTest {
    private fun check(text: String) {
        for (locale in -1..6) for (options in listOf(0, 1, 7, 8, 0x4000, 0x4001, -1)) {
            val expectedEdits = JavaEdits().apply { addReplace(10, 20) }
            val edits = Edits().apply { addReplace(10, 20) }
            val expected = StringBuilder("prefix")
            val actual = StringBuilder("prefix")
            if (locale < 0) {
                JavaCaseMap.fold(options, text, expected, expectedEdits)
                assertSame(actual, CaseMapImpl.fold(options, text, actual, edits))
                assertEquals(JavaCaseMap.fold(options, text), CaseMapImpl.fold(options, text))
            } else {
                JavaCaseMap.toLower(locale, options, text, expected, expectedEdits)
                assertSame(actual, CaseMapImpl.toLower(locale, options, text, actual, edits))
                assertEquals(JavaCaseMap.toLower(locale, options, text), CaseMapImpl.toLower(locale, options, text))
            }
            assertEquals(expected.toString(), actual.toString(), "locale $locale options $options")
            assertEquals(expectedEdits.lengthDelta(), edits.lengthDelta())
            assertEquals(expectedEdits.numberOfChanges(), edits.numberOfChanges())
            val j = expectedEdits.fineIterator
            val k = edits.getFineIterator()
            while (true) {
                val next = j.next()
                assertEquals(next, k.next())
                assertEquals(listOf(j.hasChange(), j.oldLength(), j.newLength(), j.sourceIndex(), j.destinationIndex(), j.replacementIndex()),
                    listOf(k.hasChange(), k.oldLength(), k.newLength(), k.sourceIndex(), k.destinationIndex(), k.replacementIndex()))
                if (!next) break
            }
        }
    }
    @Test fun contextsAndMalformedUtf16Match() {
        listOf("", "Straße Iİıi ΣΟΣ ΟΣ ΟΣΑ", "I\u0301 J\u0300 I\u0307", "\u0587", "\u0130\u0149\u017f", "A".repeat(101)).forEach(::check)
        val random = Random(97)
        repeat(100) {
            check(CharArray(random.nextInt(200)) {
                when (random.nextInt(4)) {
                    0 -> random.nextInt(0x180).toChar()
                    1 -> random.nextInt(0x300, 0x400).toChar()
                    else -> random.nextInt(0x10000).toChar()
                }
            }.concatToString())
        }
    }
    @Test fun entireUnicodeRangeMatches() {
        val text = StringBuilder()
        for (cp in 0..0x10ffff) text.appendCodePoint(cp)
        check(text.toString())
    }
}
