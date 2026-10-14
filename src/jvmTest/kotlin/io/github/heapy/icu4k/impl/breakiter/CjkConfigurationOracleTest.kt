package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class CjkConfigurationOracleTest {
    @Test fun runtimeConfigurationAndConstructorStateMatchOriginal() {
        val key = "com.ibm.icu.impl.breakiter.useMLPhraseBreaking"
        val saved = System.getProperty(key)
        try {
            for (initial in listOf("false", "true", "TRUE", " true ", "")) for (korean in listOf(false, true)) {
                System.setProperty(key, initial)
                val j = com.ibm.icu.impl.breakiter.CjkBreakEngine(korean)
                val k = CjkBreakEngine(korean)
                for (current in listOf("false", "true", "TRUE", " true ", "")) {
                    System.setProperty(key, current)
                    for (text in listOf("", "日本語の文章です", "東京都に行きます", "ｶﾞｯｺｳ日本語", "한국어단어분리")) for (phrase in listOf(false, true)) {
                        val ji = java.text.StringCharacterIterator(text); val ki = StringCharacterIterator(text)
                        val jb = com.ibm.icu.impl.breakiter.DictionaryBreakEngine.DequeI(); val kb = DictionaryBreakEngine.DequeI()
                        fun result(action: () -> Int): Any = try { action() } catch (ex: RuntimeException) { ex.javaClass.simpleName }
                        val context = "$initial->$current korean=$korean phrase=$phrase $text"
                        assertEquals(result { j.divideUpDictionaryRange(ji, 0, text.length, jb, phrase) },
                            result { k.divideUpDictionaryRange(ki, 0, text.length, kb, phrase) }, context)
                        assertEquals(ji.index, ki.getIndex(), context)
                        assertEquals((0 until jb.size()).map { jb.elementAt(it) }, (0 until kb.size()).map { kb.elementAt(it) }, context)
                    }
                }
            }
        } finally { if (saved == null) System.clearProperty(key) else System.setProperty(key, saved) }
    }
    @Test fun freshDefaultRegistryUsesTheConfiguredEngine() {
        val key = "com.ibm.icu.impl.breakiter.useMLPhraseBreaking"
        val saved = System.getProperty(key)
        System.setProperty(key, "true")
        try {
            val engine = LanguageBreakEngineRegistry().get('日'.code) as CjkBreakEngine
            val source = "東京都に行きます"
            val original = com.ibm.icu.impl.breakiter.CjkBreakEngine(false)
            val jb = com.ibm.icu.impl.breakiter.DictionaryBreakEngine.DequeI(); val kb = DictionaryBreakEngine.DequeI()
            assertEquals(original.divideUpDictionaryRange(java.text.StringCharacterIterator(source), 0, source.length, jb, true),
                engine.divideUpDictionaryRange(StringCharacterIterator(source), 0, source.length, kb, true))
            assertEquals((0 until jb.size()).map { jb.elementAt(it) }, (0 until kb.size()).map { kb.elementAt(it) })
        } finally { if (saved == null) System.clearProperty(key) else System.setProperty(key, saved) }
    }
}
