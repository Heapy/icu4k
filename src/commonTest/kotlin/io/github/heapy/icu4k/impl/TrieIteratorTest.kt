package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.RangeValueIterator
import kotlin.test.*

class TrieIteratorTest {
    @Test fun independentRangeAndExtractTraceGoldens() {
        // Independently generated from ICU4J 78.3 by scripts/oracles/TrieIteratorGoldens.java.
        val rows="""
rfc3491|0|2309|-1417428889|11424|-16474494
rfc3491|1|944|193381384|10618|-248362436
rfc3491|2|1|-964281535|20735|-1468018113
rfc3491|3|1835|-703889461|11362|-1814551978
rfc3530cs|0|813|727848040|9639|1960619833
rfc3530cs|1|48|1110624941|8670|-2059727791
rfc3530cs|2|1|-964281535|16831|2069172511
rfc3530cs|3|776|-1973284237|9639|1960619833
rfc3530csci|0|2290|-193575962|11106|-1426688625
rfc3530csci|1|912|1984845626|10332|44210105
rfc3530csci|2|1|-964281535|20223|404557599
rfc3530csci|3|1826|2138232912|11106|-1426688625
rfc3530mixp|0|832|1796843254|9944|1924650754
rfc3530mixp|1|80|1662322364|8859|1455433710
rfc3530mixp|2|1|-964281535|17343|1471580095
rfc3530mixp|3|778|-441066439|9851|1347435138
rfc3722|0|2316|167879257|11502|2007980298
rfc3722|1|953|-772173419|10665|1907627354
rfc3722|2|1|-964281535|20865|-260100743
rfc3722|3|1840|580226233|11440|1767069238
rfc3920node|0|2323|1754870391|11490|49747638
rfc3920node|1|959|-1969612177|10653|-394828626
rfc3920node|2|1|-964281535|20865|-50066071
rfc3920node|3|1848|1303503085|11428|-316785950
rfc3920res|0|833|-830537414|10008|-120606462
rfc3920res|1|81|1446468352|8923|-138319670
rfc3920res|2|1|-964281535|17409|-699496891
rfc3920res|3|779|-1504368483|9915|-533646498
rfc4011|0|822|689120052|9786|1409687443
rfc4011|1|65|-1291838830|8670|-220184721
rfc4011|2|1|-964281535|17089|429995639
rfc4011|3|781|606182487|9786|1409687443
rfc4013|0|846|-1547092104|10008|-1762979078
rfc4013|1|76|-778943230|8923|-153181570
rfc4013|2|1|-964281535|17409|462113097
rfc4013|3|783|25743287|9915|1775759358
rfc4505|0|813|-1458657545|9785|-402730925
rfc4505|1|63|-398576373|8669|-1953203221
rfc4505|2|1|-964281535|17025|-876561265
rfc4505|3|771|920140644|9785|-402730925
rfc4518|0|854|-1881916970|9986|-1005498099
rfc4518|1|54|680455613|9117|287728177
rfc4518|2|1|-964281535|17409|-887139129
rfc4518|3|797|249464997|9893|-829731905
rfc4518ci|0|2331|1390465256|11465|1620390939
rfc4518ci|1|918|-798126006|10779|-954984955
rfc4518ci|2|1|-964281535|20801|-499808785
rfc4518ci|3|1628|17483949|11465|1620390939
""".trim().lines()
        assertEquals(48,rows.size)
        for(row in rows) {
            val fields=row.split('|');val mode=fields[1].toInt()
            val bytes=ICUBinary.getRequiredData(fields[0]+".spp");StringPrepDataReader(bytes).readIndexes(16)
            val trie=CharTrie(bytes,null);val trace=intArrayOf(0,1)
            val iterator=object:TrieIterator(trie) {
                override fun extract(value: Int): Int {
                    trace[0]++;trace[1]=31*trace[1]+value
                    return when(mode){1->value and 3;2->0;3->value ushr 8;else->value}
                }
            }
            val element=RangeValueIterator.Element();var count=0;var hash=1
            while(iterator.next(element)) {
                count++;hash=31*hash+element.start;hash=31*hash+element.limit;hash=31*hash+element.value
            }
            val last=listOf(element.start,element.limit,element.value)
            assertFalse(iterator.next(element));assertEquals(last,listOf(element.start,element.limit,element.value))
            iterator.reset();assertTrue(iterator.next(element))
            hash=31*hash+element.start;hash=31*hash+element.limit;hash=31*hash+element.value
            assertEquals(fields[2].toInt(),count,row);assertEquals(fields[3].toInt(),hash,row)
            assertEquals(fields[4].toInt(),trace[0],row);assertEquals(fields[5].toInt(),trace[1],row)
        }
    }
    @Test fun reusableSubclassedElementAndReset() {
        class Element : RangeValueIterator.Element()
        val iterator=TrieIterator(CharTrie(17,19){0});val element=Element()
        assertTrue(iterator.next(element));assertEquals(listOf(0,0x110000,17),listOf(element.start,element.limit,element.value))
        element.start=-7;element.limit=-8;element.value=-9
        repeat(3){assertFalse(iterator.next(element));assertEquals(listOf(-7,-8,-9),listOf(element.start,element.limit,element.value))}
        iterator.reset();assertTrue(iterator.next(element));assertEquals(0,element.start)
        assertEquals("Argument trie cannot be null",assertFailsWith<IllegalArgumentException>{TrieIterator(null)}.message)
    }
}
