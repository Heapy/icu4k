package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.util.RangeValueIterator
import kotlin.test.*

class UCharacterTypeIteratorOracleTest {
    @Test fun everyTypeRangeMatchesSourceAcrossResetAndExhaustion() {
        val source=com.ibm.icu.lang.UCharacter.getTypeIterator();val port=UCharacter.getTypeIterator()
        val s=com.ibm.icu.util.RangeValueIterator.Element();val p=RangeValueIterator.Element()
        repeat(3) {
            while(true) {
                val more=source.next(s);assertEquals(more,port.next(p))
                assertEquals(listOf(s.start,s.limit,s.value),listOf(p.start,p.limit,p.value))
                if(!more)break
            }
            repeat(5){assertEquals(source.next(s),port.next(p));assertEquals(listOf(s.start,s.limit,s.value),listOf(p.start,p.limit,p.value))}
            source.reset();port.reset()
        }
        repeat(5){source.next(s);port.next(p)}
        source.reset();port.reset();assertEquals(source.next(s),port.next(p))
        assertEquals(listOf(s.start,s.limit,s.value),listOf(p.start,p.limit,p.value))
    }
}
