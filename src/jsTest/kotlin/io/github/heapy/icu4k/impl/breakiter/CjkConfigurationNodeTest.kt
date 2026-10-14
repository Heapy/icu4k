package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class CjkConfigurationNodeTest {
    @Test fun nodeEnvironmentControlsDefaultRegistryAndRuntimeSelection() {
        val environment: dynamic = js("process.env")
        val key = "com.ibm.icu.impl.breakiter.useMLPhraseBreaking"
        val saved: dynamic = environment[key]
        try {
            environment[key] = "true"
            val configured = LanguageBreakEngineRegistry().get('日'.code) as CjkBreakEngine
            val explicit = CjkBreakEngine(false, true)
            val text = "東京都に行きます"
            fun breaks(engine: CjkBreakEngine): List<Int> {
                val output = DictionaryBreakEngine.DequeI()
                engine.divideUpDictionaryRange(StringCharacterIterator(text), 0, text.length, output, true)
                return (0 until output.size()).map { output.elementAt(it) }
            }
            assertEquals(breaks(explicit), breaks(configured))
            environment[key] = "false"
            val old = CjkBreakEngine(false)
            environment[key] = "true"
            assertFailsWith<NullPointerException> { breaks(old) }
        } finally {
            if (jsTypeOf(saved) == "undefined") js("delete environment[key]") else environment[key] = saved
        }
    }
}
