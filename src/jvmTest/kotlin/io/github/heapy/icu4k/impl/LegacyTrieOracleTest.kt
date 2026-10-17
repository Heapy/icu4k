package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class LegacyTrieOracleTest {
    private fun result(block:()->Any?): String = try {"ok:${block()}"}catch(e:Throwable){if(e is IndexOutOfBoundsException)"bounds" else "${e.javaClass.simpleName}:${e.message}"}
    @Test fun allStringPrepTrieCodePoints() {
        for(name in listOf("rfc3491","rfc3530cs","rfc3530csci","rfc3530mixp","rfc3722","rfc3920node","rfc3920res","rfc4011","rfc4013","rfc4505","rfc4518","rfc4518ci")) {
            val sb=com.ibm.icu.impl.ICUBinary.getRequiredData("$name.spp")
            val pb=ICUBinary.getRequiredData("$name.spp")
            val sr=com.ibm.icu.impl.StringPrepDataReader(sb);val pr=StringPrepDataReader(pb)
            assertContentEquals(sr.unicodeVersion,pr.getUnicodeVersion());assertContentEquals(sr.readIndexes(16),pr.readIndexes(16))
            val source=com.ibm.icu.impl.CharTrie(sb,null);val port=CharTrie(pb,null)
            assertEquals(source.serializedDataSize,port.getSerializedDataSize());assertEquals(source.isLatin1Linear,port.isLatin1Linear())
            for(cp in -1..0x110000)assertEquals(source.getCodePointValue(cp),port.getCodePointValue(cp),"$name U+${cp.toString(16)}")
            for(c in 0..65535){assertEquals(source.getBMPValue(c.toChar()),port.getBMPValue(c.toChar()));assertEquals(source.getLeadValue(c.toChar()),port.getLeadValue(c.toChar()))}
            for(c in 0..255)assertEquals(source.getLatin1LinearValue(c.toChar()),port.getLatin1LinearValue(c.toChar()))
            assertEquals(result{source.hashCode()},result{port.hashCode()})
        }
    }
    @Test fun dummyCharAndIntAndSerializedInt() {
        for(initial in listOf(-1,0,17,65536,Int.MAX_VALUE))for(lead in listOf(0,17,initial)) {
            val sc=com.ibm.icu.impl.CharTrie(initial,lead){if(it>0)32 else 0};val pc=CharTrie(initial,lead){if(it>0)32 else 0}
            val si=com.ibm.icu.impl.IntTrie(initial,lead){if(it>0)32 else 0};val pi=IntTrie(initial,lead){if(it>0)32 else 0}
            assertEquals(sc.serializedDataSize,pc.getSerializedDataSize());assertEquals(si.serializedDataSize,pi.getSerializedDataSize())
            for(cp in listOf(-1,0,127,255,256,0xd800,0xdbff,0xdc00,0xffff,0x10000,0x10ffff,0x110000)) {
                assertEquals(result{sc.getCodePointValue(cp).code},result{pc.getCodePointValue(cp).code})
                assertEquals(result{si.getCodePointValue(cp)},result{pi.getCodePointValue(cp)})
            }
            for(c in listOf('a','\ud800','\udc00','\uffff'))for(t in listOf('b','\udfff')) {
                assertEquals(result{sc.getSurrogateValue(c,t).code},result{pc.getSurrogateValue(c,t).code})
                assertEquals(result{si.getSurrogateValue(c,t)},result{pi.getSurrogateValue(c,t)})
                assertEquals(result{sc.getTrailValue(lead,t).code},result{pc.getTrailValue(lead,t).code})
                assertEquals(result{si.getTrailValue(lead,t)},result{pi.getTrailValue(lead,t)})
            }
            assertEquals(result{sc.getLatin1LinearValue('a').code},result{pc.getLatin1LinearValue('a').code})
            assertEquals(si.getLatin1LinearValue('a'),pi.getLatin1LinearValue('a'))
            assertEquals(result{si.hashCode()},result{pi.hashCode()})
        }
        for(little in listOf(false,true)) {
            val sb=java.nio.ByteBuffer.allocate(16+2080*2+256*4).order(if(little)java.nio.ByteOrder.LITTLE_ENDIAN else java.nio.ByteOrder.BIG_ENDIAN)
            sb.putInt(0x54726965).putInt(0x325).putInt(2080).putInt(256)
            repeat(2080){sb.putChar(0.toChar())};repeat(256){sb.putInt(it*100003-1000000)};sb.flip()
            val source=com.ibm.icu.impl.IntTrie(sb){0}
            val port=IntTrie(BinaryBuffer(sb.array(),if(little)BinaryBuffer.ByteOrder.LITTLE_ENDIAN else BinaryBuffer.ByteOrder.BIG_ENDIAN)){0}
            for(cp in -1..0x110000)assertEquals(source.getCodePointValue(cp),port.getCodePointValue(cp))
            for(c in 0..255)assertEquals(result{source.getLatin1LinearValue(c.toChar())},result{port.getLatin1LinearValue(c.toChar())})
            assertEquals(source.serializedDataSize,port.getSerializedDataSize())
        }
    }
}
