package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import kotlin.test.*

class NameDataReaderTest {
    @Test fun embeddedTablesAndAlgorithmicNames() {
        val data = UCharacterName.INSTANCE
        assertTrue(data.tokenTable.isNotEmpty())
        assertTrue(data.groupInfo.isNotEmpty())
        assertEquals(data.m_groupcount_ * data.m_groupsize_, data.groupInfo.size)
        for ((cp, expected) in listOf(0x4e00 to "CJK UNIFIED IDEOGRAPH-4E00", 0xac00 to "HANGUL SYLLABLE GA", 0xac01 to "HANGUL SYLLABLE GAG")) {
            val index = (0 until data.getAlgorithmLength()).first { cp in data.getAlgorithmStart(it)..data.getAlgorithmEnd(it) }
            assertEquals(expected, data.getAlgorithmName(index, cp))
        }
        val copy = ICUData.getBytes("unames.icu")!!
        copy[0] = (copy[0].toInt() xor 1).toByte()
        assertNotEquals(copy[0], ICUData.getBytes("unames.icu")!![0])
    }
    @Test fun invalidHeadersAndTruncation() {
        val bytes = ICUData.getBytes("unames.icu")!!
        assertFailsWith<ICUUncheckedIOException> { UCharacterName(BinaryBuffer(bytes.copyOf(10))) }
        val bad = bytes.copyOf(); bad[16] = 2
        assertFailsWith<ICUUncheckedIOException> { UCharacterName(BinaryBuffer(bad)) }
        assertFailsWith<ICUUncheckedIOException> { UCharacterName(BinaryBuffer(bytes.copyOf(bytes.size / 2))) }
    }
}
