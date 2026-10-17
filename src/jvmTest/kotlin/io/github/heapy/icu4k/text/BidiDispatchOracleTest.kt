// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class BidiDispatchOracleTest {
    private fun outcome(action: () -> Any?): String = try { "ok:${action()}" } catch (e: Throwable) {
        when (e) {
            is IndexOutOfBoundsException -> "bounds"
            is NullPointerException -> "null"
            is IllegalArgumentException -> "argument:${e.message}"
            is IllegalStateException -> "state:${e.message}"
            is InternalError -> "internal:${e.message}"
            else -> throw e
        }
    }
    @Test fun publicVirtualDispatchAndReentrantClassifierChangesMatchSource() {
        for (mode in 0..6) for (change in 0..3) {
            val st = mutableListOf<String>(); val pt = mutableListOf<String>()
            val s = object : com.ibm.icu.text.Bidi() {
                override fun setPara(text: String?, level: Byte, embeddings: ByteArray?) { st += "string:$text:$level"; super.setPara(text, level, embeddings) }
                override fun setPara(text: CharArray?, level: Byte, embeddings: ByteArray?) { st += "chars:${text?.concatToString()}:$level"; super.setPara(text, level, embeddings) }
                override fun countRuns(): Int { st += "count"; return super.countRuns() }
                override fun getVisualRun(index: Int): com.ibm.icu.text.BidiRun { st += "visual:$index"; return super.getVisualRun(index) }
                override fun getLevels(): ByteArray { st += "levels"; return super.getLevels() }
                override fun getVisualMap(): IntArray { st += "map"; return super.getVisualMap() }
                override fun isInverse(): Boolean { st += "inverse"; return super.isInverse() }
                override fun getCustomizedClass(c: Int): Int {
                    st += "class:$c"
                    if (c == 'x'.code) when (change) { 1 -> setContext("b", "٣"); 2 -> setReorderingMode(3); 3 -> throw IllegalStateException("callback") }
                    return super.getCustomizedClass(c)
                }
            }
            val p = object : Bidi() {
                override fun setPara(text: String?, paraLevel: Byte, embeddingLevels: ByteArray?) { pt += "string:$text:$paraLevel"; super.setPara(text, paraLevel, embeddingLevels) }
                override fun setPara(chars: CharArray?, paraLevel: Byte, embeddingLevels: ByteArray?) { pt += "chars:${chars?.concatToString()}:$paraLevel"; super.setPara(chars, paraLevel, embeddingLevels) }
                override fun countRuns(): Int { pt += "count"; return super.countRuns() }
                override fun getVisualRun(index: Int): BidiRun { pt += "visual:$index"; return super.getVisualRun(index) }
                override fun getLevels(): ByteArray { pt += "levels"; return super.getLevels() }
                override fun getVisualMap(): IntArray { pt += "map"; return super.getVisualMap() }
                override fun isInverse(): Boolean { pt += "inverse"; return super.isInverse() }
                override fun getCustomizedClass(c: Int): Int {
                    pt += "class:$c"
                    if (c == 'x'.code) when (change) { 1 -> setContext("b", "٣"); 2 -> setReorderingMode(3); 3 -> throw IllegalStateException("callback") }
                    return super.getCustomizedClass(c)
                }
            }
            s.setReorderingMode(mode); p.setReorderingMode(mode); s.setReorderingOptions(1); p.setReorderingOptions(1)
            assertEquals(outcome { s.setPara("a(אב)x 12", 0, null) }, outcome { p.setPara("a(אב)x 12", 0, null) })
            assertEquals(st, pt, "setPara mode=$mode change=$change")
            st.clear(); pt.clear()
            for (o in listOf(0, 4, 16, 31)) assertEquals(outcome { s.writeReordered(o) }, outcome { p.writeReordered(o) })
            assertEquals(st, pt, "writer mode=$mode change=$change")
        }
    }
    @Test fun customClassifierSentinelAndNegativeDirectionFailuresMatchSource() {
        for (direction in listOf(-257, -129, -1, 0, 1, 13, 22, 23, 24, Int.MAX_VALUE)) {
            val s = com.ibm.icu.text.Bidi(); val p = Bidi()
            s.customClassifier = object : com.ibm.icu.text.BidiClassifier(null) { override fun classify(c: Int) = if (c == 'x'.code) direction else super.classify(c) }
            p.setCustomClassifier(object : BidiClassifier(null) { override fun classify(c: Int) = if (c == 'x'.code) direction else super.classify(c) })
            assertEquals(outcome { s.setPara("xא", 0, null) }, outcome { p.setPara("xא", 0, null) })
            assertEquals(outcome { s.writeReordered(2) }, outcome { p.writeReordered(2) })
        }
    }
}
