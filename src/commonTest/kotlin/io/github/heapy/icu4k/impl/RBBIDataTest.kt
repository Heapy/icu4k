package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import kotlin.test.*

class RBBIDataTest {
    @Test fun readsRealRulesOnEveryTarget() {
        for (name in listOf("word", "sent", "char", "line", "title")) {
            val rules = RBBIDataWrapper.get(ICUBinary.getRequiredData("brkitr/$name.brk"))
            assertEquals(0xb1a0, rules.fHeader.fMagic)
            assertTrue(rules.fHeader.fCatCount > 0)
            assertTrue(rules.fRuleSource.isNotEmpty())
            assertTrue(rules.fStatusTable.isNotEmpty())
            val table = assertNotNull(rules.fFTable)
            assertTrue(table.fNumStates > 0)
            assertEquals(rules.fHeader.fCatCount + 3, rules.getRowIndex(1))
            val copy = RBBIDataWrapper.RBBIStateTable.get(BinaryBuffer(table.toBinary()), table.toBinary().size)!!
            assertEquals(table, copy)
            assertEquals(table.hashCode(), copy.hashCode())
            assertTrue(rules.fTrie.get(65) < rules.fHeader.fCatCount)
        }
    }
    @Test fun rejectsUnsupportedVersionAndBadRuleMagic() {
        val raw = ICUData.getBytes("brkitr/word.brk")!!
        raw[16] = 5
        assertFailsWith<ICUUncheckedIOException> { RBBIDataWrapper.get(BinaryBuffer(raw)) }
        val raw2 = ICUData.getBytes("brkitr/word.brk")!!
        val headerSize = ((raw2[0].toInt() and 255) shl 8) or (raw2[1].toInt() and 255)
        raw2[headerSize] = 1
        assertFailsWith<ICUUncheckedIOException> { RBBIDataWrapper.get(BinaryBuffer(raw2)) }
        assertNull(RBBIDataWrapper.RBBIStateTable.get(BinaryBuffer(ByteArray(0)), 0))
        assertFailsWith<ICUUncheckedIOException> { RBBIDataWrapper.RBBIStateTable.get(BinaryBuffer(ByteArray(19)), 19) }
    }
}
