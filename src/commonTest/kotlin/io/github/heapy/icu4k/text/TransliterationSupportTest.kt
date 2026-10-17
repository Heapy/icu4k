package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class TransliterationSupportTest {
    @Test fun mutableCaseContextMatchesIndependentSourceFingerprint() {
        var hash=1
        fun add(value:Any?) {hash=31*hash+(value?.toString()?.hashCode() ?: 0)}
        val texts=listOf("","A😀Σ","I\u0301Σ",charArrayOf('a','\ud800','b','\udc00').concatToString())
        for(input in texts) for(start in 0..input.length) for(limit in -1..input.length+1) for(direction in -1..1) {
            val iterator=ReplaceableContextIterator();val text=ReplaceableString(input)
            iterator.setText(text);iterator.setIndex(start);iterator.setLimit(limit);iterator.setContextLimits(start-1,limit+1)
            repeat(2) {pass ->
                val cp=iterator.nextCaseMapCP();add(cp);add(iterator.getCaseMapCPStart());iterator.reset(direction)
                repeat(input.length+3) {add(iterator.next());add(iterator.didReachLimit())}
                if(cp>=0)add(iterator.replace(if(pass==0)"XY" else ""))
                add(text.toString())
            }
        }
        assertEquals(1956853506,hash)
    }
    private fun sourceTargetFingerprint(mode:Int):Int {
        var calls=0
        val normalizer=when(mode) {1->Normalizer2.getNFCInstance();2->Normalizer2.getNFKCInstance();3->Normalizer2.getNFDInstance();else->null}
        val transform=Transform<String,String> {s -> calls++;when(mode) {0->UCharacter.toLowerCase(ULocale.US,s);1->Normalizer2.getNFCInstance().normalize(s);2->Normalizer2.getNFKCInstance().normalize(s);else->s}}
        val utility=SourceTargetUtility(transform,normalizer)
        var hash=1
        fun add(value:Any?) {hash=31*hash+(value?.toString()?.hashCode() ?: 0)}
        add(utility.sourceCache.toPattern(false));add(utility.sourceCache.isFrozen())
        for(s in utility.sourceStrings.sorted())add(s)
        add(calls)
        for(internal in listOf("[A-Za-zÀ-ÿΣΙİß\\u0300-\\u036f]","[a-z]","[]")) for(external in listOf("[A-ZÀ-ÿΣİß\\u0300-\\u036f]","[:any:]")) {
            val source=UnicodeSet("[z{zz}]");val target=UnicodeSet("[0{abc}]")
            utility.addSourceTargetSet({UnicodeSet(it).retainAll(UnicodeSet(internal)).freeze()},UnicodeSet(external),source,target)
            add(source.toPattern(false));add(target.toPattern(false))
        }
        add(calls)
        return hash
    }
    // Values generated in a standalone Java program linked only to pinned ICU4J 78.3.
    @Test fun lowerSourceTargetCacheMatchesIcu() {assertEquals(1071868851,sourceTargetFingerprint(0))}
    @Test fun nfcSourceTargetCacheMatchesIcu() {assertEquals(-597667599,sourceTargetFingerprint(1))}
    @Test fun nfkcSourceTargetCacheMatchesIcu() {assertEquals(-572370579,sourceTargetFingerprint(2))}
    @Test fun inertAndDecompositionCacheMatchesIcu() {assertEquals(-84666571,sourceTargetFingerprint(3))}
    @Test fun characterIteratorSharesTextAndPreservesStaleCloneBounds() {
        val text=ReplaceableString("A😀B")
        val iterator=ReplaceableCharacterIterator(text,1,4,1)
        val clone=iterator.clone()
        assertEquals(iterator,clone)
        assertEquals('\ud83d',iterator.current());assertEquals('\ude00',iterator.next())
        assertNotEquals(iterator,clone)
        text.replace(1,3,"XY")
        assertEquals('X',clone.current())
        assertEquals('Y',iterator.current())
        text.replace(0,4,"")
        val stale=iterator.clone()
        assertEquals(4,stale.getEndIndex())
        assertFailsWith<IndexOutOfBoundsException>{ stale.first() }
        iterator.setText(ReplaceableString("Q"))
        assertEquals(0,iterator.getBeginIndex());assertEquals(1,iterator.getEndIndex());assertEquals('Q',iterator.first())
        assertEquals(CharacterIterator.DONE,iterator.next());assertEquals('Q',iterator.previous())
        assertFailsWith<NullPointerException>{iterator.setText(null)}
        assertEquals('Q',iterator.current())
    }
    @Test fun transformContractsSupportGenericAndStringCalls() {
        val generic=Transform<Int,String> {"value:$it"}
        assertEquals("value:42",generic.transform(42))
        val string=StringTransform {it.reversed()}
        val base:Transform<String,String> = string
        assertEquals("cba",base.transform("abc"))
    }
}
