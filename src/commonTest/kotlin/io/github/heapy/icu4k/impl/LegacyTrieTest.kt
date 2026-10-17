package io.github.heapy.icu4k.impl

import kotlin.test.*

class LegacyTrieTest {
    @Test fun independentFullUnicodeFingerprints() {
        // Generated from ICU4J 78.3 using scripts/oracles/TrieGoldens.java.
        val rows="""
rfc3491|427906612|-1954866866|-2079155634|181050363|17928|false
rfc3530cs|468410603|-729454043|-853742811|-2132044595|13024|false
rfc3530csci|2120945115|-487088331|-611377099|98904703|17392|false
rfc3530mixp|500838592|-239124954|-363413722|-2132044595|13552|false
rfc3722|826189492|-695093042|-819381810|181050363|18136|false
rfc3920node|-847292996|1199245510|1074956742|181050363|18136|false
rfc3920res|-482596082|1580057112|1455768344|-2132044595|13616|false
rfc4011|-1375059435|435270843|310982075|-2132044595|13224|false
rfc4013|1777979852|67624462|-56664306|-2132044595|13616|false
rfc4505|1435057289|305109959|180821191|1229958097|13096|false
rfc4518|1398084589|854917435|730628667|-2132044595|13792|false
rfc4518ci|-1606706727|-1979470249|-2103759017|1063944899|18168|false
""".trim().lines()
        for(row in rows) {
            val fields=row.split('|');val bytes=ICUBinary.getRequiredData(fields[0]+".spp")
            val reader=StringPrepDataReader(bytes);reader.readIndexes(16);val trie=CharTrie(bytes,null)
            var points=1;var bmp=1;var lead=1;var latin=1
            for(cp in -1..0x110000)points=31*points+trie.getCodePointValue(cp).code
            for(cp in 0..65535){bmp=31*bmp+trie.getBMPValue(cp.toChar()).code;lead=31*lead+trie.getLeadValue(cp.toChar()).code}
            for(cp in 0..255)latin=31*latin+trie.getLatin1LinearValue(cp.toChar()).code
            assertEquals(fields[1].toInt(),points,row);assertEquals(fields[2].toInt(),bmp,row)
            assertEquals(fields[3].toInt(),lead,row);assertEquals(fields[4].toInt(),latin,row)
            assertEquals(fields[5].toInt(),trie.getSerializedDataSize(),row)
            assertEquals(fields[6].toBooleanStrict(),trie.isLatin1Linear(),row)
        }
    }
    @Test fun dummyFormatsRetainLeadUnitValuesAndValidation() {
        val char=CharTrie(17,19){0};val int=IntTrie(17,19){0}
        assertEquals(17,char.getBMPValue('\ud800').code);assertEquals(19,char.getLeadValue('\ud800').code)
        assertEquals(17,int.getBMPValue('\ud800'));assertEquals(19,int.getLeadValue('\ud800'))
        assertEquals(17,char.getCodePointValue(0x10000).code);assertEquals(17,int.getCodePointValue(0x10000))
        assertEquals(17,char.getCodePointValue(-1).code);assertEquals(17,int.getCodePointValue(0x110000))
        assertEquals(17,char.getSurrogateValue('a','b').code)
        assertFailsWith<IllegalArgumentException>{int.getSurrogateValue('a','b')}
        assertFailsWith<IndexOutOfBoundsException>{char.getLatin1LinearValue('a')}
        assertEquals(17,int.getLatin1LinearValue('a'))
        assertEquals(CharTrie(17,19){0},char);assertEquals(IntTrie(17,19){0},int)
    }
}
