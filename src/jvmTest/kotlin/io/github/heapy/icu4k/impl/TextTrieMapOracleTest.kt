package io.github.heapy.icu4k.impl

import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.TextTrieMap as Original

class TextTrieMapOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) { e.javaClass.simpleName }
    private fun <T> values(iterator: Iterator<T>?): List<T>? = iterator?.asSequence()?.toList()
    @Test fun randomCompressedTriesQueriesHandlersAndLeadCodePointsMatch() {
        val random = Random(855)
        for (ignoreCase in listOf(false, true)) {
            val j = Original<Int?>(ignoreCase); val k = TextTrieMap<Int?>(ignoreCase)
            val alphabet = listOf("a", "b", "C", "ß", "ẞ", "İ", "Σ", "ς", "𐐀", "𐐨", "😀", "\ud800", "\udc00")
            val keys = listOf("", "a", "ab", "abc", "abd", "\ud801", "\ud801\udc00") + List(150) { (0 until random.nextInt(1, 5)).joinToString("") { alphabet.random(random) } }
            keys.forEachIndexed { index, key -> j.put(key, index); k.put(key, index) }
            j.put("ab", null); k.put("ab", null)
            val js = com.ibm.icu.text.UnicodeSet(); val ks = io.github.heapy.icu4k.text.UnicodeSet()
            assertEquals(outcome { j.putLeadCodePoints(js); js.toPattern(false) }, outcome { k.putLeadCodePoints(ks); ks.toPattern(false) })
            for (text in keys + keys.map { it + "!" } + List(200) { alphabet.random(random) }) for (offset in listOf(0, text.length, if (text.isNotEmpty()) 1 else 0)) {
                val jo = Original.Output().also { it.matchLength = 99; it.partialMatch = true }
                val ko = TextTrieMap.Output().also { it.matchLength = 99; it.partialMatch = true }
                assertEquals(outcome { values(j.get(text, offset, jo)) }, outcome { values(k.get(text, offset, ko)) }, "$ignoreCase/$text/$offset")
                assertEquals(jo.matchLength, ko.matchLength); assertEquals(jo.partialMatch, ko.partialMatch)
                val jt = mutableListOf<Any>(); val kt = mutableListOf<Any>()
                val je = outcome { j.find(text, offset, Original.ResultHandler { length, found -> jt.add(listOf(length, values(found))); length < 3 }); null }
                val ke = outcome { k.find(text, offset, TextTrieMap.ResultHandler { length, found -> kt.add(listOf(length, values(found))); length < 3 }); null }
                assertEquals(je, ke); assertEquals(jt, kt)
            }
        }
    }
    @Test fun iteratorExhaustionCallbacksAndRemovalMatch() {
        val constructor = Original.CharIterator::class.java.getDeclaredConstructor(CharSequence::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).also { it.isAccessible = true }
        for (text in listOf("ab", "ß", "ẞ", "𐐀", "\ud800", "", "\udc00")) for (fold in listOf(false, true)) for (offset in listOf(-1, 0, text.length, text.length + 1)) {
            val j = constructor.newInstance(text, offset, fold)
            val k = TextTrieMap.CharIterator(text, offset, fold)
            repeat(5) {
                assertEquals(outcome { j.hasNext() }, outcome { k.hasNext() })
                assertEquals(outcome { j.next() }, outcome { k.next() })
                assertEquals(j.nextIndex(), k.nextIndex())
                assertEquals(outcome { j.processedLength() }, outcome { k.processedLength() })
            }
        }
        val j = Original<Int>(false).put("a", 1).put("a", 2); val k = TextTrieMap<Int>(false).put("a", 1).put("a", 2)
        val ji = j.get("a"); val ki = k.get("a")!!
        while (ji.hasNext()) { assertEquals(ji.next(), ki.next()); ji.remove(); ki.remove() }
        assertEquals(values(j.get("a")), values(k.get("a")))
    }
    @Test fun collectionSetArraysIdentityAndLiveMutationsMatch() {
        val jd = arrayListOf("a", "a", "b"); val kd = jd.toMutableList()
        val j = com.ibm.icu.impl.CollectionSet(jd); val k = CollectionSet(kd)
        assertEquals(j == setOf("a", "b"), k == setOf("a", "b"))
        val ja = arrayOf<String?>("x", "x", "x", "x", "x"); val ka = ja.copyOf()
        assertSame(ja, j.toArray(ja)); assertSame(ka, k.toArray(ka)); assertContentEquals(ja, ka)
        assertContentEquals(j.toArray(), k.toArray())
        j.removeAll(listOf("a")); k.removeAll(listOf("a")); assertEquals(jd, kd)
        assertEquals(outcome { j.toArray(arrayOf<Int?>(null)) }, outcome { k.toArray(arrayOf<Int?>(null)) })
        assertEquals(outcome { com.ibm.icu.impl.CollectionSet(listOf("a")).add("b") }, outcome { CollectionSet(listOf("a")).add("b") })
        val readonly = java.util.Collections.unmodifiableList(listOf("a"))
        assertEquals(outcome { com.ibm.icu.impl.CollectionSet(readonly).add("b") }, outcome { CollectionSet(readonly).add("b") })
    }
    @Test fun interleavedLiveValueIteratorsMatchLinkedList() {
        val random = kotlin.random.Random(7803)
        repeat(200) {
            val j = Original<Int?>(false).put("a", 1).put("a", null).put("a", 2)
            val k = TextTrieMap<Int?>(false).put("a", 1).put("a", null).put("a", 2)
            val ji = mutableListOf(j.get("a"))
            val ki = mutableListOf(k.get("a")!!)
            repeat(60) {
                val index = random.nextInt(ji.size)
                when (random.nextInt(6)) {
                    0 -> assertEquals(outcome { ji[index].hasNext() }, outcome { ki[index].hasNext() })
                    1 -> assertEquals(outcome { ji[index].next() }, outcome { ki[index].next() })
                    2 -> assertEquals(outcome { ji[index].remove() }, outcome { ki[index].remove() })
                    3 -> { val value = random.nextInt(10); j.put("a", value); k.put("a", value) }
                    4 -> { ji.add(j.get("a")); ki.add(k.get("a")!!) }
                    5 -> {
                        val a = j.get("a"); val b = k.get("a")!!
                        while (a.hasNext()) { assertEquals(a.next(), b.next()); a.remove(); b.remove() }
                    }
                }
                assertEquals(values(j.get("a")), values(k.get("a")))
            }
        }
    }

}
