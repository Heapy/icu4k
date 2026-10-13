package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.RBBIDataWrapper as JavaRules
import io.github.heapy.icu4k.util.BinaryBuffer
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import kotlin.test.*

class RBBIDataOracleTest {
    @Test fun allCompiledRulesMatchIcu() {
        val names = listOf("word", "word_POSIX", "line", "char", "line_cj", "sent", "line_normal_cj", "line_loose_phrase_cj", "line_phrase_cj", "line_normal_phrase_cj", "sent_el", "title", "line_loose", "line_loose_cj", "line_normal")
        for (name in names) {
            val path = "brkitr/$name.brk"
            val raw = ICUData.getBytes(path)!!
            val upstream = com.ibm.icu.impl.ICUData.getRequiredStream("/" + com.ibm.icu.impl.ICUData.ICU_BASE_NAME + "/" + path).use { it.readBytes() }
            assertContentEquals(upstream, raw)
            val bytes = BinaryBuffer(raw)
            val jbytes = ByteBuffer.wrap(upstream)
            val k = RBBIDataWrapper.get(bytes)
            val j = JavaRules.get(jbytes)
            assertEquals(jbytes.position(), bytes.position)
            assertEquals(j.fHeader.fCatCount, k.fHeader.fCatCount)
            assertEquals(j.fRuleSource, k.fRuleSource)
            assertContentEquals(j.fStatusTable, k.fStatusTable)
            for ((jt, kt) in listOf(j.fFTable to k.fFTable, j.fRTable to k.fRTable)) {
                if (jt == null) { assertNull(kt); continue }
                assertNotNull(kt)
                assertEquals(jt.fNumStates, kt.fNumStates); assertEquals(jt.fRowLen, kt.fRowLen)
                assertEquals(jt.fDictCategoriesStart, kt.fDictCategoriesStart)
                assertEquals(jt.fLookAheadResultsSize, kt.fLookAheadResultsSize)
                assertEquals(jt.fFlags, kt.fFlags)
                assertContentEquals(jt.fTable, kt.fTable)
                assertEquals(jt.hashCode(), kt.hashCode())
                val output = ByteArrayOutputStream()
                val written = jt.put(DataOutputStream(output))
                assertEquals(written, kt.toBinary().size)
                assertContentEquals(output.toByteArray(), kt.toBinary())
            }
            for (cp in 0..0x10ffff) assertEquals(j.fTrie.get(cp), k.fTrie.get(cp), "$name U+${cp.toString(16)}")
            for (state in 0 until j.fFTable.fNumStates) assertEquals(j.getRowIndex(state), k.getRowIndex(state))
        }
    }
}
