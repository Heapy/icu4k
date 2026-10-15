// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import com.ibm.icu.util.LocalePriorityList as Source
import com.ibm.icu.util.ULocale as SourceLocale
import kotlin.random.Random
import kotlin.test.*

class LocalePriorityListOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is NumberFormatException -> "number-format"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun state(a: Source,b: LocalePriorityList) {
        assertEquals(a.toString(),b.toString()); assertEquals(a.hashCode(),b.hashCode())
        assertEquals(a.map { it?.toString() },b.map { it?.toString() })
        for(locale in a) assertEquals(a.getWeight(locale)?.toBits(),b.getWeight(locale?.let { ULocale(it.toString()) })?.toBits())
        assertEquals(a.uLocales.map { it?.toString() },b.getULocales().map { it?.toString() })
    }
    @Test fun lenientGrammarPartialFailuresAndWeightRepresentations() {
        val cases=listOf<String?>(null,"",",,,","en, fr;q=.8, en;q=.5", "en;q=.5, de;q=.5", "de;q=0.3, ja;q=0.3, en, fr;q=0.7, de", "\t en ; q = .5 , de;q=0x1p-2, fr;q=1f ,", "en;q=NaN", "en;q=Infinity", "en;q=-Infinity", "en;q=-.01", "en;q=1.01", "en;q=", "en;Q=.4", "en;q = .4", "en;q=.4;x=y", "en;q=.5;q=.25", "fr, en;q=NaN, ja", "en;q=1e-300", "en;q=1e-999", "en;q=0x0.0000000000001p-1022", "en\u00a0;q=.5", "en;q=.5\u00a0", "en,\u2003de", "\u0000en\u0000")
        for(s in cases) for(preserve in listOf(false,true)) {
            val a=Source.add("ja;q=.7"); val b=LocalePriorityList.add("ja;q=.7")
            assertEquals(outcome { a.add(s); Unit },outcome { b.add(s); Unit },s)
            val x=a.build(preserve); val y=b.build(preserve); state(x,y)
            assertSame(x,a.build(!preserve)); assertSame(y,b.build(!preserve))
        }
    }
    @Test fun randomizedReusableBuildersAndListCopies() {
        val random=Random(78136)
        val weights=listOf(-1.0,-0.0,0.0,0.1,0.3,0.5,0.7,1.0,2.0,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,Double.MIN_VALUE)
        val locales=listOf<String?>(null,"en","en_US","de","fr","ja","zh_Hant","")
        repeat(80) {
            var a=Source.add(); var b=LocalePriorityList.add()
            var oldA=a.build(); var oldB=b.build()
            repeat(70) {
                val locale=locales.random(random); val weight=weights.random(random); val preserve=random.nextBoolean()
                val al=locale?.let(::SourceLocale); val bl=locale?.let(::ULocale)
                assertEquals(outcome { a.add(al,weight); Unit },outcome { b.add(bl,weight); Unit })
                val x=a.build(preserve); val y=b.build(preserve); state(x,y); state(oldA,oldB)
                assertSame(x,a.build(!preserve)); assertSame(y,b.build(!preserve))
                if(random.nextInt(5)==0) {
                    var ax: Source.Builder?=null; var by: LocalePriorityList.Builder?=null
                    assertEquals(outcome { ax=Source.add(x); Unit },outcome { by=LocalePriorityList.add(y); Unit })
                    if(ax!=null) { a=ax!!; b=by!!; assertSame(x,a.build()); assertSame(y,b.build()) }
                }
                oldA=x; oldB=y
            }
        }
    }
    @Test fun equalityOrderingAndImmutableSetViews() {
        val a=Source.add("en;q=.3, de;q=.3").build(true); val b=LocalePriorityList.add("en;q=.3, de;q=.3").build(true)
        val ar=Source.add("de;q=.3, en;q=.3").build(true); val br=LocalePriorityList.add("de;q=.3, en;q=.3").build(true)
        assertEquals(a==ar,b==br); assertEquals(a.hashCode(),b.hashCode())
        assertFalse(b.equals(null)); assertFalse(b.equals("en")); assertSame(b.getULocales(),b.getULocales())
        val x=a.uLocales; val y=b.getULocales() as MutableSet<ULocale?>
        assertEquals(outcome { x.remove(SourceLocale("absent")) },outcome { y.remove(ULocale("absent")) })
        assertEquals(outcome { x.addAll(emptyList()) },outcome { y.addAll(emptyList()) })
        assertEquals(outcome { x.retainAll(x) },outcome { y.retainAll(y) })
        assertEquals(outcome { x.clear() },outcome { y.clear() })
        assertEquals(outcome { a.iterator().remove() },outcome { (b.iterator() as MutableIterator<*>).remove() })
        state(a,b)
    }
}
