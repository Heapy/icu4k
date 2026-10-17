package io.github.heapy.icu4k.text

import com.ibm.icu.text.IDNA as Source
import kotlin.random.Random
import kotlin.test.*

class IDNAOracleTest {
    private fun outcome(block:()->Any?): String = try {block();"ok"}catch(e:Throwable){"${e.javaClass.simpleName}:${e.message}"}
    private fun compare(src: String,options: Int) {
        val source=Source.getUTS46Instance(options);val port=IDNA.getUTS46Instance(options)
        val si=Source.Info();val pi=IDNA.Info();val sb=StringBuilder("old");val pb=StringBuilder("old")
        for(op in 0..3) {
            val se=outcome{when(op){0->source.labelToASCII(src,sb,si);1->source.labelToUnicode(src,sb,si);2->source.nameToASCII(src,sb,si);else->source.nameToUnicode(src,sb,si)}}
            val pe=outcome{when(op){0->port.labelToASCII(src,pb,pi);1->port.labelToUnicode(src,pb,pi);2->port.nameToASCII(src,pb,pi);else->port.nameToUnicode(src,pb,pi)}}
            val context="options=$options op=$op input=${src.toCharArray().joinToString {it.code.toString(16)}}"
            assertEquals(se,pe,context);assertEquals(sb.toString(),pb.toString(),context)
            assertEquals(si.errors.map{it.name},pi.getErrors().map{it.name},context)
            assertEquals(si.hasErrors(),pi.hasErrors(),context)
            assertEquals(si.isTransitionalDifferent,pi.isTransitionalDifferent(),context)
        }
    }
    @Test fun allOptionsAndDeterministicUnicodeCases() {
        val cases=mutableListOf("", ".", "..", "EXAMPLE.COM.", "bücher.de", "faß.de", "ς.gr", "a\u200cb", "a\u200db", "क्\u200dष", "א1.2a", "a.אב", "ل\u200cا", "ل\u0301\u200c\u0301ا", "l·l", "a·b", "͵α", "͵a", "א׳", "a׳", "カ・", "a・", "١۲", "١٢", "۱۲", "\u0301a", "-a-", "ab--cd", "xn--", "xn---", "xn--a-", "xn--abc-", "xn--bcher-kva", "xn--fa-hia", "xn--xn---epa", "a_b", "a b", "😀.com", "Ａ．Ｂ。Ｃ｡", "a".repeat(64), List(4){"a".repeat(63)}.joinToString("."),List(4){"a".repeat(63)}.joinToString(".")+".")
        cases+=listOf(charArrayOf('\ud800').concatToString(),charArrayOf('a','\udc00','b').concatToString(),"𞤀𞤁", "𐐀𐐨")
        cases+=listOf("é".repeat(1000),"é".repeat(1001),"😀".repeat(500),"😀".repeat(501))
        for(options in (0..127).toList()+listOf(-1,Int.MIN_VALUE,Int.MAX_VALUE))for(input in cases)compare(input,options)
        val random=Random(4617)
        repeat(2500) {
            val input=buildString {repeat(random.nextInt(1,18)){when(random.nextInt(4)){0->append(random.nextInt(128).toChar());1->append(random.nextInt(0x10000).toChar());2->{val cp=random.nextInt(0x10000,0x110000)-0x10000;append((cp/1024+0xd800).toChar());append((cp%1024+0xdc00).toChar())};else->append(listOf('ß','ς','\u200c','\u200d','·','.','א','ل','\u0301')[random.nextInt(9)])}}}
            compare(input,random.nextInt(128))
        }
    }
    @Test fun unicode17ConformanceInputsAgainstPinnedSource() {
        val text=javaClass.getResourceAsStream("/IdnaTestV2.txt")!!.bufferedReader().use {it.readText()}
        val escape=Regex("\\\\u([0-9a-fA-F]{4})|\\\\x\\{([0-9a-fA-F]+)\\}")
        var count=0
        for(line in text.lineSequence()) {
            val field=line.substringBefore('#').substringBefore(';').trim()
            if(field.isEmpty() && !line.contains(';'))continue
            val input=if(field=="\"\"")"" else escape.replace(field){m->String(Character.toChars((m.groups[1]?.value?:m.groups[2]!!.value).toInt(16)))}
            for(options in listOf(0,14,48,62,126))compare(input,options)
            count++
        }
        assertTrue(count>6000,"Full Unicode 17 corpus must be loaded: $count")
    }
    @Test fun infoLiveErrorSetResetAndDestinationAliasing() {
        val source=Source.getUTS46Instance(126);val port=IDNA.getUTS46Instance(126)
        val si=Source.Info();val pi=IDNA.Info();val ss=si.errors;val ps=pi.getErrors()
        for(e in Source.Error.entries.reversed())ss.add(e)
        for(e in IDNA.Error.entries.reversed())ps.add(e)
        assertEquals(ss.map{it.name},ps.map{it.name})
        val sit=ss.iterator();val pit=ps.iterator();assertEquals(sit.next().name,pit.next().name)
        ss.clear();ps.clear();ss.add(Source.Error.BIDI);ps.add(IDNA.Error.BIDI)
        assertEquals(sit.asSequence().map{it.name}.toList(),pit.asSequence().map{it.name}.toList())
        val sb=StringBuilder("alias");val pb=StringBuilder("alias")
        assertEquals(outcome{source.nameToASCII(sb,sb,si)},outcome{port.nameToASCII(pb,pb,pi)})
        assertEquals(sb.toString(),pb.toString());assertEquals(ss.map{it.name},ps.map{it.name})
        source.nameToASCII("valid.example",sb,si);port.nameToASCII("valid.example",pb,pi)
        assertSame(ss,si.errors);assertSame(ps,pi.getErrors());assertTrue(ps.isEmpty());assertEquals(sb.toString(),pb.toString())
    }
}
