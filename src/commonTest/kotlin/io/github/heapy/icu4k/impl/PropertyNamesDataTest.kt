package io.github.heapy.icu4k.impl

import kotlin.test.*

class PropertyNamesDataTest {
    @Test fun propertyNameResourceHasOriginalHeaderAndPrivateCopies() {
        assertTrue(ICUData.exists("pnames.icu"))
        val raw = ICUData.getBytes("pnames.icu")!!
        assertEquals(47072, raw.size)
        val bytes = ICUBinary.getRequiredData("pnames.icu")
        ICUBinary.readHeader(bytes, 0x706e616d, ICUBinary.Authenticate { it[0].toInt() == 2 })
        val indexesLength = bytes.readInt() / 4
        assertTrue(indexesLength >= 8)
        val indexes = IntArray(indexesLength)
        indexes[0] = indexesLength * 4
        for (i in 1 until indexesLength) indexes[i] = bytes.readInt()
        assertTrue(indexes[1] > indexes[0])
        assertTrue(indexes[2] > indexes[1])
        assertTrue(indexes[3] > indexes[2])
        raw[0] = (raw[0].toInt() xor 255).toByte()
        assertNotEquals(raw[0], ICUData.getBytes("pnames.icu")!![0])
        assertContentEquals(ICUData.getBytes("pnames.icu"), ICUData.getBytes("/${ICUData.ICU_BASE_NAME}/pnames.icu"))
    }
}
