package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class BinaryKeyComparisonTest {
    @Test fun comparesUnitsWithoutStringConversionAndPreservesExactDifferences() {
        val bytes = byteArrayOf(99, 97, 98, 0, -128, 0)
        val buffer = BinaryBuffer(bytes).asReadOnlyBuffer()
        buffer.position = 3
        val sequence = object : CharSequence {
            override val length = 2
            override fun get(index: Int): Char = "ab"[index]
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("not needed")
            override fun toString(): String = error("comparison must not allocate a String")
        }
        for ((key, offset, expected) in listOf(
            Triple(sequence, 1, 0), Triple("a", 1, -1), Triple("abcdef", 1, 1),
            Triple("xb", 1, 23), Triple("", 4, -1), Triple("\u0080", 4, 256)
        )) {
            assertEquals(expected, ICUBinary.compareKeys(key, bytes, offset))
            assertEquals(expected, ICUBinary.compareKeys(key, buffer, offset))
        }
        assertEquals(3, buffer.position)
    }
    @Test fun resourceTablesInspectTheSuppliedSequenceDirectly() {
        val reader = ICUResourceBundleReader.getReader(ICUData.ICU_BRKITR_BASE_NAME, "root")!!
        val table = reader.getTable(reader.rootResource)!!
        val sequence = object : CharSequence by "dictionaries" {
            override fun toString(): String = error("lookup must not convert the query")
        }
        val index = table.findTableItem(sequence)
        assertTrue(index >= 0)
        val value = ICUResourceBundleReader.ReaderValue().also { it.reader = reader; it.res = reader.rootResource }
        assertTrue(value.getTable().findValue(sequence, value))
        assertEquals(table.getContainerResource(index), value.res)
    }
}
