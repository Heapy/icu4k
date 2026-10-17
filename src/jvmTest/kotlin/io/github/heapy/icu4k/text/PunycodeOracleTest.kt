package io.github.heapy.icu4k.text

import com.ibm.icu.impl.Punycode as Source
import io.github.heapy.icu4k.impl.Punycode
import kotlin.random.Random
import kotlin.test.*

class PunycodeOracleTest {
    private fun outcome(block: () -> Any?): String = try { "ok:${block()}" } catch(e: Throwable) {
        val code=when(e) {
            is com.ibm.icu.text.StringPrepParseException -> e.error
            is StringPrepParseException -> e.getError()
            else -> null
        }
        if(e is IndexOutOfBoundsException) "bounds" else "${e.javaClass.simpleName}:${e.message}:$code"
    }
    @Test fun codecMatchesIncludingFailuresAndCaseFlags() {
        val random=Random(819)
        val inputs=mutableListOf("", "abc", "Bücher", "mañana", "日本語", "😀a𐀀", "a".repeat(1000), "a".repeat(1001))
        inputs+=listOf(charArrayOf('\ud800').concatToString(),charArrayOf('a','\udc00').concatToString())
        repeat(500) {
            inputs+=buildString { repeat(random.nextInt(20)) { val cp=random.nextInt(0x110000); if(cp<0x10000)append(cp.toChar()) else {append(((cp-0x10000)/1024+0xd800).toChar());append(((cp-0x10000)%1024+0xdc00).toChar())} } }
        }
        for(input in inputs) for(size in listOf(-1,0,1,input.length,input.length+4).distinct()) {
            val sf=if(size<0)null else BooleanArray(size){it%3==0};val pf=sf?.copyOf()
            assertEquals(outcome{Source.encode(input,sf)},outcome{Punycode.encode(input,pf)},"encode size=$size input=$input")
            assertContentEquals(sf,pf)
        }
        val encoded=inputs.mapNotNull {try {Source.encode(it,null).toString()}catch(_:Exception){null}}.toMutableList()
        encoded+=listOf("-","a","A","z","!","é-","abc-!","zzzzzzzzzzzzzzzzzzzzzzzzzzzzzz","a".repeat(2001))
        repeat(1500) {encoded+=buildString {repeat(random.nextInt(40)){append(random.nextInt(128).toChar())}}}
        for(input in encoded) for(size in listOf(-1,0,1,input.length,input.length+8).distinct()) {
            val sf=if(size<0)null else BooleanArray(size){it%3==0};val pf=sf?.copyOf()
            assertEquals(outcome{Source.decode(input,sf)},outcome{Punycode.decode(input,pf)},"decode size=$size input=$input")
            assertContentEquals(sf,pf,"flags size=$size input=$input")
        }
    }
    @Test fun parseExceptionContextAndEqualityMatch() {
        val rules="0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        for(pos in -2..rules.length+2) for(line in listOf(-1,0,17)) {
            assertEquals(outcome{com.ibm.icu.text.StringPrepParseException("bad",7,rules,pos,line).toString()},outcome{StringPrepParseException("bad",7,rules,pos,line).toString()},"$pos/$line")
        }
        for(message in listOf(null,"","bad")) for(code in -1..12) {
            val source=com.ibm.icu.text.StringPrepParseException(message,code);val port=StringPrepParseException(message,code)
            assertEquals(source.toString(),port.toString());assertEquals(source.errorOffset,port.errorOffset)
            assertEquals(source.error,port.getError())
            assertEquals(outcome{source.hashCode()},outcome{port.hashCode()})
            assertEquals(source==com.ibm.icu.text.StringPrepParseException("other",code),port==StringPrepParseException("other",code))
        }
    }
}
