package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.impl.breakiter.LSTMBreakEngine.LSTMData as J
import com.ibm.icu.util.UResourceBundle
import com.ibm.icu.util.ULocale
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import kotlin.random.Random
import kotlin.test.*

class LSTMDataOracleTest {
    private class Bundle(private val value: Any) : UResourceBundle() {
        override fun getULocale(): ULocale = ULocale.ROOT
        override fun getLocaleID(): String = "root"
        override fun getBaseName(): String = "test"
        override fun getParent(): UResourceBundle? = null
        override fun get(key: String): UResourceBundle = Bundle((value as Map<*, *>)[key]!!)
        override fun getInt(): Int = value as Int
        override fun getString(): String = value as String
        @Suppress("UNCHECKED_CAST") override fun getStringArray(): Array<String> = value as Array<String>
        override fun getIntVector(): IntArray = value as IntArray
    }
    @Test fun completeMatrixLayoutAndRawFloatValuesMatchOriginal() {
        val random = Random(451905)
        repeat(100) {
            val embeddings = random.nextInt(1, 7); val hunits = random.nextInt(1, 7)
            val dictionary = arrayOf("ก", "ข", "😀", "ก")
            val data = IntArray(lstmDataSize(embeddings, hunits, dictionary.size)) { random.nextInt() }
            val type = listOf("codepoints", "graphclust", "other").random(random)
            val j = J(Bundle(mapOf("embeddings" to embeddings, "hunits" to hunits, "model" to "test-model",
                "type" to type, "dict" to dictionary, "data" to data)))
            val k = LSTMBreakEngine.createData(ICUResourceBundleReader(lstmFixture(embeddings, hunits, dictionary, data, type)))
            assertEquals(j.fName, k.fName); assertEquals(j.fType.name, k.fType.name); assertEquals(j.fDict, k.fDict)
            for ((a, b) in listOf(j.fEmbedding to k.fEmbedding, j.fForwardW to k.fForwardW, j.fForwardU to k.fForwardU,
                j.fBackwardW to k.fBackwardW, j.fBackwardU to k.fBackwardU, j.fOutputW to k.fOutputW)) {
                assertEquals(a.size, b.size)
                for (i in a.indices) assertContentEquals(a[i].map { it.toRawBits() }, b[i].map { it.toRawBits() })
            }
            for ((a, b) in listOf(j.fForwardB to k.fForwardB, j.fBackwardB to k.fBackwardB, j.fOutputB to k.fOutputB)) {
                assertContentEquals(a.map { it.toRawBits() }, b.map { it.toRawBits() })
            }
        }
    }
}
