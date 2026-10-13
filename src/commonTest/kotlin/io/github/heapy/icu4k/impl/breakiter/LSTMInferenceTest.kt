package io.github.heapy.icu4k.impl.breakiter

import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.StringCharacterIterator
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class LSTMInferenceTest {
    @Test fun bothVectorizersEmitSingleClassBoundaries() {
        val dictionary = arrayOf("ก", "ข", "ก้", "😀")
        for (type in listOf("codepoints", "graphclust")) {
            val payload = IntArray(lstmDataSize(2, 2, dictionary.size))
            payload[payload.lastIndex] = 1.0f.toRawBits() // SINGLE wins in output bias.
            val data = LSTMBreakEngine.createData(ICUResourceBundleReader(lstmFixture(2, 2, dictionary, payload, type)))
            val engine = LSTMBreakEngine(UScript.THAI, UnicodeSet(0, 0x10ffff), data)
            val source = "ก้ข😀ก"
            val text = StringCharacterIterator(source)
            val breaks = DictionaryBreakEngine.DequeI()
            val count = engine.divideUpDictionaryRange(text, 0, source.length, breaks, false)
            val expected = if (type == "codepoints") listOf(1, 2, 3, 4, 5) else listOf(2, 3, 5)
            assertEquals(expected.size, count)
            assertEquals(expected, (0 until breaks.size()).map { breaks.elementAt(it) })
        }
    }
    @Test fun outputDotProductRoundsMultiplicationBeforeBiasAddition() {
        val dictionary = arrayOf("ก")
        val payload = IntArray(lstmDataSize(1, 1, dictionary.size))
        // Saturated gates keep the forward hidden value at Float32(tanh(1)).
        for ((offset, value) in listOf(10 to 1000f, 11 to -1000f, 12 to 1000f, 13 to 1000f)) {
            payload[offset] = value.toRawBits()
        }
        val hidden = Float.fromBits(kotlin.math.tanh(1.0).toFloat().toRawBits())
        val weight = Float.fromBits(0x3f7ffffe)
        val roundedProduct = Float.fromBits((hidden * weight).toRawBits())
        payload[29] = weight.toRawBits()
        payload[34] = (-1f).toRawBits() // BEGIN
        payload[36] = (-1f).toRawBits() // END
        payload[37] = (-roundedProduct).toRawBits() // SINGLE ties INSIDE only with binary32 multiplication.
        val data = LSTMBreakEngine.createData(ICUResourceBundleReader(lstmFixture(1, 1, dictionary, payload, "codepoints")))
        val engine = LSTMBreakEngine(UScript.THAI, UnicodeSet(0, 0x10ffff), data)
        val breaks = DictionaryBreakEngine.DequeI()
        assertEquals(0, engine.divideUpDictionaryRange(StringCharacterIterator("กกกกกก"), 0, 6, breaks, false))
        assertTrue(breaks.isEmpty())
    }

}
