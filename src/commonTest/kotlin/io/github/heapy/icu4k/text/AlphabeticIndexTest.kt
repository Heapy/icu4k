// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class AlphabeticIndexTest {
    @Test fun englishStableRecordsAndSnapshots() {
        val index = AlphabeticIndex<Int>(ULocale.ENGLISH)
        assertEquals(28,index.getBucketCount())
        assertEquals(listOf("…","A","B"),index.getBucketLabels().take(3))
        index.addRecord("bob",1).addRecord("Alice",2).addRecord("bob",3).addRecord("123",4)
        assertEquals(4,index.getRecordCount())
        assertEquals(listOf(1,3),index.first { it.getLabel() == "B" }.map { it.getData() })
        val snapshot = index.buildImmutableIndex()
        assertEquals(0,snapshot.sumOf { it.size() })
        assertEquals("B",snapshot.getBucket(snapshot.getBucketIndex("bob"))!!.getLabel())
        assertNull(snapshot.getBucket(-1)); assertNull(snapshot.getBucket(28))
        index.setMaxLabelCount(5).clearRecords().addRecord("Zoe",5)
        assertTrue(index.getBucketCount() < snapshot.getBucketCount())
        assertEquals(28,snapshot.getBucketCount()); assertEquals(1,index.sumOf { it.size() })
    }
    @Test fun scriptsAndChineseRedirects() {
        val multilingual = AlphabeticIndex<Int>(ULocale.ENGLISH).addLabels(ULocale("ru"))
        val immutable = multilingual.buildImmutableIndex()
        assertEquals(AlphabeticIndex.Bucket.LabelType.INFLOW,immutable.getBucket(immutable.getBucketIndex("Ω"))!!.getLabelType())
        val chinese = AlphabeticIndex<Int>(ULocale("zh"))
        val frozen = chinese.buildImmutableIndex()
        assertEquals("Z",frozen.getBucket(frozen.getBucketIndex("张"))!!.getLabel())
        assertEquals(chinese.getBucketIndex("Z"),chinese.getBucketIndex("张"))
        val strokes = AlphabeticIndex<Int>(ULocale("zh@collation=stroke"))
        assertTrue(strokes.getBucketLabels().any { it?.endsWith("劃") == true })
    }
    @Test fun oldBucketsAndRecordIteratorRemainIndependentOfInput() {
        val index = AlphabeticIndex<Int>(ULocale.ENGLISH).addRecord("bob",1).addRecord("bob",2)
        val old = index.first { it.getLabel() == "B" }
        val iterator = old.iterator() as MutableIterator<AlphabeticIndex.Record<Int>>
        assertEquals(1,iterator.next().getData()); iterator.remove()
        assertEquals(1,old.size()); assertEquals(2,index.getRecordCount())
        index.addRecord("bob",3)
        assertEquals(listOf(1,2,3),index.first { it.getLabel() == "B" }.map { it.getData() })
        assertEquals(listOf(2),old.map { it.getData() })
        val empty = AlphabeticIndex<Int>(ULocale.ENGLISH)
        val frozen = empty.buildImmutableIndex()
        assertSame(empty.first(),frozen.getBucket(0))
        empty.addRecord("a",1)
        assertEquals(0,frozen.sumOf { it.size() }); assertEquals(1,empty.sumOf { it.size() })
    }
    @Test fun contractionsLabelsAndCollatorCloning() {
        val coll = RuleBasedCollator("&c<ch<d")
        val index = AlphabeticIndex<String>(coll).addLabels(UnicodeSet("[A-Z {ch} {ZZ*} {aa}]") )
        assertTrue("ch" in index.getBucketLabels()); assertTrue("ZZ" in index.getBucketLabels())
        assertFalse("aa" in index.getBucketLabels())
        val copy = index.getCollator()
        assertNotSame(coll,copy); assertSame(copy,index.getCollator())
        copy.setStrength(0)
        assertEquals(Collator.TERTIARY,coll.getStrength())
        val frozen = coll.cloneAsThawed(); frozen.freeze()
        assertSame(frozen,AlphabeticIndex<Int>(frozen).getCollator())
        val empty = AlphabeticIndex<Int>(RuleBasedCollator(""))
        assertEquals(1,empty.getBucketCount()); assertEquals(0,empty.getBucketIndex(null))
        empty.addRecord(null,1)
        assertNull(empty.single().single().getName())
    }
}
