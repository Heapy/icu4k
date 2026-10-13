package io.github.heapy.icu4k.text

import com.ibm.icu.text.CaseMap as JavaCaseMap
import com.ibm.icu.text.Edits as JavaEdits
import kotlin.random.Random
import kotlin.test.*

class CaseMapFoldOracleTest {
    @Test fun allOptionCombinationsMatchPublicIcuApi() {
        val random = Random(71)
        repeat(100) {
            val src = CharArray(random.nextInt(150)) {
                if (random.nextBoolean()) random.nextInt(0x180).toChar() else random.nextInt(0x10000).toChar()
            }.concatToString()
            for (turkic in listOf(false, true)) for (omit in listOf(false, true)) {
                var j = JavaCaseMap.fold()
                var k = CaseMap.fold()
                if (turkic) { j = j.turkic(); k = k.turkic() }
                if (omit) { j = j.omitUnchangedText(); k = k.omitUnchangedText() }
                assertEquals(j.apply(src), k.apply(src))
                val je = JavaEdits()
                val ke = Edits()
                assertEquals(j.apply(src, StringBuilder("prefix"), je).toString(), k.apply(src, StringBuilder("prefix"), ke).toString())
                val ji = je.fineIterator
                val ki = ke.getFineIterator()
                while (true) {
                    val more = ji.next()
                    assertEquals(more, ki.next())
                    assertEquals(listOf(ji.hasChange(), ji.oldLength(), ji.newLength(), ji.sourceIndex(), ji.destinationIndex(), ji.replacementIndex()),
                        listOf(ki.hasChange(), ki.oldLength(), ki.newLength(), ki.sourceIndex(), ki.destinationIndex(), ki.replacementIndex()))
                    if (!more) break
                }
            }
        }
    }
}
