// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class UVector32Test {
    @Test fun capacityAccessOwnershipAndFailurePublication() {
        val vector = UVector32()
        assertTrue(vector.isEmpty())
        val initial = vector.getBuffer()
        assertEquals(32, initial.size)
        vector.setElementAt(77, 31)
        assertEquals(77, vector.elementAti(31))
        assertEquals(0, vector.size())
        repeat(32) { vector.addElement(it) }
        assertSame(initial, vector.getBuffer())
        // Capacity grows before System.arraycopy validates insertion arguments.
        assertFailsWith<IndexOutOfBoundsException> { vector.insertElementAt(123, 33) }
        assertEquals(32, vector.size())
        assertEquals(128, vector.getBuffer().size)
        assertNotSame(initial, vector.getBuffer())
        assertContentEquals(IntArray(32) { it }, vector.getBuffer().copyOf(32))
        vector.insertElementAt(999, 1)
        assertEquals(999, vector.elementAti(1))
        assertEquals(1, vector.elementAti(2))
        val backing = vector.getBuffer()
        vector.removeAllElements()
        assertSame(backing, vector.getBuffer())
        assertEquals(999, vector.elementAti(1))
        backing[0] = 456
        assertEquals(456, vector.elementAti(0))
        assertFailsWith<IndexOutOfBoundsException> { vector.elementAti(-1) }
        assertFailsWith<IndexOutOfBoundsException> { vector.setElementAt(0, 128) }
        assertFailsWith<IndexOutOfBoundsException> { vector.insertElementAt(0, 1) }
        assertTrue(vector.isEmpty())
    }
    @Test fun largeCapacityChangesFromQuadruplingToDoubling() {
        val vector = UVector32()
        repeat(131073) { vector.addElement(it) }
        assertEquals(262144, vector.getBuffer().size)
        assertEquals(131072, vector.elementAti(131072))
    }
}
