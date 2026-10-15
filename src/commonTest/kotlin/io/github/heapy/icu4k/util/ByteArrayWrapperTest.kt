// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util
import kotlin.test.*

class ByteArrayWrapperTest {
    @Test fun adoptionGrowthReleaseAndUnsignedOrder() {
        val data=byteArrayOf(0,127,-128,-1)
        val w=ByteArrayWrapper(data,4);assertSame(data,w.bytes);assertEquals("00 7F 80 FF",w.toString())
        assertSame(w,w.ensureCapacity(3));assertSame(data,w.bytes)
        w.append(byteArrayOf(2,3),0,2);assertEquals(6,w.bytes!!.size);assertEquals("00 7F 80 FF 02 03",w.toString())
        assertTrue(ByteArrayWrapper(byteArrayOf(-128),1)>ByteArrayWrapper(byteArrayOf(127),1))
        val copy=ByteArrayWrapper(w.bytes!!.copyOf(),6);assertEquals(w,copy);assertEquals(w.hashCode(),copy.hashCode())
        assertSame(w.bytes,w.releaseBytes());assertNull(w.bytes);assertEquals(0,w.size)
    }
    @Test fun shortCopyAliasesAndFailureState() {
        val w=ByteArrayWrapper(byteArrayOf(1,2,3,4,5,6),2)
        w.append(w.bytes,0,4)
        assertContentEquals(byteArrayOf(1,2,1,2,1,2),w.bytes)
        val failed=ByteArrayWrapper(byteArrayOf(9),1)
        assertFailsWith<IndexOutOfBoundsException> { failed.set(byteArrayOf(7),0,3) }
        assertEquals(0,failed.size);assertEquals(3,failed.bytes!!.size);assertEquals(7,failed.bytes!![0].toInt())
        val empty=ByteArrayWrapper();empty.append(null,0,0);assertNotNull(empty.bytes)
        empty.append(null,2,1);assertEquals(-1,empty.size)
        assertFailsWith<IndexOutOfBoundsException> { ByteArrayWrapper(null,1) }
    }
}
