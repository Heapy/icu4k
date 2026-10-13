package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.impl.breakiter.CjkBreakEngine as J
import com.ibm.icu.impl.breakiter.DictionaryBreakEngine as JD
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.random.Random
import kotlin.test.*

class MlBreakEngineOracleTest {
    @Test fun originalModelAndMlDispatchMatchIcu4j() {
        val property = "com.ibm.icu.impl.breakiter.useMLPhraseBreaking"
        val old = System.getProperty(property)
        System.setProperty(property, "true")
        try {
            val random = Random(861915)
            val atoms = listOf("日", "本", "語", "の", "が", "カ", "ナ", "ｶﾞ", "㍿", "한", "국", "😀", "𠀀", "A", "［", "］", " ", "\ud800", "\udc00")
            val sources = mutableListOf("", "日", "日本語の文章です", "東京都に行きます", "［携帯電話］正しい選択", "しかもロゴがＵｎｉｃｏｄｅ！", "乗車率９０％程度だろうか", "ｶﾞｯｺｳ日本語", "한국어단어분리")
            repeat(250) { sources += buildString { repeat(random.nextInt(1, 30)) { append(atoms.random(random)) } } }
            for (korean in listOf(false, true)) {
                val j = J(korean); val k = CjkBreakEngine(korean, true)
                for (source in sources) repeat(3) { trial ->
                    val start = if (trial == 0) 0 else random.nextInt(source.length + 1)
                    val end = if (trial == 0) source.length else random.nextInt(start, source.length + 1)
                    for (phrase in listOf(false, true)) for (direct in listOf(false, true)) {
                        val jt = java.text.StringCharacterIterator(source, start); val kt = StringCharacterIterator(source, start)
                        val jb = JD.DequeI(); val kb = DictionaryBreakEngine.DequeI()
                        if (start > 0 && trial == 1) { jb.push(start); kb.push(start) }
                        val expected = runCatching { if (direct) j.divideUpDictionaryRange(jt, start, end, jb, phrase) else j.findBreaks(jt, start, end, jb, phrase) }
                        val actual = runCatching { if (direct) k.divideUpDictionaryRange(kt, start, end, kb, phrase) else k.findBreaks(kt, start, end, kb, phrase) }
                        val context = "$source $start..$end korean=$korean phrase=$phrase direct=$direct"
                        if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow(), context)
                        else assertTrue(actual.isFailure, context)
                        assertEquals(jt.index, kt.getIndex(), context)
                        assertEquals(jb.size(), kb.size(), context)
                        for (i in 0 until jb.size()) assertEquals(jb.elementAt(i), kb.elementAt(i), context)
                    }
                }
            }
        } finally {
            if (old == null) System.clearProperty(property) else System.setProperty(property, old)
        }
    }
}
