package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.ICUResourceLoader
import kotlin.test.*

class MBCSReconstitutionTest {
    @Test fun sourceRoundtripWritesEucTransformsAndSignedLongBoundaries() {
        for (row in mbcsReconstitutionGoldenRows.filter { it.startsWith("w|") }) {
            val fields = row.split('|')
            val table = writer(fields[1].toInt())
            val codePoints = IntArray(32) { if (it % 4 == 0) -1 else it * 3 % 64 }
            assertTrue(MBCSConverterData.writeStage3Roundtrip(table, fields[2].toLong(), codePoints))
            assertEquals(fields.drop(3).joinToString("|"), hashes(table), row)
        }
    }
    @Test fun sourceReconstructionAcrossAllEncoderOutputTypes() {
        for (row in mbcsReconstitutionGoldenRows.filter { it.startsWith("r|") }) {
            val fields = row.split('|')
            val supplementary = fields[2].toBoolean()
            val firstLength = if (supplementary) 0x440 else 0x40
            val table = reconstruction(fields[1].toInt(), supplementary)
            val index = table.mbcsIndex
            MBCSConverterData.reconstituteData(table, firstLength, 64, 128)
            assertEquals(fields.drop(3).joinToString("|"), hashes(table), row)
            assertSame(index, table.mbcsIndex)
            assertEquals(0, index!!.position)
        }
    }
    @Test fun sourceV53FilesWithOmittedEncoderTablesLoadEndToEnd() {
        for (row in mbcsReconstitutionGoldenRows.filter { it.startsWith("l|") }) {
            val fields = row.split('|')
            val raw = file(fields[1].toInt(), fields[2].toBoolean())
            val loaded = MBCSConverterData.load("fixture", "custom", ICUResourceLoader { BinaryBuffer(raw.copyOf()) })
            val table = loaded.sharedData.mbcs
            assertEquals(fields.drop(3).joinToString("|"), hashes(table) + "|${table.utf8Friendly}|${table.maxFastUChar.code}|${table.asciiRoundtrips}|${table.mbcsIndex == null}", row)
        }
    }
    private fun file(output: Int, supplementary: Boolean): ByteArray {
        val table = reconstruction(output, supplementary)
        val firstLength = if (supplementary) 0x440 else 0x40
        val toUnicode = 40 + 2048
        val fromUnicode = toUnicode + 14
        val fromBytes = fromUnicode + firstLength * 2 + 256
        val bytes = BinaryBuffer(ByteArray(132 + fromBytes + 64))
        ICUBinary.writeHeader(0x636e7674, 0x06010000, 0).copyInto(bytes.bytes)
        bytes.position = 32; bytes.writeInt(100)
        bytes.position = 101; bytes.writeByte(2); bytes.writeByte(1); bytes.writeByte(4); bytes.writeByte(63)
        bytes.position = 108; bytes.writeByte(1)
        bytes.position = 111; bytes.writeByte(if (supplementary) 1 else 0)
        bytes.position = 132
        for (b in listOf(5, 3, 0x80, 0)) bytes.writeByte(b)
        for (i in listOf(2, 0, toUnicode, fromUnicode, fromBytes, output, 12288, 0x40, 128)) bytes.writeInt(i)
        for (state in table.stateTable) for (entry in state) bytes.writeInt(entry)
        for (unit in table.unicodeCodeUnits) bytes.writeShort(unit.code)
        for (unit in table.fromUnicodeTable) bytes.writeShort(unit.code)
        repeat(64) { bytes.writeInt(0) }
        val index = table.mbcsIndex!!.slice()
        while (index.remaining > 0) bytes.writeByte(index.readUnsignedByte())
        return bytes.bytes
    }
    private fun writer(output: Int) = UConverterMBCSTable().also {
        it.outputType = output.toShort()
        it.fromUnicodeTable = CharArray(64) { 32.toChar() }
        it.fromUnicodeTableInts = IntArray(96) { n -> n % 4 + 1 }
        when (output) {
            2, 9 -> it.fromUnicodeBytes = ByteArray(384)
            3 -> it.fromUnicodeInts = IntArray(128)
            else -> it.fromUnicodeChars = CharArray(128)
        }
    }
    private fun reconstruction(output: Int, supplementary: Boolean) = UConverterMBCSTable().also {
        it.outputType = output.toShort()
        it.countStates = 2
        it.maxFastUChar = 0x7ff.toChar()
        val length = if (supplementary) 0x440 else 0x40
        it.fromUnicodeTable = CharArray(length) { (length / 2).toChar() }
        it.fromUnicodeTable[0] = (length / 2 + 1).toChar()
        it.fromUnicodeTableInts = IntArray(length / 2 + 64)
        it.fromUBytesLength = 12288
        it.mbcsIndex = BinaryBuffer(ByteArray(64)).also { index ->
            repeat(32) { n -> index.writeShort(if (n < 16 && n != 2) (n + 1) * 64 else 0) }
            index.position = 0
        }
        fun end(action: Int, value: Int) = MBCSConverterData.final(0, action, value)
        val states = Array(2) { IntArray(256) { end(6, 0) } }
        states[0][0] = end(0, 0); states[0][1] = end(0, 1); states[0][32] = 1 shl 24; states[0][255] = end(0, 255)
        states[1][1] = end(0, 1); states[1][2] = end(4, 0); states[1][3] = end(4, 1)
        states[1][4] = end(5, if (supplementary) 2 else 4); states[1][5] = end(5, 4); states[1][6] = end(5, 6)
        states[1][7] = if (supplementary) end(1, 0) else end(0, 3); states[1][8] = end(0, 65535)
        it.stateTable = states
        it.unicodeCodeUnits = charArrayOf('A', 0xfffe.toChar(), 0xd800.toChar(), 0xdc00.toChar(), 0xe000.toChar(), 'B', 0xdc00.toChar())
    }
    private fun hashes(table: UConverterMBCSTable): String = listOf(table.fromUnicodeTableInts?.contentHashCode() ?: 0, table.fromUnicodeChars?.contentHashCode() ?: 0, table.fromUnicodeBytes?.contentHashCode() ?: 0, table.fromUnicodeInts?.contentHashCode() ?: 0).joinToString("|")
}
