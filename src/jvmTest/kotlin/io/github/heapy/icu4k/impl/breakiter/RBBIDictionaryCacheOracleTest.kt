package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.text.RuleBasedBreakIterator as J
import com.ibm.icu.impl.breakiter.DictionaryBreakEngine as JD
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.random.Random
import kotlin.test.*

class RBBIDictionaryCacheOracleTest {
    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(target)
    private fun call(target: Any, name: String, vararg args: Any): Any? = target.javaClass.declaredMethods.single {
        it.name == name && it.parameterCount == args.size
    }.also { it.isAccessible = true }.invoke(target, *args)
    private fun state(j: Any, k: RBBIDictionaryCache) {
        assertEquals(field(j, "fPositionInCache"), k.positionInCache)
        assertEquals(field(j, "fStart"), k.start); assertEquals(field(j, "fLimit"), k.limit)
        assertEquals(field(j, "fBoundary"), k.boundary); assertEquals(field(j, "fStatusIndex"), k.statusIndex)
        val breaks = field(j, "fBreaks") as JD.DequeI
        assertEquals(breaks.size(), k.breaks.size())
        for (i in 0 until breaks.size()) assertEquals(breaks.elementAt(i), k.breaks.elementAt(i))
    }
    @Test fun populationNavigationStatusAndCloningMatchOriginal() {
        val random = Random(395821)
        val cj = CjkBreakEngine(false); val ko = CjkBreakEngine(true)
        val lao = LaoBreakEngine(); val khmer = KhmerBreakEngine(); val fallback = UnhandledBreakEngine()
        val resolver: (Int) -> LanguageBreakEngine = { cp ->
            when (UCharacter.getIntPropertyValue(cp, UProperty.SCRIPT)) {
                UScript.HAN, UScript.HIRAGANA, UScript.KATAKANA -> cj
                UScript.HANGUL -> ko
                UScript.LAO -> lao
                UScript.KHMER -> khmer
                else -> { fallback.handleChar(cp); fallback }
            }
        }
        val sources = mutableListOf("", "A", "日本語の文章です", "A東京都に行きますB", "中文分词测试", "한국어단어분리", "ພາສາລາວ", "ខ្ញុំរៀនភាសាខ្មែរ", "😀日𠀀本語!", "［携帯電話］正しい選択")
        val atoms = listOf("日本", "中文", "の", "カタカナ", "한국", "ພາສາ", "ខ្មែរ", "A", " ", "😀")
        repeat(100) { sources += buildString { repeat(random.nextInt(1, 12)) { append(atoms.random(random)) } } }
        for (rule in listOf("word", "line")) for (phrase in listOf(false, true)) for (source in sources) {
            val path = "brkitr/$rule.brk"
            val j = J.getInstanceFromCompiledRules(java.nio.ByteBuffer.wrap(ICUData.getBytes(path)!!))
            j.setText(source)
            J::class.java.getDeclaredField("fPhraseBreaking").also { it.isAccessible = true }.setBoolean(j, phrase)
            val jc = field(j, "fDictionaryCache")!!
            val kt = StringCharacterIterator(source)
            val k = RBBIDictionaryCache(kt, RBBIDataWrapper.get(ICUBinary.getRequiredData(path)), resolver, phrase)
            repeat(3) { trial ->
                val start = if (trial == 0) 0 else random.nextInt(source.length + 1)
                val end = if (trial == 0) source.length else random.nextInt(start, source.length + 1)
                call(jc, "populateDictionary", start, end, 12, 34)
                k.populateDictionary(start, end, 12, 34)
                state(jc, k)
                assertEquals(j.text.index, kt.getIndex())
                for (i in -1..source.length + 1) {
                    assertEquals(call(jc, "following", i), k.following(i)); state(jc, k)
                }
                for (i in source.length + 1 downTo -1) {
                    assertEquals(call(jc, "preceding", i), k.preceding(i)); state(jc, k)
                }
                repeat(20) {
                    val pos = random.nextInt(-1, source.length + 2)
                    val forward = random.nextBoolean()
                    assertEquals(call(jc, if (forward) "following" else "preceding", pos),
                        if (forward) k.following(pos) else k.preceding(pos))
                    state(jc, k)
                }
            }
            val copiedJ = field(j.clone(), "fDictionaryCache")!!
            val copiedK = k.copyFor(kt.clone(), resolver)
            state(copiedJ, copiedK)
            call(jc, "reset"); k.reset(); state(jc, k)
            for (i in 0..source.length) {
                assertEquals(call(copiedJ, "following", i), copiedK.following(i)); state(copiedJ, copiedK)
            }
        }
    }
}
