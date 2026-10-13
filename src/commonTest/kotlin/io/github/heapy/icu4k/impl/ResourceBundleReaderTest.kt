package io.github.heapy.icu4k.impl

import kotlin.test.*
import io.github.heapy.icu4k.util.BinaryBuffer

class ResourceBundleReaderTest {
    @Test fun originalBreakResourcesAreReadableWithoutJvm() {
        val root = ICUResourceBundleReader(ICUBinary.getRequiredData("brkitr/root.res"))
        val table = assertNotNull(root.getTable(root.rootResource))
        val dictionaries = assertNotNull(root.getTable(table.getResource("dictionaries")))
        assertEquals("burmesedict.dict", root.getString(dictionaries.getResource("Mymr")))
        assertEquals("cjdict.dict", root.getString(dictionaries.getResource("Hira")))
        val ja = ICUResourceBundleReader(ICUBinary.getRequiredData("brkitr/ja.res"))
        val jat = assertNotNull(ja.getTable(ja.rootResource))
        val extensions = assertNotNull(ja.getArray(jat.getResource("extensions")))
        assertTrue(extensions.size > 0)
        assertNotNull(ja.getString(extensions.getContainerResource(0)))
        assertNull(ja.getString(ja.rootResource))
        assertEquals(-1, ICUResourceBundleReader.RES_GET_INT(0x7fffffff))
        assertEquals(0xfffffff, ICUResourceBundleReader.RES_GET_UINT(0x7fffffff))
    }
    private fun fixture(attributes: Int = 0, poolLimit: Int = 0, build: (BinaryBuffer) -> Unit): BinaryBuffer {
        val body = BinaryBuffer(ByteArray(256))
        body.writeInt(0x40000034)
        for (value in intArrayOf(8 or (poolLimit shl 8), 12, 64, 64, 2, attributes, 32, 77)) body.writeInt(value)
        body.position = 36
        for (c in "a\u0000b\u0000") body.writeByte(c.code)
        build(body)
        return BinaryBuffer(ICUBinary.writeHeader(0x52657342, 0x03000000, 0x11000000) + body.bytes)
    }
    @Test fun decodesAllContainerWidthsAndScalarPayloads() {
        val reader = ICUResourceBundleReader(fixture { body ->
            fun units(offset: Int, vararg values: Int) {
                body.position = 48 + 2 * offset
                for (value in values) body.writeShort(value)
            }
            units(1, 'v'.code, 0)
            units(3, 0xdc03, 'x'.code, 0, 'y'.code)
            units(8, 2, 1, 3)
            units(12, 2, 36, 38, 1, 3)
            body.position = 128; body.writeInt(2); body.writeShort('o'.code); body.writeShort('k'.code)
            body.position = 140; body.writeInt(2); body.writeShort('j'.code); body.writeShort('a'.code)
            body.position = 152; body.writeInt(3); body.writeByte(1); body.writeByte(2); body.writeByte(3)
            body.position = 160; body.writeInt(2); body.writeInt(-1); body.writeInt(42)
            body.position = 176; for (v in intArrayOf(3, 32, 0x30000023, 0xe0000028.toInt())) body.writeInt(v)
            body.position = 192; body.writeShort(2); body.writeShort(36); body.writeShort(38); body.writeShort(0)
            body.writeInt(32); body.writeInt(0x60000001)
            body.position = 208; for (v in intArrayOf(2, 36, 38, 0x20000030, 0x90000008.toInt())) body.writeInt(v)
        })
        assertEquals("ok", reader.getString(32))
        assertEquals("v", reader.getString(0x60000001))
        assertEquals("x\u0000y", reader.getString(0x60000003))
        assertEquals("ja", reader.getAlias(0x30000023))
        assertContentEquals(byteArrayOf(1, 2, 3), reader.getBinary(0x10000026))
        assertContentEquals(intArrayOf(-1, 42), reader.getIntVector(0xe0000028.toInt()))
        val compact = assertNotNull(reader.getArray(0x90000008.toInt()))
        assertEquals("v", reader.getString(compact.getContainerResource(0)))
        assertEquals("x\u0000y", reader.getString(compact.getContainerResource(1)))
        assertEquals(3, assertNotNull(reader.getArray(0x8000002c.toInt())).size)
        for (handle in listOf(0x20000030, 0x5000000c, 0x40000034)) {
            val table = assertNotNull(reader.getTable(handle))
            assertEquals(2, table.size)
            assertEquals("a", table.getKey(0)); assertEquals("b", table.getKey(1))
            assertEquals(1, table.findTableItem("b"))
        }
        assertEquals(0, assertNotNull(reader.getTable(0x20000000)).size)
        assertEquals("", reader.getString(0x60000000))
        assertContentEquals(intArrayOf(), reader.getIntVector(0xe0000000.toInt()))
    }
    @Test fun poolKeysStringsAndChecksumAreResolved() {
        val pool = ICUResourceBundleReader(fixture(attributes = 2) { body ->
            body.position = 50; body.writeShort('v'.code); body.writeShort(0)
        })
        fun input() = fixture(attributes = 4 or (4 shl 16), poolLimit = 4) { body ->
            body.position = 0; body.writeInt(0x50000001)
            body.position = 8; body.writeInt(9) // No local keys; pool key offsets begin at zero.
            body.position = 28; body.writeInt(16)
            body.position = 38; body.writeShort(1); body.writeShort(0); body.writeShort(1)
        }
        val reader = ICUResourceBundleReader(input(), pool)
        val table = assertNotNull(reader.getTable(reader.rootResource))
        assertEquals("a", table.getKey(0))
        assertEquals("v", reader.getString(table.getContainerResource(0)))
        assertFailsWith<IllegalStateException> { ICUResourceBundleReader(input()) }
        val bad = input(); bad.position = 32 + 32; bad.writeInt(78); bad.position = 0
        assertFailsWith<IllegalStateException> { ICUResourceBundleReader(bad, pool) }
    }

}
