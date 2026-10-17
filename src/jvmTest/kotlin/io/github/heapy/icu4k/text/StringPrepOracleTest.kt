package io.github.heapy.icu4k.text

import com.ibm.icu.text.StringPrep as Source
import com.ibm.icu.text.UCharacterIterator as SourceIterator
import kotlin.random.Random
import kotlin.test.*

class StringPrepOracleTest {
    private fun result(block:()->Any?): String = try {"ok:${block()}"}catch(e:Throwable) {
        when(e) {
            is com.ibm.icu.text.StringPrepParseException->"error:${e.error}:$e"
            is StringPrepParseException->"error:${e.getError()}:$e"
            is IndexOutOfBoundsException->"bounds"
            else->"${e.javaClass.simpleName}:${e.message}"
        }
    }
    private fun compare(input: String,profile: Int,options: Int) {
        val source=Source.getInstance(profile);val port=StringPrep.getInstance(profile)
        val context="profile=$profile options=$options input=${input.toCharArray().joinToString {it.code.toString(16)}}"
        assertEquals(result{source.prepare(input,options)},result{port.prepare(input,options)},context)
        for(index in listOf(0,input.length/2,input.length).distinct()) {
            val si=SourceIterator.getInstance(input);val pi=UCharacterIterator.getInstance(input)
            si.setIndex(index);pi.setIndex(index)
            assertEquals(result{source.prepare(si,options)},result{port.prepare(pi,options)},"$context start=$index")
            assertEquals(si.index,pi.getIndex(),context)
        }
    }
    @Test fun allProfilesMappingsNormalizationErrorsAndCursor() {
        val inputs=mutableListOf("", "User Name", "Straße", "Iİı", "\u00adA\u034f", "\u0000\u0007", "a\u00a0b", "ＡＢＣ", "a\u0301", "\u212b\ufb03", "אa", "א1א", "1א", "ا\u0301", "😀", "𐀀", "\u0221", "\u0340\u0341", "\uf951", "\u200b\u200c\u200d", "\ue000", "\uffff")
        inputs+=listOf(charArrayOf('\ud800').concatToString(),charArrayOf('a','\udc00').concatToString())
        for(profile in 0..13)for(options in listOf(0,1,-1,2))for(input in inputs)compare(input,profile,options)
        val random=Random(3491)
        repeat(2500) {
            val input=buildString {repeat(random.nextInt(1,30)){append(random.nextInt(65536).toChar())}}
            compare(input,random.nextInt(14),random.nextInt(2))
        }
        for(profile in listOf(-1,14,Int.MIN_VALUE,Int.MAX_VALUE))assertEquals(result{Source.getInstance(profile)},result{StringPrep.getInstance(profile)})
    }
    @Test fun importedProfilesAndStreamClosing() {
        val names=listOf("rfc3491","rfc3530cs","rfc3530csci","rfc3530mixp","rfc3722","rfc3920node","rfc3920res","rfc4011","rfc4013","rfc4505","rfc4518","rfc4518ci")
        for(name in names) {
            val bytes=com.ibm.icu.impl.ICUBinary.getRequiredData("$name.spp").let{b->ByteArray(b.remaining()).also{b.get(it)}}
            var closed=false
            val stream=object:java.io.ByteArrayInputStream(bytes){override fun close(){closed=true;super.close()}}
            val port=StringPrep(stream);assertTrue(closed)
            val source=Source(bytes.inputStream())
            for(input in listOf("User Name", "Straße", "a\u00a0b", "א1א"))for(options in 0..1)assertEquals(result{source.prepare(input,options)},result{port.prepare(input,options)},name)
        }
        for(profile in 0..13)assertSame(StringPrep.getInstance(profile),StringPrep.getInstance(profile))
    }
    @Test fun everyMappedOrDeletedCodePointInEveryProfile() {
        val names=listOf("rfc3491","rfc3530cs","rfc3530csci","rfc3491","rfc3530mixp","rfc3491","rfc3722","rfc3920node","rfc3920res","rfc4011","rfc4013","rfc4505","rfc4518","rfc4518ci")
        var checked=0
        for(profile in 0..13) {
            val bytes=com.ibm.icu.impl.ICUBinary.getRequiredData(names[profile]+".spp")
            com.ibm.icu.impl.StringPrepDataReader(bytes).readIndexes(16)
            val trie=com.ibm.icu.impl.CharTrie(bytes,null)
            val source=Source.getInstance(profile);val port=StringPrep.getInstance(profile)
            for(cp in 0..0x10ffff) {
                val word=trie.getCodePointValue(cp).code
                if(word !in 1..0xffef)continue
                val input=String(Character.toChars(cp))
                assertEquals(result{source.prepare(input,1)},result{port.prepare(input,1)},"profile=$profile cp=${cp.toString(16)}")
                checked++
            }
        }
        assertEquals(10436,checked,"All pinned profile mappings must be checked")
    }
}
