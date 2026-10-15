// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.SearchIterator as Source
import java.text.StringCharacterIterator as SourceText
import kotlin.random.Random
import kotlin.test.*

class SearchIteratorOracleTest {
    private class SourceFixture(target: java.text.CharacterIterator?) : Source(target, null) {
        val calls = mutableListOf<Pair<String,Int>>()
        private val state get() = Source::class.java.getDeclaredField("search_").apply { isAccessible = true }.get(this)
        private fun stateField(name: String) = state.javaClass.getDeclaredField(name).apply { isAccessible = true }
        override fun getIndex(): Int = targetText.index
        override fun setIndex(position: Int) { super.setIndex(position); targetText.setIndex(position) }
        private fun match(index: Int): Int {
            stateField("matchedIndex_").setInt(state, index)
            setMatchLength(if (index == DONE) 0 else 2)
            targetText.setIndex(if (index == DONE) (if (stateField("isForwardSearching_").getBoolean(state)) targetText.endIndex else targetText.beginIndex) else index)
            return index
        }
        private fun accepts(index: Int): Boolean = targetText.setIndex(index) == 'a' && targetText.next() == 'a'
        override fun handleNext(start: Int): Int {
            calls.add("next" to start)
            for(i in start..targetText.endIndex-2) if(accepts(i)) return match(i)
            return match(DONE)
        }
        override fun handlePrevious(startAt: Int): Int {
            calls.add("previous" to startAt)
            for(i in startAt-2 downTo targetText.beginIndex) if(accepts(i)) return match(i)
            return match(DONE)
        }
    }
    private fun result(block: () -> Any?): Any? = try { block() } catch(e: Exception) { listOf(e.javaClass.simpleName, e.message) }
    @Test fun mixedProtocolHistoriesAgainstIcu4j() {
        val random = Random(7813)
        repeat(40) { history ->
            val text = "!" + buildString { repeat(15) { append(if(random.nextInt(3)==0) ' ' else 'a') } } + "?"
            val a = SourceFixture(SourceText(text,1,16,random.nextInt(1,17)))
            val b = SearchProtocolFixture(StringCharacterIterator(text,1,16,a.index))
            repeat(100) { step ->
                val op = random.nextInt(13); val position = random.nextInt(0,18)
                val label = "$history/$step/$op/$position"
                val x = result { when(op) {
                    0 -> a.next(); 1 -> a.previous(); 2 -> a.first(); 3 -> a.last()
                    4 -> a.following(position); 5 -> a.preceding(position); 6 -> a.setIndex(position)
                    7 -> a.reset(); 8 -> a.isOverlapping = true; 9 -> a.isOverlapping = false
                    10 -> a.elementComparisonType = Source.ElementComparisonType.ANY_BASE_WEIGHT_IS_WILDCARD
                    11 -> a.elementComparisonType = null
                    else -> a.matchedText
                } }
                val y = result { when(op) {
                    0 -> b.next(); 1 -> b.previous(); 2 -> b.first(); 3 -> b.last()
                    4 -> b.following(position); 5 -> b.preceding(position); 6 -> b.setIndex(position)
                    7 -> b.reset(); 8 -> b.setOverlapping(true); 9 -> b.setOverlapping(false)
                    10 -> b.setElementComparisonType(SearchIterator.ElementComparisonType.ANY_BASE_WEIGHT_IS_WILDCARD)
                    11 -> b.setElementComparisonType(null)
                    else -> b.getMatchedText()
                } }
                assertEquals(x,y,label)
                assertEquals(listOf(a.index,a.matchStart,a.matchLength,a.isOverlapping,a.elementComparisonType?.name,a.target.index), listOf(b.getIndex(),b.getMatchStart(),b.getMatchLength(),b.isOverlapping(),b.getElementComparisonType()?.name,b.getTarget()!!.getIndex()),label)
                assertEquals(a.calls,b.calls,label)
            }
        }
    }
    @Test fun constructionAndSetTargetValidation() {
        assertEquals(result { SourceFixture(null) }, result { SearchProtocolFixture(null) })
        assertEquals(result { SourceFixture(SourceText("")) }, result { SearchProtocolFixture(StringCharacterIterator("")) })
        val a = SourceFixture(SourceText("aaa")); val b = SearchProtocolFixture(StringCharacterIterator("aaa"))
        for(position in 0..3) {
            val x = SourceText("aaa",position); val y = StringCharacterIterator("aaa",position)
            assertEquals(result { a.setTarget(x) },result { b.setTarget(y) })
            assertEquals(x.index,y.getIndex())
            assertEquals(a.target.index,b.getTarget()!!.getIndex())
        }
    }
}
