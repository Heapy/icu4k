package io.github.heapy.icu4k.text

import com.ibm.icu.text.CharsetDetector as SourceDetector
import kotlin.random.Random
import kotlin.test.*

class CharsetUnicodeRecognizerOracleTest {
    private val recognizers=listOf(
        "CharsetRecog_UTF8" to CharsetRecog_UTF8(),
        "CharsetRecog_Unicode\$CharsetRecog_UTF_16_BE" to CharsetRecog_Unicode.CharsetRecog_UTF_16_BE(),
        "CharsetRecog_Unicode\$CharsetRecog_UTF_16_LE" to CharsetRecog_Unicode.CharsetRecog_UTF_16_LE(),
        "CharsetRecog_Unicode\$CharsetRecog_UTF_32_BE" to CharsetRecog_Unicode.CharsetRecog_UTF_32_BE(),
        "CharsetRecog_Unicode\$CharsetRecog_UTF_32_LE" to CharsetRecog_Unicode.CharsetRecog_UTF_32_LE(),
        "CharsetRecog_2022\$CharsetRecog_2022JP" to CharsetRecog_2022.CharsetRecog_2022JP(),
        "CharsetRecog_2022\$CharsetRecog_2022KR" to CharsetRecog_2022.CharsetRecog_2022KR(),
        "CharsetRecog_2022\$CharsetRecog_2022CN" to CharsetRecog_2022.CharsetRecog_2022CN(),
    )
    private val sourceRecognizers=recognizers.map{(name,_)->Class.forName("com.ibm.icu.text.$name").getDeclaredConstructor().apply{isAccessible=true}.newInstance()}
    private val match=Class.forName("com.ibm.icu.text.CharsetRecognizer").getDeclaredMethod("match",SourceDetector::class.java).apply{isAccessible=true}
    private val fields=listOf("fRawLength","fInputBytes","fInputLen").associateWith{SourceDetector::class.java.getDeclaredField(it).apply{isAccessible=true}}
    private fun compare(raw: ByteArray,length: Int,filtered: ByteArray=raw,filteredLength: Int=filtered.size) {
        val detector=SourceDetector().setText(raw)
        fields.getValue("fRawLength").setInt(detector,length);fields.getValue("fInputBytes").set(detector,filtered);fields.getValue("fInputLen").setInt(detector,filteredLength)
        val input=CharsetDetectionInput(raw,length,filtered,filteredLength)
        for(i in recognizers.indices) {
            val source=match.invoke(sourceRecognizers[i],detector) as com.ibm.icu.text.CharsetMatch?
            val expected=source?.let{CharsetCandidate(it.name,it.confidence,it.language)}
            assertEquals(expected,recognizers[i].second.match(input),"${recognizers[i].first} length=$length filtered=$filteredLength raw=${raw.joinToString{(it.toInt() and 255).toString(16)}}")
        }
    }
    @Test fun deterministicRandomRawLengthsAndFilteredViews() {
        val random=Random(2022)
        repeat(3000) {
            val raw=ByteArray(random.nextInt(129)){random.nextInt(256).toByte()}
            val length=random.nextInt(raw.size+1)
            val filtered=ByteArray(random.nextInt(65)){if(random.nextBoolean())random.nextInt(256).toByte()else listOf(27,14,15,36,40,41,42,43,65,66,67,78,79)[random.nextInt(13)].toByte()}
            compare(raw,length,filtered,random.nextInt(filtered.size+1))
        }
    }
    @Test fun unicodeBomMalformedSequencesAndEscapes() {
        val cases=mutableListOf(byteArrayOf(),byteArrayOf(0),"plain ASCII sample\n".encodeToByteArray(),"Привет 中文 😀".encodeToByteArray())
        for(hex in listOf("efbbbf","efbbbfc0","c080","eda080","f48fbfbf","f4908080","e282","fffe","feff","fffe0000","0000feff","0010fffe","0010ffff","00110000","0000d800","80000000"))cases+=hex.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
        for(encoding in listOf("UTF-16BE","UTF-16LE","UTF-32BE","UTF-32LE"))cases+="Hello world! Привет 😀".toByteArray(java.nio.charset.Charset.forName(encoding))
        for(tail in listOf("\u001b\u0024\u0028C","\u001b\u0024\u0029C","\u001b\u0024\u0029A","\u001bN","\u001bO","\u001bBAD"))for(repeat in 1..7)cases+=(tail.repeat(repeat)+"\u000e\u000f").encodeToByteArray()
        for(raw in cases)for(length in listOf(0,1,2,3,4,raw.size/2,raw.size).filter{it<=raw.size}.distinct())compare(raw,length)
        for(raw in cases)compare(raw.copyOf(8000),raw.size,raw,raw.size)
    }
}
