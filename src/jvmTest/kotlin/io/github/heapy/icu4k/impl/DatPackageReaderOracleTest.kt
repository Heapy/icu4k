package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import kotlin.test.*

class DatPackageReaderOracleTest {
    private val reader = Class.forName("com.ibm.icu.impl.ICUBinary\$DatPackageReader")
    private val validate = reader.getDeclaredMethod("validate", ByteBuffer::class.java).apply { isAccessible = true }
    private val getData = reader.getDeclaredMethod("getData", ByteBuffer::class.java, CharSequence::class.java).apply { isAccessible = true }
    private val addNames = reader.getDeclaredMethod("addBaseNamesInFolder", ByteBuffer::class.java, String::class.java, String::class.java, Set::class.java).apply { isAccessible = true }

    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Throwable) {
        val cause = if (failure is InvocationTargetException) failure.cause!! else failure
        when (cause) {
            is IndexOutOfBoundsException -> "bounds"
            is java.io.IOException, is ICUUncheckedIOException -> "io"
            else -> cause::class.simpleName
        }
    }
    @Test fun packageDataAndFolderNamesMatchBothByteOrdersAndReadonlyViews() {
        val random = Random(7823)
        for (little in listOf(false, true)) for (readonly in listOf(false, true)) for (packageName in listOf("icudt78b", "icudt78l")) {
            val items = linkedMapOf<String, ByteArray>()
            for (i in 0 until 80) {
                val folder = when (i % 4) { 0 -> ""; 1 -> "coll/"; 2 -> "coll/nested/"; else -> "lang/" }
                items[folder + "item_" + i.toString().padStart(3, '0') + if (i % 5 == 0) ".resjunk" else ".res"] = random.nextBytes(16 + i % 37)
            }
            items["a.res"] = random.nextBytes(16); items["z.res"] = random.nextBytes(16)
            val data = DatPackageFixture.write(items, little, packageName)
            val prefixed = ByteArray(11) + data + ByteArray(7)
            var java = ByteBuffer.wrap(prefixed, 11, data.size).slice()
            if (readonly) java = java.asReadOnlyBuffer()
            val common = BinaryBuffer(prefixed, arrayOffset = 11, capacity = data.size, isReadOnly = readonly)
            assertEquals(validate.invoke(null, java), DatPackageReader.validate(common))
            assertEquals(java.position(), common.position)
            for (key in items.keys + listOf("", "a", "missing.res", "coll", "zzzz", "item_999.res")) {
                val expected = getData.invoke(null, java, key) as ByteBuffer?
                val actual = DatPackageReader.getData(common, key)
                if (expected == null) { assertNull(actual); continue }
                assertNotNull(actual)
                assertEquals(expected.remaining(), actual.remaining, key)
                assertEquals(expected.isReadOnly, actual.isReadOnly)
                assertEquals(ByteOrder.BIG_ENDIAN, expected.order())
                assertEquals(BinaryBuffer.ByteOrder.BIG_ENDIAN, actual.order)
                val bytes = ByteArray(expected.remaining()); expected.get(bytes)
                assertContentEquals(bytes, actual.readBytes(actual.remaining), key)
            }
            for (folder in listOf("", "coll", "coll/nested", "lang", "missing", "coll/", "a.res")) {
                for (suffix in listOf(".res", "", "junk", ".dat", "longer-than-item-name", "item")) {
                    val expected = linkedSetOf<String>(); val actual = linkedSetOf<String>()
                    addNames.invoke(null, java, folder, suffix, expected)
                    DatPackageReader.addBaseNamesInFolder(common, folder, suffix, actual)
                    assertEquals(expected, actual, "$folder:$suffix")
                }
            }
            assertEquals(32, common.position); assertEquals(32, java.position())
        }
    }
    @Test fun validationAndItsPartialBufferStateMatchForMalformedPackages() {
        val good = DatPackageFixture.write(mapOf("a.res" to ByteArray(16), "z.res" to ByteArray(16)))
        val cases = mutableListOf<ByteArray>()
        for (index in listOf(0, 1, 2, 3, 4, 5, 8, 9, 10, 12, 16, 32, 33, 34, 35, 36, 39, 44, 47, 52, 60)) {
            for (value in listOf(0, 1, 2, 127, 255)) cases.add(good.copyOf().also { it[index] = value.toByte() })
        }
        cases.add(DatPackageFixture.write(mapOf("a" to ByteArray(16)), packageName = "icudt77b"))
        cases.add(DatPackageFixture.write(mapOf("a" to ByteArray(16)), packageName = "icudt78x"))
        cases.add(good.copyOf().also { it[35] = 0 })
        for ((index, data) in cases.withIndex()) {
            val java = ByteBuffer.wrap(data)
            val common = BinaryBuffer(data)
            assertEquals(outcome { validate.invoke(null, java) }, outcome { DatPackageReader.validate(common) }, "case $index")
            assertEquals(java.position(), common.position, "position $index")
            assertEquals(java.order() == ByteOrder.BIG_ENDIAN, common.order == BinaryBuffer.ByteOrder.BIG_ENDIAN, "order $index")
        }
    }
    @Test fun duplicateStateAndBorrowedPackagePayloadMatchByteBuffer() {
        val storage = DatPackageFixture.write(mapOf("a.res" to ByteArray(16)), little = true)
        val java = ByteBuffer.wrap(storage)
        val common = BinaryBuffer(storage)
        assertEquals(true, validate.invoke(null, java)); assertTrue(DatPackageReader.validate(common))
        val expected = getData.invoke(null, java, "a.res") as ByteBuffer
        val actual = DatPackageReader.getData(common, "a.res")!!
        actual.writeByte(91); assertEquals(91, expected.get(0).toInt())
        expected.put(1, 72); assertEquals(72, actual.getByte(1).toInt())
        common.limit = common.capacity - 1; java.limit(java.capacity() - 1)
        val duplicate = common.duplicate(); val jduplicate = java.duplicate()
        assertEquals(jduplicate.position(), duplicate.position); assertEquals(jduplicate.limit(), duplicate.limit)
        assertEquals(BinaryBuffer.ByteOrder.BIG_ENDIAN, duplicate.order)
        // The final item ends at capacity, even when the package's current limit is shorter.
        val tail = DatPackageReader.getData(common, "a.res")!!
        val jtail = getData.invoke(null, java, "a.res") as ByteBuffer
        assertEquals(jtail.capacity(), tail.capacity)
        assertEquals(common.capacity - 1, common.limit)
    }
}
