package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.CaseMapImpl as JavaCaseMap
import com.ibm.icu.text.BreakIterator
import com.ibm.icu.text.Edits as JavaEdits
import io.github.heapy.icu4k.text.Edits
import java.util.Locale
import kotlin.random.Random
import kotlin.test.*

class CaseMapTitleOracleTest {
    private fun check(text: String) {
        val locales = listOf(Locale.ROOT, Locale.forLanguageTag("tr"), Locale.forLanguageTag("lt"), Locale.forLanguageTag("el"), Locale.forLanguageTag("nl"), Locale.forLanguageTag("hy"))
        for ((n, locale) in locales.withIndex()) for (options in listOf(0, 0x100, 0x200, 0x400, 0x4000, 0x4100, 0x4200, 0x4400)) {
            val boundaries = BreakIterator.getWordInstance(locale).apply { setText(text) }
            val common = object : CaseMapImpl.TitleIterator {
                override fun first() = boundaries.first()
                override fun next() = boundaries.next()
            }
            val expected = StringBuilder("prefix")
            val actual = StringBuilder("prefix")
            val j = JavaEdits().apply { addReplace(5, 7) }
            val k = Edits().apply { addReplace(5, 7) }
            JavaCaseMap.toTitle(n + 1, options, boundaries, text, expected, j)
            CaseMapImpl.toTitle(n + 1, options, common, text, actual, k)
            assertEquals(expected.toString(), actual.toString(), "$locale $options $text")
            assertEquals(JavaCaseMap.toTitle(n + 1, options, boundaries, text), CaseMapImpl.toTitle(n + 1, options, common, text))
            assertEquals(j.numberOfChanges(), k.numberOfChanges())
            assertEquals(j.lengthDelta(), k.lengthDelta())
            val ji = j.fineIterator
            val ki = k.getFineIterator()
            while (true) {
                val next = ji.next()
                assertEquals(next, ki.next())
                assertEquals(listOf(ji.hasChange(), ji.oldLength(), ji.newLength(), ji.sourceIndex(), ji.destinationIndex(), ji.replacementIndex()),
                    listOf(ki.hasChange(), ki.oldLength(), ki.newLength(), ki.sourceIndex(), ki.destinationIndex(), ki.replacementIndex()))
                if (!next) break
            }
        }
    }
    @Test fun contextsAndDutchAccentsMatch() {
        listOf("", "ijssel IJssel iJssel", "íj\u0301 i\u0301j\u0301 ÍJ\u0301 i\u0301J\u0301", "ij\u0308 ij\u0301 i\u0301j", "123ABC --ΣΟΣ ΟΣΑ", "I\u0301 J\u0307 I\u0307", "ßABC ﬃABC ǳABC", "\u0587 ABC", "A".repeat(101)).forEach(::check)
        val random = Random(861)
        repeat(100) {
            check(CharArray(random.nextInt(150)) {
                when (random.nextInt(5)) {
                    0 -> ' '
                    1 -> random.nextInt(0x300, 0x400).toChar()
                    2 -> random.nextInt(0x180).toChar()
                    else -> random.nextInt(0x10000).toChar()
                }
            }.concatToString())
        }
    }
}
