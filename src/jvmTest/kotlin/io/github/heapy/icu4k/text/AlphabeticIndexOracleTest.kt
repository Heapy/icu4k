// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.AlphabeticIndex as Source
import com.ibm.icu.text.RuleBasedCollator as SourceCollator
import com.ibm.icu.text.UnicodeSet as SourceSet
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.ULocale
import kotlin.random.Random
import kotlin.test.*

class AlphabeticIndexOracleTest {
    private fun field(a: Any,name: String): Any? = a.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(a)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is ArithmeticException -> "arithmetic"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private val names = listOf("", "123", "!", "Alice", "alex", "Álex", "Åsa", "Ægir", "æon", "Schmidt", "Schuster", "Stark", "chad", "cha", "ç", "Zoe", "Ωμέγα", "Борис", "Яна", "علي", "אב", "क", "ก", "ሀ", "ለ", "가", "나", "あ", "い", "ア", "张", "王", "李", "一", "丁", "😀", "𝔄", "e\u0301", "é", "\u0000", "\uffff")
    private fun snapshot(a: Source<Int>,b: AlphabeticIndex<Int>,label: String) {
        assertEquals(outcome { a.bucketLabels },outcome { b.getBucketLabels() },label)
        assertEquals(a.recordCount,b.getRecordCount(),label)
        assertEquals(a.maxLabelCount,b.getMaxLabelCount(),label)
        assertEquals(a.firstCharactersInScripts,b.getFirstCharactersInScripts(),label)
        assertEquals(a.map { listOf(it.label,it.labelType.name,it.toString(),it.map { r -> listOf(r.name.toString(),r.data,r.toString()) }) },
            b.map { listOf(it.getLabel(),it.getLabelType().name,it.toString(),it.map { r -> listOf(r.getName().toString(),r.getData(),r.toString()) }) },label)
        val ab = field(a,"buckets")!!; val bb = field(b,"buckets") as AlphabeticIndex.BucketList<Int>
        val all = field(ab,"bucketList") as List<Source.Bucket<Int>>
        assertEquals(all.map { listOf(it.label,field(it,"lowerBoundary"),it.labelType.name,all.indexOf(field(it,"displayBucket")),field(it,"displayIndex"),it.map { r -> r.data }) },
            bb.all.map { listOf(it.getLabel(),it.lowerBoundary,it.getLabelType().name,bb.all.indexOf(it.displayBucket),it.displayIndex,it.map { r -> r.getData() }) },label)
        for (name in names) assertEquals(outcome { a.getBucketIndex(name) },outcome { b.getBucketIndex(name) },"$label/$name")
    }
    private fun immutable(a: Source.ImmutableIndex<Int>,b: AlphabeticIndex.ImmutableIndex<Int>,label: String) {
        assertEquals(a.bucketCount,b.getBucketCount(),label)
        for(i in -2..a.bucketCount+2) {
            val x=a.getBucket(i); val y=b.getBucket(i)
            assertEquals(x?.let { listOf(it.label,it.labelType.name,it.size(),it.toString()) },y?.let { listOf(it.getLabel(),it.getLabelType().name,it.size(),it.toString()) },label)
        }
        for(name in names) assertEquals(a.getBucketIndex(name),b.getBucketIndex(name),label)
    }
    @Test fun localeLabelsHiddenBoundariesRecordsAndSnapshots() {
        val locales=listOf("", "en", "de", "de@collation=phonebook", "sv", "da", "es", "es@collation=traditional", "cs", "ru", "uk", "el", "tr", "ja", "ko", "zh", "zh@collation=stroke", "zh@collation=zhuyin", "zh@collation=unihan", "am", "ar", "he", "hi", "th", "fa")
        for(locale in locales) {
            val a=Source<Int>(SourceLocale(locale)); val b=AlphabeticIndex<Int>(ULocale(locale))
            snapshot(a,b,locale)
            val ai=a.buildImmutableIndex(); val bi=b.buildImmutableIndex()
            names.forEachIndexed { i,n -> a.addRecord(n,i); b.addRecord(n,i) }
            a.addRecord("bob",100).addRecord("bob",101); b.addRecord("bob",100).addRecord("bob",101)
            snapshot(a,b,"$locale/records"); immutable(ai,bi,"$locale/old immutable")
            immutable(a.buildImmutableIndex(),b.buildImmutableIndex(),"$locale/new immutable")
            a.addLabels(SourceLocale.ENGLISH,SourceLocale("ru")); b.addLabels(ULocale.ENGLISH,ULocale("ru"))
            snapshot(a,b,"$locale/extra")
            a.clearRecords().setMaxLabelCount(7); b.clearRecords().setMaxLabelCount(7)
            snapshot(a,b,"$locale/limited")
        }
    }
    @Test fun customRulesStarsExpansionsNfkdTiesAndLimits() {
        for(rule in listOf("", "&c<ch<d", "&a<æ/e", "&S<Sch<St")) {
            val a=Source<Int>(SourceCollator(rule)); val b=AlphabeticIndex<Int>(RuleBasedCollator(rule))
            val labels="[A-Z a-z é É Å Å Ä Æ æ {CH} {ch} {Sch} {St} {SCH*} {ZZ*} {aa} {a**} {𝔄} {é} {\u0301}]"
            a.addLabels(SourceSet(labels)); b.addLabels(UnicodeSet(labels))
            names.forEachIndexed { i,n -> a.addRecord(n,i); b.addRecord(n,i) }
            snapshot(a,b,rule)
            for(limit in listOf(0,1,2,3,10,26,99,Int.MAX_VALUE,-1,-2,Int.MIN_VALUE)) {
                a.setMaxLabelCount(limit); b.setMaxLabelCount(limit)
                assertEquals(outcome { a.bucketLabels },outcome { b.getBucketLabels() },"$rule/$limit")
                assertEquals(outcome { a.bucketCount },outcome { b.getBucketCount() },"$rule/$limit")
            }
        }
    }
    @Test fun mixedHistoriesAndSnapshotIsolation() {
        val random=Random(78134)
        repeat(12) { history ->
            val locale=listOf("en","zh","zh@collation=stroke","ru","ko","am")[history%6]
            val a=Source<Int>(SourceLocale(locale)); val b=AlphabeticIndex<Int>(ULocale(locale))
            var ai=a.buildImmutableIndex(); var bi=b.buildImmutableIndex()
            repeat(65) { step ->
                val op=random.nextInt(9); val label=listOf<String?>(null,"","…","before","after","between").random(random)
                val name=names.random(random); val limit=random.nextInt(0,35)
                when(op) {
                    0 -> { a.addRecord(name,step); b.addRecord(name,step) }
                    1 -> { a.clearRecords(); b.clearRecords() }
                    2 -> { a.setUnderflowLabel(label); b.setUnderflowLabel(label) }
                    3 -> { a.setOverflowLabel(label); b.setOverflowLabel(label) }
                    4 -> { a.setInflowLabel(label); b.setInflowLabel(label) }
                    5 -> { a.setMaxLabelCount(limit); b.setMaxLabelCount(limit) }
                    6 -> { a.addLabels(SourceSet("[A-Z Æ {Sch*}]")); b.addLabels(UnicodeSet("[A-Z Æ {Sch*}]")) }
                    7 -> { a.addLabels(SourceLocale("el")); b.addLabels(ULocale("el")) }
                    else -> { ai=a.buildImmutableIndex(); bi=b.buildImmutableIndex() }
                }
                snapshot(a,b,"$history/$step/$op"); immutable(ai,bi,"$history/$step/snapshot")
            }
        }
    }
    @Test fun originalCollatorMutationRecordsAndOldBucketAliases() {
        val ac=SourceCollator(""); val bc=RuleBasedCollator("")
        val a=Source<Int>(ac).addLabels(SourceSet("[A-Z]")); val b=AlphabeticIndex<Int>(bc).addLabels(UnicodeSet("[A-Z]"))
        for ((i,n) in listOf("Bob","bob","bób","BÓB","alice").withIndex()) { a.addRecord(n,i); b.addRecord(n,i) }
        snapshot(a,b,"initial aliases")
        val oldA=a.first { it.label=="B" }; val oldB=b.first { it.getLabel()=="B" }
        val ia=oldA.iterator(); val ib=oldB.iterator() as MutableIterator<AlphabeticIndex.Record<Int>>
        assertEquals(ia.next().data,ib.next().getData()); ia.remove(); ib.remove()
        snapshot(a,b,"removed bucket record")
        ac.strength=0; bc.setStrength(0)
        a.addRecord("BOB",99); b.addRecord("BOB",99)
        snapshot(a,b,"mutated original")
        assertEquals(oldA.map { it.data },oldB.map { it.getData() })
        val frozenA=SourceCollator(""); val frozenB=RuleBasedCollator(""); frozenA.freeze(); frozenB.freeze()
        assertSame(frozenA,Source<Int>(frozenA).collator); assertSame(frozenB,AlphabeticIndex<Int>(frozenB).getCollator())
    }
    @Test fun aliasesNullableRecordsJavaLocalesAndIteratorContracts() {
        val ac=SourceCollator(""); val bc=RuleBasedCollator("")
        val a=Source<Int>(ac); val b=AlphabeticIndex<Int>(bc)
        assertEquals(outcome { a.getBucketIndex(null) },outcome { b.getBucketIndex(null) })
        val an=StringBuilder("bob"); val bn=StringBuilder("bob")
        a.addRecord(an,1); b.addRecord(bn,1)
        assertSame(an,a.single().single().name); assertSame(bn,b.single().single().getName())
        an.append('a'); bn.append('a'); snapshot(a,b,"mutable names")
        a.addRecord(null,2); b.addRecord(null,2)
        assertEquals(outcome { a.bucketCount },outcome { b.getBucketCount() })
        assertEquals(a.bucketCount,b.getBucketCount()) // failed sort retains newly cached buckets
        assertEquals(a.sumOf { it.size() },b.sumOf { it.size() })
        assertSame(a.collator,a.collator); assertSame(b.getCollator(),b.getCollator())
        assertNotSame(ac,a.collator); assertNotSame(bc,b.getCollator())
        for(locale in listOf(java.util.Locale.ENGLISH,java.util.Locale.CHINESE,java.util.Locale.JAPANESE)) {
            val x=Source<Int>(locale); val y=AlphabeticIndex<Int>(locale)
            x.addLabels(java.util.Locale.GERMAN,java.util.Locale("ru")); y.addLabels(java.util.Locale.GERMAN,java.util.Locale("ru"))
            snapshot(x,y,locale.toLanguageTag())
            assertFailsWith<UnsupportedOperationException> { x.iterator().apply { next() }.remove() }
            assertFailsWith<UnsupportedOperationException> { (y.iterator().apply { next() } as MutableIterator<*>).remove() }
        }
    }
}
