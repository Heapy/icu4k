package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.impl.breakiter.LSTMBreakEngine as J
import com.ibm.icu.impl.breakiter.DictionaryBreakEngine as JD
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.text.StringCharacterIterator
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.random.Random
import kotlin.test.*

class LSTMInferenceOracleTest {
    @Test fun recurrentComputeVectorizationAndBiesBoundariesMatchOriginal() {
        val random = Random(781092)
        val dictionary = arrayOf("ก", "ข", "ค", "ก้", "A", "😀", "\ud83d", "\ude00", "က", "ခ")
        val sources = listOf("", "ก", "กขคกขค", "ก้ขคก้ขค", "A😀กข😀ค", "ก\u0301ข\u0308คก", "ကခကခကခ", "A\uffffกขค", "\ud800ก\udc00ขคก", "👩‍👩‍👧‍👦กขค")
        for (type in listOf("codepoints", "graphclust")) repeat(20) {
            val embeddings = random.nextInt(1, 5); val units = random.nextInt(1, 5)
            val payload = IntArray(lstmDataSize(embeddings, units, dictionary.size)) {
                (random.nextDouble(-2.0, 2.0).toFloat()).toRawBits()
            }
            val jdata = J.LSTMData(LSTMDataOracleTest.Bundle(mapOf("embeddings" to embeddings, "hunits" to units,
                "model" to "test-model", "type" to type, "dict" to dictionary, "data" to payload)))
            val kdata = LSTMBreakEngine.createData(ICUResourceBundleReader(lstmFixture(embeddings, units, dictionary, payload, type)))
            val j = J(UScript.THAI, com.ibm.icu.text.UnicodeSet(0, 0x10ffff), jdata)
            val k = LSTMBreakEngine(UScript.THAI, UnicodeSet(0, 0x10ffff), kdata)
            val jm = J::class.java.declaredMethods.single { it.name == "compute" }.also { it.isAccessible = true }
            val km = LSTMBreakEngine::class.java.declaredMethods.single { it.name == "compute" }.also { it.isAccessible = true }
            var jh = FloatArray(units); var kh = FloatArray(units)
            val jc = FloatArray(units); val kc = FloatArray(units)
            repeat(20) {
                val index = random.nextInt(dictionary.size + 1)
                jh = jm.invoke(j, jdata.fForwardW, jdata.fForwardU, jdata.fForwardB, jdata.fEmbedding[index], jh, jc) as FloatArray
                kh = km.invoke(k, kdata.fForwardW, kdata.fForwardU, kdata.fForwardB, kdata.fEmbedding[index], kh, kc) as FloatArray
                assertContentEquals(jh.map { it.toRawBits() }, kh.map { it.toRawBits() })
                assertContentEquals(jc.map { it.toRawBits() }, kc.map { it.toRawBits() })
            }
            val jvectorizer = J::class.java.getDeclaredField("fVectorizer").also { it.isAccessible = true }.get(j)
            val jv = jvectorizer.javaClass.declaredMethods.single { it.name == "vectorize" }.also { it.isAccessible = true }
            val kv = LSTMBreakEngine::class.java.declaredMethods.single { it.name == "vectorize" }.also { it.isAccessible = true }
            for (source in sources) for (start in 0..source.length) {
                val end = random.nextInt(start, source.length + 1)
                val jt = java.text.StringCharacterIterator(source, start); val kt = StringCharacterIterator(source, start)
                val jo = ArrayList<Int>(); val ko = ArrayList<Int>(); val ji = ArrayList<Int>(); val ki = ArrayList<Int>()
                jv.invoke(jvectorizer, jt, start, end, jo, ji); kv.invoke(k, kt, start, end, ko, ki)
                assertEquals(jo, ko, "$type $source $start..$end"); assertEquals(ji, ki)
                assertEquals(jt.index, kt.getIndex())
                for (phrase in listOf(false, true)) for (direct in listOf(false, true)) {
                    jt.setIndex(start); kt.setIndex(start)
                    val jb = JD.DequeI(); val kb = DictionaryBreakEngine.DequeI(); jb.push(-1); kb.push(-1)
                    val expected = if (direct) j.divideUpDictionaryRange(jt, start, end, jb, phrase) else j.findBreaks(jt, start, end, jb, phrase)
                    val actual = if (direct) k.divideUpDictionaryRange(kt, start, end, kb, phrase) else k.findBreaks(kt, start, end, kb, phrase)
                    assertEquals(expected, actual, "$type $source $start..$end")
                    assertEquals(jt.index, kt.getIndex())
                    assertEquals(jb.size(), kb.size())
                    for (i in 0 until jb.size()) assertEquals(jb.elementAt(i), kb.elementAt(i))
                }
            }
            for (cp in listOf(0x0e01, 0x1000, 65, 0x1f600)) assertEquals(j.handles(cp), k.handles(cp))
        }
    }
}
