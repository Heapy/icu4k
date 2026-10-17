package io.github.heapy.icu4k.text

import com.ibm.icu.text.CharsetDetector as SourceDetector
import kotlin.random.Random
import kotlin.test.*

class CharsetMbcsRecognizerOracleTest {
    private val names=listOf("CharsetRecog_sjis","CharsetRecog_big5","CharsetRecog_euc\$CharsetRecog_euc_jp","CharsetRecog_euc\$CharsetRecog_euc_kr","CharsetRecog_gb_18030")
    private val ports=listOf(CharsetRecog_mbcs.CharsetRecog_sjis(),CharsetRecog_mbcs.CharsetRecog_big5(),CharsetRecog_mbcs.CharsetRecog_euc.CharsetRecog_euc_jp(),CharsetRecog_mbcs.CharsetRecog_euc.CharsetRecog_euc_kr(),CharsetRecog_mbcs.CharsetRecog_gb_18030())
    private val sources=names.map {Class.forName("com.ibm.icu.text.CharsetRecog_mbcs\$$it").getDeclaredConstructor().apply{isAccessible=true}.newInstance()}
    private val base=Class.forName("com.ibm.icu.text.CharsetRecog_mbcs")
    private val iterType=Class.forName("com.ibm.icu.text.CharsetRecog_mbcs\$iteratedChar")
    private val iterConstructor=iterType.getDeclaredConstructor().apply{isAccessible=true}
    private val next=base.getDeclaredMethod("nextChar",iterType,SourceDetector::class.java).apply{isAccessible=true}
    private val match=Class.forName("com.ibm.icu.text.CharsetRecognizer").getDeclaredMethod("match",SourceDetector::class.java).apply{isAccessible=true}
    private val fields=listOf("charValue","nextIndex","error","done").map{iterType.getDeclaredField(it).apply{isAccessible=true}}
    private val rawLength=SourceDetector::class.java.getDeclaredField("fRawLength").apply{isAccessible=true}
    private fun compare(raw:ByteArray,length:Int=raw.size,trace:Boolean=true) {
        val source=SourceDetector().setText(raw);rawLength.setInt(source,length)
        val input=CharsetDetectionInput(raw,length)
        for(i in ports.indices) {
            val result=match.invoke(sources[i],source) as com.ibm.icu.text.CharsetMatch?
            assertEquals(result?.let{CharsetCandidate(it.name,it.confidence,it.language)},ports[i].match(input),"${names[i]} length=$length")
            if(!trace)continue
            val si=iterConstructor.newInstance();val pi=CharsetRecog_mbcs.IteratedChar();var count=0
            while(true) {
                val more=next.invoke(sources[i],si,source) as Boolean
                assertEquals(more,ports[i].nextChar(pi,input),"${names[i]} length=$length")
                assertEquals(fields.map{it.get(si)},listOf(pi.charValue,pi.nextIndex,pi.error,pi.done),"${names[i]} length=$length")
                if(!more)break
                assertTrue(++count<=raw.size+1)
            }
        }
    }
    @Test fun randomMalformedInputAndTruncatedState() {
        val random=Random(18030)
        repeat(2500) {
            val raw=ByteArray(random.nextInt(129)){random.nextInt(256).toByte()}
            compare(raw,random.nextInt(raw.size+1))
        }
        for(hex in listOf("","81","8130","813081","81308130","817f","ff00","ffff","8f20ff","8ea0","8fff","a1ff","80ff")) {
            val raw=hex.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
            for(length in 0..raw.size)compare(raw,length)
            compare(raw.copyOf(8000),raw.size)
        }
    }
    @Test fun frequencyTablesEncodedTextAndConfidenceBoundaries() {
        for((i,table) in listOf(CharsetMbcsTables.sjis,CharsetMbcsTables.big5,CharsetMbcsTables.euc_jp,CharsetMbcsTables.euc_kr,CharsetMbcsTables.gb_18030).withIndex()) {
            val field=sources[i].javaClass.getDeclaredField("commonChars").apply{isAccessible=true}
            assertContentEquals(field.get(null) as IntArray,table)
        }
        for(encoding in listOf("Shift_JIS","Big5","EUC-JP","EUC-KR","GB18030")) {
            val text="日本語の文章です。中文測試文字符號，한국어 문장입니다. ".repeat(40)
            val raw=text.toByteArray(java.nio.charset.Charset.forName(encoding));compare(raw,trace=false)
            for(length in 0..minOf(64,raw.size))compare(raw,length)
        }
        for(size in listOf(0,1,9,10,11,12,16,20,31,32,64,100,128,256,512,1024,4096))for(common in listOf(0,1,2,3,4,7,11,size/4,size/2,size-1,size).filter{it in 0..size}.distinct()) {
            val raw=ByteArray(size*2){index->if(index%2==0)0x81.toByte()else if(index/2<common)0x40.toByte()else 0x90.toByte()}
            compare(raw,trace=false)
            compare(raw+byteArrayOf(0x81.toByte(),0,0x81.toByte(),0),trace=false)
        }
    }
    @Test fun scoringWithoutFrequencyTableAndIteratorReset() {
        val scoring=base.getDeclaredMethod("match",SourceDetector::class.java,IntArray::class.java).apply{isAccessible=true}
        val parser=CharsetRecog_mbcs.CharsetRecog_sjis()
        val port=object:CharsetRecog_mbcs("custom","xx",null) {
            override fun nextChar(iterator:IteratedChar,input:CharsetDetectionInput)=parser.nextChar(iterator,input)
        }
        for(count in listOf(0,1,9,10,11,20,25,50,100))for(errors in 0..3) {
            val raw=ByteArray(count*2){if(it%2==0)0x81.toByte()else 0x40.toByte()}+ByteArray(errors*2){if(it%2==0)0x81.toByte()else 0}
            assertEquals(scoring.invoke(sources[0],SourceDetector().setText(raw),null),port.match(CharsetDetectionInput(raw))?.confidence?:0)
        }
        val source=SourceDetector().setText(byteArrayOf(0x81.toByte()))
        val si=iterConstructor.newInstance();val pi=CharsetRecog_mbcs.IteratedChar()
        assertEquals(next.invoke(sources[0],si,source),parser.nextChar(pi,CharsetDetectionInput(byteArrayOf(0x81.toByte()))))
        iterType.getDeclaredMethod("reset").apply{isAccessible=true}.invoke(si);pi.reset()
        assertEquals(fields.map{it.get(si)},listOf(pi.charValue,pi.nextIndex,pi.error,pi.done))
    }
}
