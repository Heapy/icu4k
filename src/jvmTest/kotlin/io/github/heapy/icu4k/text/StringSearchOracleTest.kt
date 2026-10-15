// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.StringSearch as Source
import com.ibm.icu.text.RuleBasedCollator as SourceCollator
import com.ibm.icu.text.SearchIterator.ElementComparisonType as SourceType
import kotlin.random.Random
import kotlin.test.*

class StringSearchOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NegativeArraySizeException -> "negative-size"; is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is ClassCastException -> "cast"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun field(a: Any,n: String): Any? = generateSequence(a.javaClass) { it.superclass }.flatMap { it.declaredFields.asSequence() }.first { it.name==n }.apply { isAccessible=true }.get(a)
    private fun state(a: Source,b: StringSearch,label: String) {
        assertEquals(listOf(a.pattern,a.index,a.matchStart,a.matchLength,a.isCanonical,a.isOverlapping,a.elementComparisonType?.name,a.target.index),
            listOf(b.getPattern(),b.getIndex(),b.getMatchStart(),b.getMatchLength(),b.isCanonical(),b.isOverlapping(),b.getElementComparisonType()?.name,b.getTarget()!!.getIndex()),label)
        assertEquals(outcome { a.matchedText },outcome { b.getMatchedText() },label)
        assertEquals(a.target.index,b.getTarget()!!.getIndex(),label)
        val ap=field(a,"pattern_")!!; val compiler=field(b,"compiler") as StringSearchPatternCompiler
        assertEquals(field(ap,"CELength_"),compiler.pattern_.CELength_,label); assertEquals(field(ap,"PCELength_"),compiler.pattern_.PCELength_,label)
        assertContentEquals(field(ap,"CE_") as IntArray?,compiler.pattern_.CE_,label); assertContentEquals(field(ap,"PCE_") as LongArray?,compiler.pattern_.PCE_,label)
        for(n in listOf("strength_","ceMask_","toShift_","variableTop_")) assertEquals(field(a,n),field(compiler,n),"$label/$n")
        val asr=field(a,"search_")!!; val bsr=b.search_
        assertEquals(field(asr,"isForwardSearching_"),bsr.isForwardSearching_,label); assertEquals(field(asr,"reset_"),bsr.reset_,label)
    }
    @Test fun forwardBackwardMatchesAllStrengthsWildcardsAndBoundaries() {
        val cases=listOf("cafe" to "A CAFÉ cafe\u0301!", "ana" to "banana", "e" to "é e e\u0301", "é" to "e é è", "\u0301" to "a\u0301 é", "ae" to "ä ae Æ", "ch" to "ch cH xch", "😀" to "x😀😀a", "क" to "क क्‍ष", "ᄀ" to "가 가", "" to "abc", "\u0000" to "a\u0000b")
        val types=SearchIterator.ElementComparisonType.entries.map { it as SearchIterator.ElementComparisonType? }+null
        for(rule in listOf("","&c<ch&a<ä/e")) for(strength in listOf(0,1,2,3,15)) for(type in types) for((pattern,text) in cases) {
            // ICU4J cycles until Int overflow for an isolated combining mark with
            // IDENTICAL + ANY wildcard: exhausted target CEs keep skipping the pattern.
            // Keep the port's loop unchanged; do not spend billions of CEs in this oracle.
            if (strength == 15 && type == SearchIterator.ElementComparisonType.ANY_BASE_WEIGHT_IS_WILDCARD && pattern == "\u0301") continue
            val ac=SourceCollator(rule); val bc=RuleBasedCollator(rule)
            ac.strength=strength; bc.setStrength(strength)
            val a=Source(pattern,java.text.StringCharacterIterator(text),ac); val b=StringSearch(pattern,StringCharacterIterator(text),bc)
            a.setElementComparisonType(type?.let { SourceType.valueOf(it.name) }); b.setElementComparisonType(type)
            val label="$rule/$strength/$type/$pattern/$text"
            for(reverse in listOf(false,true)) {
                if(reverse) { a.reset(); b.reset(); a.setElementComparisonType(type?.let { SourceType.valueOf(it.name) }); b.setElementComparisonType(type) }
                repeat(text.length+4) {
                    assertEquals(outcome { if(reverse) a.previous() else a.next() },outcome { if(reverse) b.previous() else b.next() },label)
                    state(a,b,label)
                }
            }
        }
    }
    @Test fun mixedHistoriesPatternTargetCollatorReplacementAndReset() {
        val random=Random(78133)
        repeat(35) { history ->
            var ac=SourceCollator(""); var bc=RuleBasedCollator("")
            val a=Source("a",java.text.StringCharacterIterator("aé aa ch😀a"),ac)
            val b=StringSearch("a",StringCharacterIterator("aé aa ch😀a"),bc)
            repeat(100) { step ->
                val op=random.nextInt(18); val position=random.nextInt(-1,13)
                val pattern=listOf<String?>(null,"","a","é","ch","😀","\u0301").random(random)
                val text=listOf("abc","banana","é e\u0301","ch a😀","a-b"," ").random(random)
                val strength=listOf(0,1,2,3,15).random(random); val flag=random.nextBoolean()
                val type=SearchIterator.ElementComparisonType.entries.random(random)
                val x=outcome { when(op) {
                    0 -> a.next(); 1 -> a.previous(); 2 -> a.first(); 3 -> a.last(); 4 -> a.following(position); 5 -> a.preceding(position)
                    6 -> a.setIndex(position); 7 -> a.reset(); 8 -> a.setPattern(pattern)
                    9 -> a.setTarget(java.text.StringCharacterIterator(text))
                    10 -> a.setOverlapping(flag); 11 -> a.setCanonical(flag); 12 -> a.setElementComparisonType(SourceType.valueOf(type.name))
                    13 -> { ac=SourceCollator("&c<ch"); ac.strength=strength; a.setCollator(ac); a.reset() }
                    // ICU requires reset or setCollator after mutating the shared collator.
                    14 -> { ac.setStrength(strength); a.reset() }; 15 -> { ac.setAlternateHandlingShifted(flag); a.reset() }
                    16 -> a.setBreakIterator(if(flag) com.ibm.icu.text.BreakIterator.getWordInstance() else null)
                    else -> a.setCollator(null)
                } }
                val y=outcome { when(op) {
                    0 -> b.next(); 1 -> b.previous(); 2 -> b.first(); 3 -> b.last(); 4 -> b.following(position); 5 -> b.preceding(position)
                    6 -> b.setIndex(position); 7 -> b.reset(); 8 -> b.setPattern(pattern)
                    9 -> b.setTarget(StringCharacterIterator(text))
                    10 -> b.setOverlapping(flag); 11 -> b.setCanonical(flag); 12 -> b.setElementComparisonType(type)
                    13 -> { bc=RuleBasedCollator("&c<ch"); bc.setStrength(strength); b.setCollator(bc); b.reset() }
                    14 -> { bc.setStrength(strength); b.reset() }; 15 -> { bc.setAlternateHandlingShifted(flag); b.reset() }
                    16 -> b.setBreakIterator(if(flag) BreakIterator.getWordInstance() else null)
                    else -> b.setCollator(null)
                } }
                val label="$history/$step/$op/$position/$pattern/$text"
                assertEquals(x,y,label); state(a,b,label)
            }
        }
    }
    @Test fun constructorValidationNonzeroRangesAndLocaleEntryPoints() {
        for(pattern in listOf<String?>(null,"","a")) for(text in listOf<String?>(null,"","abc")) for(numeric in listOf(false,true)) {
            val a=SourceCollator(""); val b=RuleBasedCollator(""); a.numericCollation=numeric; b.setNumericCollation(numeric)
            assertEquals(outcome { Source(pattern,text?.let { java.text.StringCharacterIterator(it) },a); Unit },outcome { StringSearch(pattern,text?.let(::StringCharacterIterator),b); Unit })
        }
        val a=SourceCollator(""); val b=RuleBasedCollator("")
        assertEquals(outcome { Source("a",java.text.StringCharacterIterator("!abc",1,4,2),a); Unit },outcome { StringSearch("a",StringCharacterIterator("!abc",1,4,2),b); Unit })
        for(locale in listOf("en","de@collation=phonebook","es@collation=traditional","ja")) {
            val x=Source("a",java.text.StringCharacterIterator("ä a abc"),com.ibm.icu.util.ULocale(locale))
            val y=StringSearch("a",StringCharacterIterator("ä a abc"),io.github.heapy.icu4k.util.ULocale(locale))
            repeat(5) { assertEquals(outcome { x.next() },outcome { y.next() }); state(x,y,locale) }
        }
    }
}
