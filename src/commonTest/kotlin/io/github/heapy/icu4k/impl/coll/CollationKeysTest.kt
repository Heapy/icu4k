// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

internal class KeysTestSink(capacity: Int=8,val grow: Boolean=true): CollationKeys.SortKeyByteSink(ByteArray(capacity)) {
    val calls=mutableListOf<String>()
    fun buffer(): ByteArray = buffer_!!
    override fun Resize(appendCapacity: Int,length: Int): Boolean {
        calls.add("resize/$appendCapacity/$length")
        if(!grow) return false
        buffer_=buffer_!!.copyOf(maxOf(length+appendCapacity,buffer_!!.size*2+1));return true
    }
    override fun AppendBeyondCapacity(bytes: ByteArray,start: Int,n: Int,length: Int) {
        calls.add("beyond/$start/$n/$length")
        if(Resize(n,length)) bytes.copyInto(buffer_!!,length,start,start+n)
    }
    fun result(): List<Int> = buffer_!!.take(minOf(buffer_!!.size,NumberOfBytesAppended())).map { it.toInt() and 255 }
}
class CollationKeysTest {
    private fun key(weights: LongArray,flags: Int=0,strength: Int=2,stop: Int=-1): List<Int> {
        val sink=KeysTestSink();val settings=CollationSettings().apply { options=(strength shl 12) or flags;variableTop=0x20000000 }
        CollationKeys.writeSortKeyUpToQuaternary(compareFixtureIterator(weights),BooleanArray(256),settings,sink,1,object: CollationKeys.LevelCallback() { override fun needToWrite(level: Int): Boolean = level!=stop },true)
        return sink.result()
    }
    @Test fun primaryAndCommonWeightCompression() {
        assertEquals(listOf(0x30,1,5,1,5),key(longArrayOf(compareCE(0x30000000))))
        assertEquals(listOf(0x30,1,6,1,6),key(longArrayOf(compareCE(0x30000000),compareCE(0))))
        assertEquals(listOf(1,1),key(longArrayOf()))
        assertEquals(listOf(0x30),key(longArrayOf(compareCE(0x30000000)),stop=2))
        assertEquals(listOf(0x30,1,5),key(longArrayOf(compareCE(0x30000000)),stop=4))
    }
    @Test fun keysHaveSameOrderAsCeComparison() {
        val cases=listOf(longArrayOf(),longArrayOf(compareCE(0x30000000)),longArrayOf(compareCE(0x40000000)),longArrayOf(compareCE(0x30000000,0x600)),longArrayOf(compareCE(0x30000000,0x500,0x8500)),longArrayOf(compareCE(0x10000000),compareCE(0x30000000)))
        for(strength in 0..3) for(flags in listOf(0,4,0x800,0x200,0x300,0x400,0x700)) for(a in cases) for(b in cases) {
            val s=CollationSettings().apply { options=(strength shl 12) or flags;variableTop=0x20000000 }
            val expected=CollationCompare.compareUpToQuaternary(compareFixtureIterator(a),compareFixtureIterator(b),s)
            val ka=key(a,flags,strength)+0;val kb=key(b,flags,strength)+0
            var actual=0
            for(i in 0 until minOf(ka.size,kb.size)) if(ka[i]!=kb[i]) { actual=ka[i].compareTo(kb[i]);break }
            assertEquals(expected,actual)
        }
    }
    @Test fun sinkOverflowCountsAndNoLevelsReturn() {
        val sink=KeysTestSink(1,false);sink.Append(9);sink.Append(10);sink.Append(byteArrayOf(11,12),2)
        assertEquals(4,sink.NumberOfBytesAppended());assertEquals(-3,sink.GetRemainingCapacity());assertTrue(sink.Overflowed());assertEquals(listOf(9),sink.result())
        sink.setBufferAndAppended(ByteArray(3),0);assertFalse(sink.Overflowed())
        CollationKeys.writeSortKeyUpToQuaternary(null,null,CollationSettings(),null,6,null,false)
    }
}
