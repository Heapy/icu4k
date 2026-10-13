package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.impl.breakiter.CjkBreakEngine as J
import com.ibm.icu.impl.breakiter.DictionaryBreakEngine as JD
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.random.Random
import kotlin.test.*

class CjkBreakEngineOracleTest {
    @Test fun dictionaryCostsNormalizationAndPhraseBoundariesMatchOriginal() {
        val random = Random(871055)
        val sources = mutableListOf("", "日本語の文章です", "東京都に行きます", "中文分词测试", "한국어단어분리", "カタカナテスト", "ｶﾞｯｺｳ", "㍿株式会社", "［携帯電話］正しい選択", "乗車率９０％程度だろうか", "しかもロゴがＵｎｉｃｏｄｅ！", "😀日本𠀀語", "A中文B", "カ".repeat(21))
        val atoms = listOf("日", "本", "語", "中", "文", "的", "の", "は", "が", "カ", "タ", "ナ", "ー", "ｶﾞ", "㍿", "①", "한", "국", "어", "😀", "𠀀", "A", "［", "］", " ", "\ud800", "\udc00")
        repeat(350) { sources += buildString { repeat(random.nextInt(1, 35)) { append(atoms.random(random)) } } }
        for (korean in listOf(false, true)) {
            val j = J(korean); val k = CjkBreakEngine(korean)
            for (cp in 0..0x10ffff) if (j.handles(cp) != k.handles(cp)) fail("handles cp=$cp korean=$korean")
            for (source in sources) repeat(3) { trial ->
                val start = if (trial == 0) 0 else random.nextInt(source.length + 1)
                val end = if (trial == 0) source.length else random.nextInt(start, source.length + 1)
                for (phrase in listOf(false, true)) for (direct in listOf(false, true)) {
                    val jt = java.text.StringCharacterIterator(source, start)
                    val kt = StringCharacterIterator(source, start)
                    val jb = JD.DequeI(); val kb = DictionaryBreakEngine.DequeI()
                    // A preexisting valid boundary exercises deduplication without an invalid cursor.
                    if (start > 0 && trial % 2 == 1) { jb.push(start); kb.push(start) }
                    val expected = runCatching { if (direct) j.divideUpDictionaryRange(jt, start, end, jb, phrase)
                        else j.findBreaks(jt, start, end, jb, phrase) }
                    val actual = runCatching { if (direct) k.divideUpDictionaryRange(kt, start, end, kb, phrase)
                        else k.findBreaks(kt, start, end, kb, phrase) }
                    val context = "source=$source $start..$end korean=$korean phrase=$phrase direct=$direct"
                    if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow(), context)
                    else {
                        assertTrue(expected.exceptionOrNull() is IndexOutOfBoundsException || expected.exceptionOrNull() is IllegalArgumentException, context)
                        assertTrue(actual.exceptionOrNull() is IndexOutOfBoundsException || actual.exceptionOrNull() is IllegalArgumentException, context)
                    }
                    assertEquals(jt.index, kt.getIndex(), context)
                    assertEquals(jb.size(), kb.size(), context)
                    for (i in 0 until jb.size()) assertEquals(jb.elementAt(i), kb.elementAt(i), context)
                }
            }
        }
    }
}
