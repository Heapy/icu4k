// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.test.*

class LocalePriorityListTest {
    @Test fun orderingWeightsDuplicateRemovalAndBuilderReuse() {
        val builder = LocalePriorityList.add("de;q=0.3, ja;q=0.3, en, fr;q=0.7, de")
        val first = builder.build(true)
        assertEquals("en, de, fr;q=0.7, ja;q=0.3",first.toString())
        assertSame(first,builder.build(false))
        builder.add(ULocale.ENGLISH,0.0).add(ULocale.JAPANESE,2.0)
        assertEquals("de, ja, fr",builder.build().toString())
        assertEquals(listOf("en","de","fr","ja"),first.map { it.toString() })
        assertEquals(0.3,first.getWeight(ULocale.JAPANESE))
        assertSame(first.getULocales(),first.getULocales())
        assertFailsWith<UnsupportedOperationException> { (first.getULocales() as MutableSet<ULocale?>).clear() }
        assertFailsWith<UnsupportedOperationException> { (first.iterator() as MutableIterator<*>).remove() }
    }
    @Test fun uniformWeightQuirkAndIdentityFreeEquality() {
        val a = LocalePriorityList.add("en;q=.5, de;q=.5").build(false)
        assertEquals("en;q=0.5, de;q=0.5",a.toString())
        val b = LocalePriorityList.add("de;q=.5, en;q=.5").build(true)
        assertEquals(a,b); assertEquals(a.hashCode(),b.hashCode())
        assertNotEquals(a,LocalePriorityList.add("en, de").build())
        val tiny = LocalePriorityList.add(ULocale.ENGLISH,1e-7).build(true)
        assertEquals("en;q=1.0E-7",tiny.toString())
        val bits = 1e-7.toBits()
        assertEquals(ULocale.ENGLISH.hashCode() xor (bits xor (bits ushr 32)).toInt(),tiny.hashCode())
    }
    @Test fun asciiWhitespaceParsingFailuresAndPartialMutation() {
        val b = LocalePriorityList.add(" \t en ; q = .5 ,,, fr;q=0x1p-2, de;q=1f, ")
        assertEquals("de, en;q=0.5, fr;q=0.25",b.build(true).toString())
        assertFailsWith<IllegalArgumentException> { b.add("ja;q=.2, en;q=NaN") }
        assertEquals(0.2,b.build(true).getWeight(ULocale.JAPANESE))
        for(s in listOf("en;q=-.1","en;q=1.1","en;q=Infinity","en;q=NaN","en;q=")) assertFails { LocalePriorityList.add(s) }
        assertEquals("en",LocalePriorityList.add(", en,,").build().toString())
    }
    @Test fun nullKeysNanAndNoWeightSorting() {
        val a = LocalePriorityList.add(null as ULocale?,0.5).add(ULocale.ENGLISH,Double.NaN).add(ULocale.GERMAN,1.0).build(true)
        assertEquals("en;q=NaN, de, null;q=0.5",a.toString())
        assertEquals(0.5,a.getWeight(null)); assertTrue(a.getWeight(ULocale.ENGLISH)!!.isNaN())
        val b = LocalePriorityList.add(null as ULocale?,0.5).add(ULocale.GERMAN,1.0).add(ULocale.ENGLISH,Double.NaN).build(true)
        assertEquals(a,b); assertEquals(a.hashCode(),b.hashCode())
        assertEquals(2,LocalePriorityList.add(ULocale.ENGLISH,Double.NaN).add(ULocale.GERMAN,Double.NaN).build().getULocales().size)
    }
}
