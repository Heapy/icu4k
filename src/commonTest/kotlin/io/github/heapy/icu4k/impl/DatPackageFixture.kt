package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

/** CmnD writer with real offset tables and aligned payloads for package differential tests. */
internal object DatPackageFixture {
    fun write(items: Map<String, ByteArray>, little: Boolean = false, packageName: String = "icudt78b"): ByteArray {
        val keys = items.keys.sorted()
        val header = ICUBinary.writeHeader(0x436d6e44, 0x01000000, 0x4e000000)
        if (little) {
            header[0] = 32; header[1] = 0; header[4] = 20; header[5] = 0; header[8] = 0
        }
        val data = BinaryBuffer(ByteArray(65536), if (little) BinaryBuffer.ByteOrder.LITTLE_ENDIAN else BinaryBuffer.ByteOrder.BIG_ENDIAN)
        header.copyInto(data.bytes)
        val base = 32
        data.position = base
        data.writeInt(keys.size)
        repeat(keys.size * 2) { data.writeInt(0) }
        val nameOffsets = keys.map { key ->
            val start = data.position - base
            for (c in "$packageName/$key") data.writeByte(c.code)
            data.writeByte(0)
            start
        }
        val dataOffsets = keys.map { key ->
            while (data.position % 16 != 0) data.writeByte(0)
            val start = data.position - base
            for (byte in items.getValue(key)) data.writeByte(byte.toInt())
            start
        }
        while (data.position % 16 != 0) data.writeByte(0)
        val end = data.position
        data.position = base + 4
        keys.indices.forEach { i -> data.writeInt(nameOffsets[i]); data.writeInt(dataOffsets[i]) }
        return data.bytes.copyOf(end)
    }
}
