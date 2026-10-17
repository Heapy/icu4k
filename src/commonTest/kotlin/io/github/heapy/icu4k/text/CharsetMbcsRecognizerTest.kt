package io.github.heapy.icu4k.text

import kotlin.test.*

class CharsetMbcsRecognizerTest {
    @Test fun independentConfidenceAndIteratorStateGoldens() {
        val recognizers=listOf(CharsetRecog_mbcs.CharsetRecog_sjis(),CharsetRecog_mbcs.CharsetRecog_big5(),CharsetRecog_mbcs.CharsetRecog_euc.CharsetRecog_euc_jp(),CharsetRecog_mbcs.CharsetRecog_euc.CharsetRecog_euc_kr(),CharsetRecog_mbcs.CharsetRecog_gb_18030())
        val noTable=object:CharsetRecog_mbcs("custom","xx",null) {
            override fun nextChar(iterator:IteratedChar,input:CharsetDetectionInput)=recognizers[0].nextChar(iterator,input)
        }
        val names=listOf("Shift_JIS","Big5","EUC-JP","EUC-KR","GB18030");val languages=listOf("ja","zh","ja","ko","zh")
        assertEquals(573,charsetMbcsGoldenRows.size)
        for(row in charsetMbcsGoldenRows) {
            val fields=row.split('|')
            val raw: ByteArray;val length: Int
            if(fields[0]=="h") {
                val bytes=fields[1].chunked(2).map{it.toInt(16).toByte()}.toByteArray()
                raw=if(fields[3].toBooleanStrict())bytes.copyOf(8000) else bytes;length=fields[2].toInt()
            }else {
                val count=fields[1].toInt();val common=fields[2].toInt();val errors=fields[3].toInt()
                raw=ByteArray(2*(count+errors)){index->if(index%2==0)0x81.toByte()else if(index/2>=count)0 else if(index/2<common)0x40.toByte()else 0x90.toByte()}
                length=raw.size
            }
            val input=CharsetDetectionInput(raw,length)
            val scores=fields[4].split(',').map{it.toInt()};val traces=fields[5].split(',').map{it.toInt()}
            for(i in recognizers.indices) {
                val candidate=recognizers[i].match(input)
                assertEquals(scores[i],candidate?.confidence?:0,"score $i/$row")
                if(candidate!=null){assertEquals(names[i],candidate.name);assertEquals(languages[i],candidate.language)}
                val cursor=CharsetRecog_mbcs.IteratedChar();var hash=1
                while(true) {
                    val more=recognizers[i].nextChar(cursor,input)
                    hash=31*hash+if(more)1 else 0
                    hash=31*hash+cursor.charValue;hash=31*hash+cursor.nextIndex
                    hash=31*hash+if(cursor.error)1 else 0;hash=31*hash+if(cursor.done)1 else 0
                    if(!more)break
                }
                assertEquals(traces[i],hash,"trace $i/$row")
                cursor.reset();assertEquals(listOf(0,0,false,false),listOf(cursor.charValue,cursor.nextIndex,cursor.error,cursor.done))
            }
            assertEquals(scores[5],noTable.match(input)?.confidence?:0,"no table/$row")
        }
    }
    @Test fun largeInputFloatRoundingMatchesRealIcu4jRuns() {
        // Each source result was produced by detecting a 32 MiB byte array, not by copying the formula.
        // scripts/oracles/CharsetMbcsFloatGoldens.java; test the scoring stage without allocating it on every target.
        val rows="""
16777216|4194303|100
16777216|4194304|100
16777217|4194303|100
16777217|4194304|100
16777219|4194303|99
16777219|4194304|100
16777221|4194303|99
16777221|4194304|100
""".trim().lines()
        val recognizer=CharsetRecog_mbcs.CharsetRecog_sjis()
        for(row in rows) {
            val fields=row.split('|');val count=fields[0].toInt();val common=fields[1].toInt()
            assertEquals(fields[2].toInt(),recognizer.confidence(count,common,0,count),row)
        }
    }
}
