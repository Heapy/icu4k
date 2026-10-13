package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.text.RuleBasedBreakIterator as J
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.impl.RBBIRuleEngine
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.random.Random
import kotlin.test.*

class RBBIBreakCacheOracleTest {
    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(target)
    private fun call(target: Any, name: String, vararg args: Any): Any? = target.javaClass.declaredMethods.single {
        it.name == name && it.parameterCount == args.size
    }.also { it.isAccessible = true }.invoke(target, *args)
    private fun cacheState(j: Any, k: RBBIBreakCache) {
        assertEquals(field(j, "fStartBufIdx"), k.startBufIdx)
        assertEquals(field(j, "fEndBufIdx"), k.endBufIdx)
        assertEquals(field(j, "fTextIdx"), k.textIdx)
        assertEquals(field(j, "fBufIdx"), k.bufIdx)
        assertContentEquals(field(j, "fBoundaries") as IntArray, k.boundaries)
        assertContentEquals(field(j, "fStatuses") as ShortArray, k.statuses)
    }
    @Test fun completeCacheNavigationRefillAndWrapMatchOriginal() {
        val random = Random(551092)
        val cj = CjkBreakEngine(false); val ko = CjkBreakEngine(true)
        val lao = LaoBreakEngine(); val khmer = KhmerBreakEngine(); val fallback = UnhandledBreakEngine()
        val resolver: (Int) -> LanguageBreakEngine = { cp -> when (UCharacter.getIntPropertyValue(cp, UProperty.SCRIPT)) {
            UScript.HAN, UScript.HIRAGANA, UScript.KATAKANA -> cj
            UScript.HANGUL -> ko
            UScript.LAO -> lao
            UScript.KHMER -> khmer
            else -> { fallback.handleChar(cp); fallback }
        } }
        val sources = listOf("", "A", "Hello, world! One two.\nNext line.", "a ".repeat(300),
            "日本語の文章です。東京都に行きます！".repeat(20), "A中文한국어ພາສາລາວខ្មែរ😀B ".repeat(15),
            "A\ud800B\udc00C😀\u0301é".repeat(20))
        for (rule in listOf("char", "word", "line", "sent", "title")) for (phrase in listOf(false, true)) for (source in sources) {
            val path = "brkitr/$rule.brk"
            val j = J.getInstanceFromCompiledRules(java.nio.ByteBuffer.wrap(ICUData.getBytes(path)!!))
            j.setText(source)
            J::class.java.getDeclaredField("fPhraseBreaking").also { it.isAccessible = true }.setBoolean(j, phrase)
            val jc = field(j, "fBreakCache")!!
            val text = StringCharacterIterator(source)
            val engine = RBBIRuleEngine(RBBIDataWrapper.get(ICUBinary.getRequiredData(path)), text)
            val dictionary = RBBIDictionaryCache(text, engine.data, resolver, phrase)
            val k = RBBIBreakCache(engine, dictionary)
            fun state() {
                cacheState(jc, k)
                assertEquals(field(j, "fPosition"), engine.position, "$rule phrase=$phrase")
                assertEquals(field(j, "fRuleStatusIndex"), engine.ruleStatusIndex)
                assertEquals(field(j, "fDone"), engine.done)
                assertEquals(field(j, "fDictionaryCharCount"), engine.dictionaryCharCount)
                assertEquals(j.text.index, text.getIndex())
            }
            state()
            repeat(source.length + 4) { call(jc, "next"); k.next(); state() }
            repeat(source.length + 4) { call(jc, "previous"); k.previous(); state() }
            repeat(120) {
                val raw = random.nextInt(source.length + 1)
                val pos = if (raw > 0 && raw < source.length && source[raw].code in 0xdc00..0xdfff && source[raw - 1].code in 0xd800..0xdbff) raw - 1 else raw
                when (random.nextInt(5)) {
                    0 -> { call(jc, "following", pos); k.following(pos) }
                    1 -> { call(jc, "preceding", pos); k.preceding(pos) }
                    2 -> { assertEquals(call(jc, "seek", pos), k.seek(pos)); call(jc, "current"); k.current() }
                    3 -> { call(jc, "next"); k.next() }
                    else -> { call(jc, "previous"); k.previous() }
                }
                state()
            }
            val copiedJ = field(j.clone(), "fBreakCache")!!
            val copiedEngine = RBBIRuleEngine(engine.data, text.clone())
            val copiedDictionary = dictionary.copyFor(copiedEngine.text, resolver)
            val copiedK = k.copyFor(copiedEngine, copiedDictionary)
            cacheState(copiedJ, copiedK)
            call(jc, "reset"); k.reset(); state()
            cacheState(copiedJ, copiedK)
        }
    }
    @Test fun directCircularInsertionEvictionAndRetainedPositionMatchOriginal() {
        val path = "brkitr/char.brk"
        val j = J.getInstanceFromCompiledRules(java.nio.ByteBuffer.wrap(ICUData.getBytes(path)!!))
        val jc = field(j, "fBreakCache")!!
        val text = StringCharacterIterator("a".repeat(2000))
        val engine = RBBIRuleEngine(RBBIDataWrapper.get(ICUBinary.getRequiredData(path)), text)
        val k = RBBIBreakCache(engine, RBBIDictionaryCache(text, engine.data, { null }, false))
        call(jc, "reset", 1000, 123); k.reset(1000, 123)
        for (pos in 1001..1400) { call(jc, "addFollowing", pos, pos % 32768, true); k.addFollowing(pos, pos % 32768, true); cacheState(jc, k) }
        for (pos in 999 downTo 600) {
            assertEquals(call(jc, "addPreceding", pos, pos % 32768, true), k.addPreceding(pos, pos % 32768, true))
            cacheState(jc, k)
        }
        call(jc, "reset", 1000, 0); k.reset(1000, 0)
        for (pos in 999 downTo 850) {
            assertEquals(call(jc, "addPreceding", pos, 5, false), k.addPreceding(pos, 5, false))
            cacheState(jc, k)
        }
    }
}
