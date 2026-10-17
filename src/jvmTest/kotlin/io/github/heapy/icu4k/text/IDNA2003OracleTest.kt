package io.github.heapy.icu4k.text

import com.ibm.icu.text.IDNA as Source
import com.ibm.icu.text.UCharacterIterator as SourceIterator
import kotlin.random.Random
import kotlin.test.*

class IDNA2003OracleTest {
    private fun result(block:()->Any?): String = try {"ok:${block()}"}catch(e:Throwable) {
        when(e) {
            is com.ibm.icu.text.StringPrepParseException->"error:${e.error}:$e"
            is StringPrepParseException->"error:${e.getError()}:$e"
            is IndexOutOfBoundsException->"bounds"
            else->"${e.javaClass.simpleName}:${e.message}"
        }
    }
    private fun compare(input: String,options: Int) {
        val context="options=$options input=${input.toCharArray().joinToString {it.code.toString(16)}}"
        assertEquals(result{Source.convertToASCII(input,options)},result{IDNA.convertToASCII(input,options)},context)
        assertEquals(result{Source.convertToUnicode(input,options)},result{IDNA.convertToUnicode(input,options)},context)
        assertEquals(result{Source.convertIDNToASCII(input,options)},result{IDNA.convertIDNToASCII(input,options)},context)
        assertEquals(result{Source.convertIDNToUnicode(input,options)},result{IDNA.convertIDNToUnicode(input,options)},context)
        for(index in listOf(0,input.length/2,input.length).distinct()) for(unicode in listOf(false,true)) {
            val si=SourceIterator.getInstance(input);val pi=UCharacterIterator.getInstance(input)
            si.setIndex(index);pi.setIndex(index)
            assertEquals(result{if(unicode)Source.convertToUnicode(si,options)else Source.convertToASCII(si,options)},result{if(unicode)IDNA.convertToUnicode(pi,options)else IDNA.convertToASCII(pi,options)},"$context/$index/$unicode")
            assertEquals(si.index,pi.getIndex(),context)
        }
        assertEquals(result{Source.convertToASCII(StringBuffer(input),options)},result{IDNA.convertToASCII(StringBuilder(input),options)},context)
        assertEquals(result{Source.convertToUnicode(StringBuffer(input),options)},result{IDNA.convertToUnicode(StringBuilder(input),options)},context)
    }
    @Test fun conversionsErrorsAndIteratorOffsets() {
        val cases=mutableListOf("", ".", "..", "Example.COM.", "bücher.de", "faß.de", "ς.gr", "a\u200cb", "אa", "א1א", "xn--", "xn---", "xn--a-", "xn--abc-", "xn--bcher-kva", "xn--fa-hia", "XN--BCHER-KVA", "xn--é", "a_b", "-abc-", "a b", "\u00ad", "a。b．c｡", "😀.com", "a".repeat(64),"é".repeat(1001),List(4){"a".repeat(63)}.joinToString("."),List(4){"a".repeat(63)}.joinToString(".")+".")
        cases+=listOf(charArrayOf('\ud800').concatToString(),charArrayOf('a','\udc00').concatToString())
        for(input in cases)for(options in listOf(0,1,2,3,-1,Int.MIN_VALUE))compare(input,options)
        val random=Random(2003)
        repeat(2500) {
            val input=buildString {repeat(random.nextInt(1,25)){append(if(random.nextBoolean())random.nextInt(128).toChar()else random.nextInt(65536).toChar())}}
            compare(input,random.nextInt(4))
        }
    }
    @Test fun sourceAsciiComparisonIncludingUnequalLengths() {
        val cases=listOf("", "a", "ab", "abc", "AbC", "a.example", "bücher.de", "xn--bcher-kva.de", "faß", "fass", "a.", "א")
        for(s1 in cases)for(s2 in cases)for(options in 0..3) {
            assertEquals(result{Source.compare(s1,s2,options)},result{IDNA.compare(s1,s2,options)},"$s1/$s2/$options")
            assertEquals(result{Source.compare(StringBuffer(s1),StringBuffer(s2),options)},result{IDNA.compare(StringBuilder(s1),StringBuilder(s2),options)})
            assertEquals(result{Source.compare(SourceIterator.getInstance(s1),SourceIterator.getInstance(s2),options)},result{IDNA.compare(UCharacterIterator.getInstance(s1),UCharacterIterator.getInstance(s2),options)})
        }
        assertEquals(result{Source.compare(null as String?,"a",0)},result{IDNA.compare(null as String?,"a",0)})
    }
}
