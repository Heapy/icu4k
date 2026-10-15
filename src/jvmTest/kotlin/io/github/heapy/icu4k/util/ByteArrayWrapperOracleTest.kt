// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import com.ibm.icu.util.ByteArrayWrapper as Source
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.test.*

class ByteArrayWrapperOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) { when(e) { is IndexOutOfBoundsException -> "bounds";is NullPointerException -> "null";is NegativeArraySizeException -> "negative";else -> e.javaClass.simpleName+":"+e.message } }
    @Test fun randomizedMutationHistoriesAndCorruptPublicState() {
        val random=Random(78312)
        val a=Source();val b=ByteArrayWrapper()
        repeat(4000) { step ->
            val data=ByteArray(random.nextInt(0,100)) { random.nextInt().toByte() };val start=random.nextInt(-2,data.size+3);val end=random.nextInt(-2,data.size+3)
            val resultA: Any?;val resultB: Any?
            when(step%8) {
                0 -> { val capacity=random.nextInt(-2,120);resultA=outcome { a.ensureCapacity(capacity);Unit };resultB=outcome { b.ensureCapacity(capacity);Unit } }
                1 -> { resultA=outcome { a.append(data,start,end);Unit };resultB=outcome { b.append(data,start,end);Unit } }
                2 -> { resultA=outcome { a.set(data,start,end);Unit };resultB=outcome { b.set(data,start,end);Unit } }
                3 -> { val n=random.nextInt(-2,104);resultA=outcome { a.size=n };resultB=outcome { b.size=n } }
                4 -> { resultA=outcome { a.toString() };resultB=outcome { b.toString() } }
                5 -> { resultA=outcome { a.hashCode() };resultB=outcome { b.hashCode() } }
                6 -> { resultA=outcome { a.releaseBytes()?.toList() };resultB=outcome { b.releaseBytes()?.toList() } }
                else -> { resultA=outcome { a.append(a.bytes,start,end);Unit };resultB=outcome { b.append(b.bytes,start,end);Unit } }
            }
            assertEquals(resultA,resultB,"step=$step");assertEquals(a.size,b.size);assertContentEquals(a.bytes,b.bytes,"state/$step")
            val otherA=Source(data,data.size);val otherB=ByteArrayWrapper(data.copyOf(),data.size)
            assertEquals(outcome { a==otherA },outcome { b==otherB });assertEquals(outcome { a.compareTo(otherA) },outcome { b.compareTo(otherB) })
        }
    }
    @Test fun bufferConstructorConsumesLimitRatherThanRemaining() {
        for(direct in listOf(false,true)) for(position in 0..8) {
            val a=if(direct) ByteBuffer.allocateDirect(8) else ByteBuffer.allocate(8)
            for(i in 0..7) a.put(i,i.toByte())
            a.position(position);val b=a.duplicate()
            assertEquals(outcome { Source(a).toString() },outcome { ByteArrayWrapper(b).toString() })
            assertEquals(a.position(),b.position())
        }
        val common=BinaryBuffer(byteArrayOf(1,2,3));assertEquals("01 02 03",ByteArrayWrapper(common).toString());assertEquals(3,common.position)
    }
}
