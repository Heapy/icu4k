package io.github.heapy.icu4k.impl.number

import kotlin.test.*

class DecimalBcdStorageTest {
    @Test fun spillCompactAndCopyPreserveDigits() {
        val storage = DecimalBcdStorage()
        storage.readLongToBcd(1234567890123456L)
        storage.compact()
        assertFalse(storage.usingBytes)
        storage.shiftLeft(1)
        assertTrue(storage.usingBytes)
        storage.setDigitPos(0, 7)
        storage.compact()
        assertEquals("12345678901234567E-1", storage.toNumberString())
        assertNull(storage.checkHealth())
        val copy = storage.copy()
        copy.shiftRight(2); copy.compact()
        assertFalse(copy.usingBytes)
        assertEquals("123456789012345E1", copy.toNumberString())
        assertEquals("12345678901234567E-1", storage.toNumberString())
        assertNull(copy.checkHealth())
    }
    @Test fun arbitraryLengthCoefficientsAndResetMetadata() {
        val storage = DecimalBcdStorage()
        storage.readBigIntegerToBcd("1" + "0".repeat(110) + "12300")
        storage.compact()
        assertTrue(storage.usingBytes)
        assertEquals(2, storage.scale)
        assertEquals(114, storage.precision)
        assertNull(storage.checkHealth())
        storage.origDouble = 1.25; storage.isApproximate = true; storage.origDelta = 3; storage.exponent = 6
        storage.setBcdToZero()
        assertEquals("0E0", storage.toNumberString())
        assertEquals(0, storage.precision)
        assertFalse(storage.isApproximate)
        assertEquals(0, storage.exponent)
        assertNull(storage.checkHealth())
    }
}
