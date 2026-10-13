package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.text.RuleBasedBreakIterator as J
import com.ibm.icu.impl.breakiter.LanguageBreakEngine as JE
import com.ibm.icu.impl.breakiter.DictionaryBreakEngine as JD
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class LanguageBreakEngineRegistryOracleTest {
    @Test fun dispatchMatchesOriginalPrivateRegistryAndSegmentation() {
        val iterator = J.getInstanceFromCompiledRules(java.nio.ByteBuffer.wrap(ICUData.getBytes("brkitr/word.brk")!!))
        val get = J::class.java.getDeclaredMethod("getLanguageBreakEngine", Int::class.javaPrimitiveType).also { it.isAccessible = true }
        val registry = LanguageBreakEngineRegistry()
        val samples = listOf(0x0e01 to "ภาษาไทยภาษาไทย", 0x1000 to "မြန်မာဘာသာစကား", 0x0e81 to "ພາສາລາວ", 0x1780 to "ភាសាខ្មែរ",
            0x4e00 to "日本語中文", 0x3042 to "ひらがな", 0x30ab to "カタカナ", 0xac00 to "한국어", 65 to "ABC", 0x3a3 to "ΣΣΣ")
        for ((cp, source) in samples) {
            val j = get.invoke(iterator, cp) as JE
            val k = assertNotNull(registry.get(cp))
            assertEquals(j.javaClass.simpleName, k.javaClass.simpleName)
            assertSame(k, registry.get(cp))
            val jt = java.text.StringCharacterIterator(source); val kt = StringCharacterIterator(source)
            val jb = JD.DequeI(); val kb = DictionaryBreakEngine.DequeI()
            assertEquals(j.findBreaks(jt, 0, source.length, jb, false), k.findBreaks(kt, 0, source.length, kb, false))
            assertEquals(jt.index, kt.getIndex()); assertEquals(jb.size(), kb.size())
            for (i in 0 until jb.size()) assertEquals(jb.elementAt(i), kb.elementAt(i))
        }
    }
    @Test fun concurrentPublicationRetainsEveryScriptAndOnePublishedEngine() {
        val registry = LanguageBreakEngineRegistry()
        val cps = listOf(65, 0x3a3, 0x621, 0x10400, 0x4e00, 0x3042, 0x30ab, 0xac00, 0x0e01, 0x1000, 0x0e81, 0x1780)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        val gate = java.util.concurrent.CyclicBarrier(8)
        try {
            val jobs = (0 until 8).map { worker -> pool.submit<List<Pair<Int, LanguageBreakEngine>>> {
                gate.await()
                (0 until 3).flatMap { cps.drop(worker % cps.size) + cps.take(worker % cps.size) }.map { cp ->
                    cp to assertNotNull(registry.get(cp))
                }
            } }
            val results = jobs.flatMap { it.get(30, java.util.concurrent.TimeUnit.SECONDS) }
            for ((cp, engine) in results) assertSame(registry.get(cp), engine)
            for (cp in cps) assertTrue(assertNotNull(registry.get(cp)).handles(cp))
            val fallback = registry.get(65)
            for (cp in listOf(0x3a3, 0x621, 0x10400)) assertSame(fallback, registry.get(cp))
        } finally { pool.shutdownNow() }
    }
}
