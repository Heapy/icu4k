package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.StringCharacterIterator
import kotlin.test.*

class LSTMDefaultModelTest {
    @Test fun pinnedTrainedModelsLoadAndSegmentOnEveryTarget() {
        for ((script, source) in listOf(UScript.THAI to "ภาษาไทยภาษาไทยภาษาไทย",
            UScript.MYANMAR to "မြန်မာဘာသာစကားမြန်မာဘာသာစကား")) {
            val data = assertNotNull(LSTMBreakEngine.createData(script))
            assertEquals(LSTMBreakEngine.EmbeddingType.GRAPHEME_CLUSTER, data.fType)
            assertTrue(data.fDict.isNotEmpty())
            val engine = LSTMBreakEngine.create(script, data)
            val cursor = StringCharacterIterator(source)
            val breaks = DictionaryBreakEngine.DequeI()
            val count = engine.findBreaks(cursor, 0, source.length, breaks, false)
            assertEquals(source.length, cursor.getIndex()); assertEquals(count, breaks.size())
            assertTrue(count > 0)
            for (i in 0 until count) assertTrue(breaks.elementAt(i) in 1 until source.length)
        }
        assertNull(LSTMBreakEngine.createData(UScript.LATIN))
    }
}
