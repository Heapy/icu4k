package io.github.heapy.icu4k.text

import kotlin.test.*

class CharsetUnicodeRecognizerTest {
    @Test fun independentUnicodeAndEscapeConfidenceGoldens() {
        val recognizers=listOf(CharsetRecog_UTF8(),CharsetRecog_Unicode.CharsetRecog_UTF_16_BE(),CharsetRecog_Unicode.CharsetRecog_UTF_16_LE(),
            CharsetRecog_Unicode.CharsetRecog_UTF_32_BE(),CharsetRecog_Unicode.CharsetRecog_UTF_32_LE(),
            CharsetRecog_2022.CharsetRecog_2022JP(),CharsetRecog_2022.CharsetRecog_2022KR(),CharsetRecog_2022.CharsetRecog_2022CN())
        val names=listOf("UTF-8","UTF-16BE","UTF-16LE","UTF-32BE","UTF-32LE","ISO-2022-JP","ISO-2022-KR","ISO-2022-CN")
        // Independent source output: scripts/oracles/CharsetRecognizerGoldens.java, ICU4J 78.3.
        val rows="""
|false|15,0,0,0,0,0,0,0
|true|15,0,0,0,0,0,0,0
00|false|15,0,0,0,0,0,0,0
00|true|15,0,0,0,0,0,0,0
61626364|false|15,10,10,0,0,0,0,0
61626364|true|15,0,0,0,0,0,0,0
efbbbf|false|100,0,0,0,0,0,0,0
efbbbf|true|100,0,0,0,0,0,0,0
efbbbfc0|false|100,10,10,0,0,0,0,0
efbbbfc0|true|100,0,0,0,0,0,0,0
c080|false|80,0,0,0,0,0,0,0
c080|true|80,0,0,0,0,0,0,0
eda080|false|80,0,0,0,0,0,0,0
eda080|true|80,0,0,0,0,0,0,0
f48fbfbf|false|80,10,10,0,0,0,0,0
f48fbfbf|true|80,0,0,0,0,0,0,0
f4908080|false|80,10,10,0,0,0,0,0
f4908080|true|80,0,0,0,0,0,0,0
e282|false|15,0,0,0,0,0,0,0
e282|true|15,0,0,0,0,0,0,0
fffe|false|0,0,100,0,0,0,0,0
fffe|true|0,0,100,0,0,0,0,0
feff|false|0,100,0,0,0,0,0,0
feff|true|0,100,0,0,0,0,0,0
fffe0000|false|0,0,100,0,100,0,0,0
fffe0000|true|0,0,100,0,100,0,0,0
0000feff|false|0,0,0,100,0,0,0,0
0000feff|true|0,0,0,100,0,0,0,0
0010fffe|false|0,10,10,80,0,0,0,0
0010fffe|true|0,0,0,80,0,0,0,0
0010ffff|false|0,10,10,0,0,0,0,0
0010ffff|true|0,0,0,0,0,0,0,0
00110000|false|15,0,0,0,80,0,0,0
00110000|true|15,0,0,0,80,0,0,0
0000d800|false|0,0,0,0,0,0,0,0
0000d800|true|0,0,0,0,0,0,0,0
80000000|false|0,0,10,0,80,0,0,0
80000000|true|0,0,0,0,80,0,0,0
004100420043004400450046|false|15,70,10,0,0,0,0,0
004100420043004400450046|true|15,0,0,0,0,0,0,0
410042004300440045004600|false|15,10,70,0,0,0,0,0
410042004300440045004600|true|15,0,0,0,0,0,0,0
1b242843|false|15,10,10,0,0,60,0,0
1b242843|true|15,0,0,0,0,60,0,0
1b242943|false|15,10,10,0,0,0,60,0
1b242943|true|15,0,0,0,0,0,60,0
1b242941|false|15,10,10,0,0,0,0,60
1b242941|true|15,0,0,0,0,0,0,60
1b4e|false|15,0,0,0,0,0,0,60
1b4e|true|15,0,0,0,0,0,0,60
1b4f|false|15,0,0,0,0,0,0,60
1b4f|true|15,0,0,0,0,0,0,60
1b2428430e0f0e0f|false|15,10,10,0,0,100,0,0
1b2428430e0f0e0f|true|15,0,0,0,0,100,0,0
1b2428431b2428431b2428431b2428431b242843|false|15,10,10,0,0,100,0,0
1b2428431b2428431b2428431b2428431b242843|true|15,0,0,0,0,100,0,0
1b2428431b424144|false|15,10,10,0,0,0,0,0
1b2428431b424144|true|15,0,0,0,0,0,0,0
efbbbfe282ac|false|100,10,10,0,0,0,0,0
efbbbfe282ac|true|100,0,0,0,0,0,0,0
f09f9880|false|80,10,10,0,0,0,0,0
f09f9880|true|80,0,0,0,0,0,0,0
c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1ff|false|25,10,10,0,0,0,0,0
c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1ff|true|25,0,0,0,0,0,0,0
efbbbfc2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1ff|false|80,10,10,0,0,0,0,0
efbbbfc2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1c2a1ff|true|80,0,0,0,0,0,0,0
000000410000004100000041000000410000004100000041000000410000004100000041000000410000004100000041ffffffff|false|0,0,0,25,0,0,0,0
000000410000004100000041000000410000004100000041000000410000004100000041000000410000004100000041ffffffff|true|0,0,0,25,0,0,0,0
0000feff000000410000004100000041000000410000004100000041000000410000004100000041000000410000004100000041ffffffff|false|0,0,0,80,0,0,0,0
0000feff000000410000004100000041000000410000004100000041000000410000004100000041000000410000004100000041ffffffff|true|0,0,0,80,0,0,0,0
410000004100000041000000410000004100000041000000410000004100000041000000410000004100000041000000ffffffff|false|0,0,20,0,25,0,0,0
410000004100000041000000410000004100000041000000410000004100000041000000410000004100000041000000ffffffff|true|0,0,20,0,25,0,0,0
fffe0000410000004100000041000000410000004100000041000000410000004100000041000000410000004100000041000000ffffffff|false|0,0,100,0,80,0,0,0
fffe0000410000004100000041000000410000004100000041000000410000004100000041000000410000004100000041000000ffffffff|true|0,0,100,0,80,0,0,0
1b2428431b2428431b2428431b424144|false|15,10,10,0,0,30,0,0
1b2428431b2428431b2428431b424144|true|15,0,0,0,0,30,0,0
1b2428431b2428431b2428431b2428431b2428431b424144|false|15,10,10,0,0,66,0,0
1b2428431b2428431b2428431b2428431b2428431b424144|true|15,0,0,0,0,66,0,0
""".trim().lines()
        assertEquals(78,rows.size)
        for(row in rows) {
            val fields=row.split('|');val exact=fields[0].chunked(2).map{it.toInt(16).toByte()}.toByteArray()
            val raw=if(fields[1].toBooleanStrict())exact.copyOf(8000) else exact
            val input=CharsetDetectionInput(raw,exact.size,exact,exact.size)
            val expected=fields[2].split(',').map{it.toInt()}
            for(i in recognizers.indices) {
                val match=recognizers[i].match(input)
                assertEquals(expected[i],match?.confidence?:0,"$i/$row")
                if(match!=null){assertEquals(names[i],match.name);assertNull(match.language)}
            }
        }
    }
}
