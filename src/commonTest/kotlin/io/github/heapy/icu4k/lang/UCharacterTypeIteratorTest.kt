package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.util.RangeValueIterator
import kotlin.test.*

class UCharacterTypeIteratorTest {
    @Test fun sourceFingerprintFullUnicodeCoverageResetAndExhaustion() {
        val iterator=UCharacter.getTypeIterator();val element=RangeValueIterator.Element()
        repeat(2) {
            var count=0;var hash=1;var limit=0;var previous=-1
            while(iterator.next(element)) {
                assertEquals(limit,element.start);assertTrue(element.limit>element.start)
                assertNotEquals(previous,element.value)
                for(cp in element.start until element.limit)assertEquals(UCharacter.getType(cp),element.value)
                limit=element.limit;previous=element.value;count++
                hash=31*hash+element.start;hash=31*hash+element.limit;hash=31*hash+element.value
            }
            // Independently generated from ICU4J 78.3 by scripts/oracles/TypeIteratorGolden.java.
            assertEquals(4144,count);assertEquals(-2096587558,hash);assertEquals(0x110000,limit)
            element.start=-1;element.limit=-2;element.value=-3
            repeat(4){assertFalse(iterator.next(element));assertEquals(listOf(-1,-2,-3),listOf(element.start,element.limit,element.value))}
            iterator.reset()
        }
        assertTrue(iterator.next(element));iterator.reset();assertTrue(iterator.next(element));assertEquals(0,element.start)
    }
}
