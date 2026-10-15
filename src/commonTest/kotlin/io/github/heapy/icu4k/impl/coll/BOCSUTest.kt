// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.util.ByteArrayWrapper
import kotlin.test.*

class BOCSUTest {
    @Test fun asciiMergeSeparatorAndEmptyRun() {
        val sink=ByteArrayWrapper(ByteArray(0),0)
        assertEquals('c'.code,BOCSU.writeIdenticalLevelRun(0,"abc",0,3,sink))
        assertEquals("92 93 94",sink.toString())
        assertEquals(0,BOCSU.writeIdenticalLevelRun('c'.code,"\ufffe",0,1,sink))
        assertEquals(2,sink.bytes!![sink.size-1].toInt())
        assertEquals(123,BOCSU.writeIdenticalLevelRun(123,null,2,2,null))
        assertFailsWith<NullPointerException> { BOCSU.writeIdenticalLevelRun(0,"a",0,1,ByteArrayWrapper()) }
    }
    @Test fun streamingAndCodePointOrder() {
        val texts=listOf("a","abc","á","Ω","中文","한글",charArrayOf(0xd83d.toChar(),0xde00.toChar()).concatToString())
        val whole=texts.joinToString("");val one=ByteArrayWrapper(ByteArray(0),0);val many=ByteArrayWrapper(ByteArray(0),0)
        val last=BOCSU.writeIdenticalLevelRun(0,whole,0,whole.length,one)
        var prev=0
        for(text in texts) prev=BOCSU.writeIdenticalLevelRun(prev,text,0,text.length,many)
        assertEquals(last,prev);assertEquals(one,many)
        for(i in 0 until one.size) assertTrue((one.bytes!![i].toInt() and 255)>=3)
        for(i in 1 until texts.size) {
            val a=ByteArrayWrapper(ByteArray(0),0);val b=ByteArrayWrapper(ByteArray(0),0)
            BOCSU.writeIdenticalLevelRun(0,texts[i-1],0,texts[i-1].length,a);BOCSU.writeIdenticalLevelRun(0,texts[i],0,texts[i].length,b)
            // These fixtures are ordered by code point, as are the encoded bytes.
            if(texts[i-1][0]<texts[i][0]) assertTrue(a<b)
        }
    }
}
