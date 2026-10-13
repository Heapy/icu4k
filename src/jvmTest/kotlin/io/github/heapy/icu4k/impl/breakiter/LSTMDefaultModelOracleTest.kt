package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.impl.breakiter.LSTMBreakEngine as J
import com.ibm.icu.impl.breakiter.DictionaryBreakEngine as JD
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class LSTMDefaultModelOracleTest {
    @Test fun originalDefaultModelsAndTrainedBoundariesMatch() {
        for ((script, sources) in listOf(
            UScript.THAI to listOf("ภาษาไทยภาษาไทยภาษาไทย", "สวัสดีครับประเทศไทย", "ฉันกำลังเรียนภาษาไทย", "ก้ขคกขค", "A😀ภาษาไทยB"),
            UScript.MYANMAR to listOf("မြန်မာဘာသာစကားမြန်မာဘာသာစကား", "မင်္ဂလာပါ", "ကျွန်တော်မြန်မာစာလေ့လာနေပါတယ်", "ကခဂဃငစဆဇဈည", "A😀မြန်မာဘာသာB"))) {
            val jd = J.createData(script); val kd = assertNotNull(LSTMBreakEngine.createData(script))
            assertEquals(jd.fName, kd.fName); assertEquals(jd.fDict, kd.fDict); assertEquals(jd.fType.name, kd.fType.name)
            for ((a, b) in listOf(jd.fEmbedding to kd.fEmbedding, jd.fForwardW to kd.fForwardW, jd.fForwardU to kd.fForwardU,
                jd.fBackwardW to kd.fBackwardW, jd.fBackwardU to kd.fBackwardU, jd.fOutputW to kd.fOutputW)) {
                assertEquals(a.size, b.size)
                for (i in a.indices) assertContentEquals(a[i].map { it.toRawBits() }, b[i].map { it.toRawBits() })
            }
            for ((a, b) in listOf(jd.fForwardB to kd.fForwardB, jd.fBackwardB to kd.fBackwardB, jd.fOutputB to kd.fOutputB)) {
                assertContentEquals(a.map { it.toRawBits() }, b.map { it.toRawBits() })
            }
            val j = J.create(script, jd); val k = LSTMBreakEngine.create(script, kd)
            for (source in sources) for (start in listOf(0, 1, source.length / 2, source.length)) {
                for (end in listOf(start, (start + 2).coerceAtMost(source.length), source.length)) {
                    for (phrase in listOf(false, true)) for (direct in listOf(false, true)) {
                        val jt = java.text.StringCharacterIterator(source, start); val kt = StringCharacterIterator(source, start)
                        val jb = JD.DequeI(); val kb = DictionaryBreakEngine.DequeI()
                        val expected = if (direct) j.divideUpDictionaryRange(jt, start, end, jb, phrase) else j.findBreaks(jt, start, end, jb, phrase)
                        val actual = if (direct) k.divideUpDictionaryRange(kt, start, end, kb, phrase) else k.findBreaks(kt, start, end, kb, phrase)
                        assertEquals(expected, actual, "$script $source $start..$end")
                        assertEquals(jt.index, kt.getIndex()); assertEquals(jb.size(), kb.size())
                        for (i in 0 until jb.size()) assertEquals(jb.elementAt(i), kb.elementAt(i))
                    }
                }
            }
        }
        for (script in listOf(-1, UScript.LATIN, UScript.HAN, Int.MAX_VALUE)) {
            assertNull(J.createData(script)); assertNull(LSTMBreakEngine.createData(script))
        }
        for (script in listOf(UScript.KHMER, UScript.LAO)) {
            assertFailsWith<java.util.MissingResourceException> { J.createData(script) }
            assertFailsWith<io.github.heapy.icu4k.util.MissingResourceException> { LSTMBreakEngine.createData(script) }
        }
    }
}
