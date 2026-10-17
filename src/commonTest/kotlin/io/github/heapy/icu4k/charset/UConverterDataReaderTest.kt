package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.InvalidFormatException
import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class UConverterDataReaderTest {
    @Test fun all190ConvertersMatchIndependentSourceGoldens() {
        assertEquals(190, converterDataGoldenRows.size)
        for (row in converterDataGoldenRows) {
            val name = row.substringBefore('|')
            val bytes = ICUBinary.getRequiredData(name)
            assertEquals(row, converterDataFingerprint(name, bytes), name)
        }
    }
    @Test fun headerVersionsSignedNamesAndEndianHandling() {
        for (order in BinaryBuffer.ByteOrder.entries) {
            for ((major, minor, options) in listOf(Triple(4, 3, 0), Triple(5, 2, 0), Triple(5, 3, 0), Triple(5, 3, 0x40))) {
                val buffer = synthetic(order, major, minor, options)
                val reader = UConverterDataReader(buffer)
                assertEquals(order, buffer.order)
                assertEquals(32, buffer.position)
                assertTrue(reader.dataFormatHasUnicodeMask())
                val data = UConverterStaticData(); reader.readStaticData(data)
                assertEquals(100, data.structSize)
                assertEquals(60, data.name.length)
                assertEquals('\ufffd', data.name[0])
                assertEquals('\u0000', data.name[1])
                assertEquals(255, data.unicodeMask.toInt())
                val header = MBCSHeader(); reader.readMBCSHeader(header)
                assertEquals(if (major == 5 && minor >= 3) options else 0, header.options)
                assertEquals(if (options == 0x40) 123 else 0, header.fullStage2Length)
                assertEquals(if (major == 5 && minor >= 3) if (options == 0x40) 40 else 36 else 32, reader.bytesReadAfterStaticData())
            }
        }
        val bytes = synthetic(BinaryBuffer.ByteOrder.BIG_ENDIAN, 4, 0, 0)
        bytes.position = 132
        bytes.writeByte(0xff); bytes.writeByte(0)
        bytes.position = 0
        val reader = UConverterDataReader(bytes); reader.readStaticData(UConverterStaticData())
        assertEquals("\uffff", reader.readBaseTableName())
    }
    @Test fun extensionViewsValidationAndNonpositiveSkips() {
        for (order in BinaryBuffer.ByteOrder.entries) {
            val bytes = synthetic(order, 4, 0, 0)
            bytes.position = 132
            bytes.writeInt(32)
            bytes.position = 132 + 31 * 4; bytes.writeInt(128)
            bytes.position = 0
            val reader = UConverterDataReader(bytes); reader.readStaticData(UConverterStaticData())
            val extension = reader.readExtIndexes(-7)
            assertEquals(128, extension.limit)
            assertEquals(0, extension.position)
            assertEquals(128, reader.bytesReadAfterStaticData())
            bytes.position = 132; bytes.writeInt(42)
            assertEquals(42, extension.readInt())
            bytes.position = 0
            val invalid = UConverterDataReader(bytes); invalid.readStaticData(UConverterStaticData())
            bytes.position = 132; bytes.writeInt(31); bytes.position = 132
            assertFailsWith<InvalidFormatException> { invalid.readExtIndexes(0) }
        }
    }
    @Test fun omittedEncoderDataKeepsLiveIndexViewAndReaderPosition() {
        for (order in BinaryBuffer.ByteOrder.entries) {
            val bytes = synthetic(order, 5, 3, 0x40)
            bytes.position = 0
            val reader = UConverterDataReader(bytes); reader.readStaticData(UConverterStaticData())
            val header = MBCSHeader(); reader.readMBCSHeader(header)
            val before = bytes.position
            // An empty table leaves the following units as the optional index.
            header.countStates = 0; header.countToUFallbacks = 0
            header.offsetToUCodeUnits = 0; header.offsetFromUTable = 0; header.offsetFromUBytes = 0
            val table = UConverterMBCSTable(); reader.readMBCSTable(header, table)
            assertEquals(before, bytes.position)
            val index = assertNotNull(table.mbcsIndex)
            assertEquals(0, index.position)
            bytes.writeShort(0x1234)
            assertEquals(0x1234, index.readUnsignedShort())
            assertNull(table.fromUnicodeChars)
        }
    }
    @Test fun invalidHeadersAndTruncationFail() {
        val valid = synthetic(BinaryBuffer.ByteOrder.BIG_ENDIAN, 4, 0, 0).bytes
        for (count in listOf(0, 1, 8, 16, 20, 31)) assertFails { UConverterDataReader(BinaryBuffer(valid.copyOf(count))) }
        for ((index, value) in listOf(2 to 0, 12 to 0, 16 to 5, 10 to 4)) {
            val corrupt = valid.copyOf(); corrupt[index] = value.toByte()
            assertFails { UConverterDataReader(BinaryBuffer(corrupt)) }
        }
        val truncated = UConverterDataReader(BinaryBuffer(valid.copyOf(131)))
        assertFails { truncated.readStaticData(UConverterStaticData()) }
    }

    private fun synthetic(order: BinaryBuffer.ByteOrder, major: Int, minor: Int, options: Int): BinaryBuffer {
        val buffer = BinaryBuffer(ByteArray(512))
        ICUBinary.writeHeader(0x636e7674, 0x06010000, 0).copyInto(buffer.bytes)
        if (order == BinaryBuffer.ByteOrder.LITTLE_ENDIAN) {
            for (start in listOf(0, 4, 6)) {
                val b = buffer.bytes[start]; buffer.bytes[start] = buffer.bytes[start + 1]; buffer.bytes[start + 1] = b
            }
            buffer.bytes[8] = 0
        }
        buffer.order = order; buffer.position = 32
        buffer.writeInt(100); buffer.writeByte(0xff)
        buffer.position = 32 + 79; buffer.writeByte(255)
        buffer.position = 132
        buffer.writeByte(major); buffer.writeByte(minor); buffer.writeShort(0)
        repeat(7) { buffer.writeInt(0) }
        if (major == 5 && minor >= 3) {
            buffer.writeInt(options)
            if ((options and 0x40) != 0) buffer.writeInt(123)
        }
        buffer.position = 0
        return buffer
    }
}

internal fun converterDataFingerprint(name: String, bytes: BinaryBuffer): String {
    val reader = UConverterDataReader(bytes)
    val data = UConverterStaticData(); reader.readStaticData(data)
    val header = MBCSHeader(); reader.readMBCSHeader(header)
    val values = mutableListOf(name, reader.dataFormatHasUnicodeMask().toString(), bytes.position.toString(), data.name.hashCode().toString())
    values += listOf(data.structSize, data.codepage, data.platform.toInt(), data.conversionType.toInt(), data.minBytesPerChar.toInt(), data.maxBytesPerChar.toInt(), data.subCharLen.toInt(), data.hasToUnicodeFallback.toInt(), data.hasFromUnicodeFallback.toInt(), data.unicodeMask.toInt(), data.subChar1.toInt()).map { it.toString() }
    values += listOf(data.subChar.contentHashCode(), data.reserved.contentHashCode(), header.version.contentHashCode()).map { it.toString() }
    values += listOf(header.countStates, header.countToUFallbacks, header.offsetToUCodeUnits, header.offsetFromUTable, header.offsetFromUBytes, header.flags, header.fromUBytesLength, header.options, header.fullStage2Length).map { it.toString() }
    val output = header.flags.toByte().toShort()
    val offset = header.flags ushr 8
    if (output == 14.toShort()) values += reader.readBaseTableName() else {
        val table = UConverterMBCSTable()
        table.outputType = output; table.unicodeMask = if (reader.dataFormatHasUnicodeMask()) (data.unicodeMask.toInt() and 3).toShort() else 3
        reader.readMBCSTable(header, table)
        values += listOf(table.countStates.toInt(), table.countToUFallbacks, table.fromUBytesLength, table.stateTable.contentDeepHashCode(), table.unicodeCodeUnits.contentHashCode(), table.fromUnicodeTable.contentHashCode(), table.fromUnicodeTableInts?.contentHashCode() ?: 0, table.fromUnicodeBytes?.contentHashCode() ?: 0, table.fromUnicodeChars?.contentHashCode() ?: 0, table.fromUnicodeInts?.contentHashCode() ?: 0, table.mbcsIndex?.let { it.slice().readChars(it.remaining / 2).contentHashCode() } ?: 0).map { it.toString() }
        var hash = 1
        for (f in table.toUFallbacks) { hash = 31 * hash + f.offset; hash = 31 * hash + f.codePoint }
        values += hash.toString()
    }
    values += reader.bytesReadAfterStaticData().toString()
    if (offset != 0) {
        val extension = reader.readExtIndexes(offset - reader.bytesReadAfterStaticData())
        values += extension.slice().readBytes(extension.remaining).contentHashCode().toString()
        values += extension.remaining.toString()
    } else values += listOf("0", "0")
    values += listOf(bytes.position.toString(), reader.bytesReadAfterStaticData().toString())
    return values.joinToString("|")
}
