// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ByteArrayWrapper
import kotlin.test.*

class CollationKeyTest {
    @Test fun arrayOwnershipAndRawOverloadComparison() {
        val bytes=byteArrayOf(20,1,5,0,99)
        val raw=RawCollationKey(bytes,4);val key=CollationKey("source",raw)
        assertNull(raw.bytes);assertEquals(0,raw.size);assertEquals("source",key.getSourceString())
        assertContentEquals(byteArrayOf(20,1,5,0),key.toByteArray())
        val copy=key.toByteArray();copy[0]=50;assertEquals(20,key.toByteArray()[0].toInt())
        bytes[0]=21;assertEquals(21,key.toByteArray()[0].toInt())
        val a=RawCollationKey(byteArrayOf(3),1);val b=RawCollationKey(byteArrayOf(100),1)
        assertEquals(-1,a.compareTo(b));assertEquals(-97,(a as ByteArrayWrapper).compareTo(b))
        assertEquals(0,RawCollationKey(byteArrayOf(9)).size)
    }
    @Test fun boundsAndUnequalLevelMerging() {
        val a=CollationKey("a",byteArrayOf(20,21,1,5,1,7,0))
        val b=CollationKey("b",byteArrayOf(30,1,6,0))
        assertContentEquals(byteArrayOf(20,21,0),a.getBound(0,1).toByteArray())
        assertContentEquals(byteArrayOf(20,21,2,0),a.getBound(1,1).toByteArray())
        assertContentEquals(byteArrayOf(20,21,-1,-1,0),a.getBound(2,1).toByteArray())
        assertContentEquals(byteArrayOf(20,21,2,30,1,5,2,6,1,7,0),a.merge(b).toByteArray())
        assertNull(a.merge(b).getSourceString())
        assertFailsWith<IllegalArgumentException> { a.getBound(0,3) }
        assertFailsWith<IllegalArgumentException> { a.merge(null) }
        assertEquals(a,CollationKey("different source",a.toByteArray()))
        assertEquals(a.hashCode(),CollationKey(null,a.toByteArray()).hashCode())
    }
    @Test fun cachedLengthAndHashSurviveExternalMutations() {
        val bytes=byteArrayOf(20,21,0,0);val key=CollationKey(null,bytes)
        val hash=key.hashCode();assertEquals(3,key.toByteArray().size)
        bytes[0]=30;bytes[1]=0
        assertEquals(hash,key.hashCode());assertEquals(3,key.toByteArray().size)
        assertEquals(1,CollationKey(null,null as ByteArray?).hashCode())
    }
}
