package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.CaseMapImpl as JavaCaseMap
import com.ibm.icu.text.Edits as JavaEdits
import io.github.heapy.icu4k.text.Edits
import kotlin.random.Random
import kotlin.test.*

class CaseMapStringOracleTest {
    private fun check(text: String) {
        checkMode(text, false)
        checkMode(text, true)
    }
    private fun checkMode(text: String, upper: Boolean) {
        for (locale in -1..6) for (options in listOf(0, 1, 7, 8, 0x4000, 0x4001, -1)) {
            val expectedEdits = JavaEdits().apply { addReplace(10, 20) }
            val edits = Edits().apply { addReplace(10, 20) }
            val expected = StringBuilder("prefix")
            val actual = StringBuilder("prefix")
            if (upper && locale >= 0) {
                JavaCaseMap.toUpper(locale, options, text, expected, expectedEdits)
                assertSame(actual, CaseMapImpl.toUpper(locale, options, text, actual, edits))
                assertEquals(JavaCaseMap.toUpper(locale, options, text), CaseMapImpl.toUpper(locale, options, text))
            } else if (locale < 0) {
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
    @Test fun greekAccentSequencesMatch() {
        listOf("άι άυ α\u0301ι α\u0301υ", "ή Ή η\u0301 ήα αή", "ΐ ΰ ῷ ᾄ", "α\u0345\u0345\u0301ι", "ΑΪ Η\u0301", "Ωί", "ή' ή'α").forEach(::check)
        val marks = intArrayOf(0x300, 0x301, 0x302, 0x303, 0x304, 0x306, 0x308, 0x311, 0x313, 0x314, 0x342, 0x343, 0x344, 0x345)
        val random = Random(872)
        repeat(200) {
            check(buildString {
                repeat(25) {
                    append(when (random.nextInt(5)) {
                        0 -> marks[random.nextInt(marks.size)].toChar()
                        1 -> ' '
                        2 -> random.nextInt(0x1f00, 0x2000).toChar()
                        else -> random.nextInt(0x370, 0x400).toChar()
                    })
                }
            })
        }
    }
    @Test fun entireUnicodeRangeMatches() {
        val text = StringBuilder()
        for (cp in 0..0x10ffff) text.appendCodePoint(cp)
        check(text.toString())
    }
}
