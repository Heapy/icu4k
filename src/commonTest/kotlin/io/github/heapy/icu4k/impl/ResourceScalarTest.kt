package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class ResourceScalarTest {
    private class Root(reader: ICUResourceBundleReader) : ICUResourceBundleImpl(WholeBundle("test/scalars", "root", reader))
    private fun reader(): ICUResourceBundleReader {
        val body = BinaryBuffer(ByteArray(256))
        body.writeInt(0x20000000)
        for (v in intArrayOf(8, 9, 64, 64, 0, 1, 9, 0)) body.writeInt(v)
        body.position = 128; body.writeInt(2); body.writeShort(111); body.writeShort(107)
        body.position = 152; body.writeInt(3); body.writeByte(1); body.writeByte(2); body.writeByte(3)
        body.position = 160; body.writeInt(2); body.writeInt(-1); body.writeInt(42)
        return ICUResourceBundleReader(BinaryBuffer(ICUBinary.writeHeader(0x52657342, 0x03000000, 0) + body.bytes))
    }
    @Test fun concreteScalarTypesPreserveKeysSizesAndTypedFailures() {
        val root = Root(reader())
        val string = ICUResourceBundleImpl.ResourceString(root, "string", 32)
        assertEquals(UResourceBundle.STRING, string.getType()); assertEquals("ok", string.getString())
        assertEquals(32, string.getResource()); assertEquals("string", string.getKey())
        assertEquals(1, string.getSize()); assertTrue(string.isRoot()); assertEquals(ULocale.ROOT, string.getULocale())
        assertFailsWith<UResourceTypeMismatchException> { string.getInt() }
        val integer = ICUResourceBundleImpl.ResourceInt(root, "int", 0x7fffffff)
        assertEquals(UResourceBundle.INT, integer.getType()); assertEquals(-1, integer.getInt()); assertEquals(0xfffffff, integer.getUInt())
        val vector = ICUResourceBundleImpl.ResourceIntVector(root, "vector", 0xe0000028.toInt())
        assertEquals(UResourceBundle.INT_VECTOR, vector.getType()); assertEquals(1, vector.getSize())
        val first = vector.getIntVector(); first[0] = 0
        assertContentEquals(intArrayOf(-1, 42), vector.getIntVector())
        val binary = ICUResourceBundleImpl.ResourceBinary(root, "binary", 0x10000026)
        val destination = ByteArray(3)
        assertSame(destination, binary.getBinary(destination))
        assertContentEquals(byteArrayOf(1, 2, 3), destination)
        val short = ByteArray(2)
        val replacement = binary.getBinary(short)
        assertNotSame(short, replacement)
        assertContentEquals(byteArrayOf(1, 2, 3), replacement)
        destination.fill(0); assertContentEquals(byteArrayOf(1, 2, 3), binary.getBinary(null))
        assertTrue(binary.getBinary().isReadOnly)
        val cursor = binary.getBinary(); cursor.readByte(); assertEquals(0, binary.getBinary().position)
        val empty = ICUResourceBundleImpl.ResourceBinary(root, "empty", 0x10000000)
        val supplied = byteArrayOf()
        assertNotSame(supplied, empty.getBinary(supplied))
        assertSame(empty.getBinary(null), empty.getBinary(supplied))
        assertEquals(0, empty.getBinary().remaining)
    }
}
