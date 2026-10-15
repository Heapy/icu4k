// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2008-2016, Google Inc, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.coll.collationIteratorClassAssertionsEnabled
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.util.LocaleData
import io.github.heapy.icu4k.util.ULocale

/** Locale-sensitive index labels and stably sorted records, grouped by primary collation. */
class AlphabeticIndex<V> private constructor(locale: ULocale?, collator: RuleBasedCollator?) : Iterable<AlphabeticIndex.Bucket<V>> {
    private val collatorOriginal = collator ?: Collator.getInstance(locale) as RuleBasedCollator
    private val collatorPrimaryOnly: RuleBasedCollator
    private var collatorExternal: RuleBasedCollator? = null
    private val recordComparator = Comparator<Record<V>> { a,b -> collatorOriginal.compare(a.name as Any?,b.name as Any?) }
    private val firstCharsInScripts: MutableList<String>
    private val initialLabels = UnicodeSet()
    private var inputList: MutableList<Record<V>>? = null
    private var buckets: BucketList<V>? = null
    private var overflowLabel: String? = "\u2026"
    private var underflowLabel: String? = "\u2026"
    private var inflowLabel: String? = "\u2026"
    private var maxLabelCount = 99

    constructor(locale: ULocale?) : this(locale,null)
    constructor(collator: RuleBasedCollator?) : this(null,collator)

    init {
        collatorPrimaryOnly = try { collatorOriginal.cloneAsThawed() } catch(e: Exception) {
            throw IllegalStateException("Collator cannot be cloned",e)
        }
        collatorPrimaryOnly.setStrength(Collator.PRIMARY)
        collatorPrimaryOnly.freeze()
        firstCharsInScripts = getFirstCharactersInScripts().toMutableList()
        firstCharsInScripts.sortWith { a,b -> collatorPrimaryOnly.compare(a,b) }
        while (true) {
            if (firstCharsInScripts.isEmpty()) throw IllegalArgumentException("AlphabeticIndex requires some non-ignorable script boundary strings")
            if (collatorPrimaryOnly.compare(firstCharsInScripts[0],"") == 0) firstCharsInScripts.removeAt(0) else break
        }
        if (!addChineseIndexCharacters() && locale != null) addIndexExemplars(locale)
    }

    fun addLabels(additions: UnicodeSet?): AlphabeticIndex<V> {
        initialLabels.addAll(additions!!); buckets = null; return this
    }
    fun addLabels(vararg additions: ULocale?): AlphabeticIndex<V> {
        for (addition in additions) addIndexExemplars(addition)
        buckets = null; return this
    }
    fun setOverflowLabel(label: String?): AlphabeticIndex<V> { overflowLabel = label; buckets = null; return this }
    fun getOverflowLabel(): String? = overflowLabel
    fun setUnderflowLabel(label: String?): AlphabeticIndex<V> { underflowLabel = label; buckets = null; return this }
    fun getUnderflowLabel(): String? = underflowLabel
    fun setInflowLabel(label: String?): AlphabeticIndex<V> { inflowLabel = label; buckets = null; return this }
    fun getInflowLabel(): String? = inflowLabel
    fun setMaxLabelCount(count: Int): AlphabeticIndex<V> { maxLabelCount = count; buckets = null; return this }
    fun getMaxLabelCount(): Int = maxLabelCount

    private fun initLabels(): MutableList<String> {
        val nfkd = Normalizer2.getNFKDInstance()
        val result = ArrayList<String>()
        val first = firstCharsInScripts[0]
        val overflow = firstCharsInScripts.last()
        for (original in initialLabels) {
            var item = original
            val checkDistinct = if (!UTF16.hasMoreCodePointsThan(item,1)) false
                else if (item.last() == '*' && item[item.length-2] != '*') { item = item.dropLast(1); false } else true
            if (collatorPrimaryOnly.compare(item,first) < 0 || collatorPrimaryOnly.compare(item,overflow) >= 0 ||
                (checkDistinct && collatorPrimaryOnly.compare(item,separated(item)) == 0)) continue
            val insertion = result.binarySearch(item,Comparator { a,b -> collatorPrimaryOnly.compare(a,b) })
            if (insertion < 0) result.add(insertion.inv(),item)
            else if (isOneLabelBetterThanOther(nfkd,item,result[insertion])) result[insertion] = item
        }
        val size = result.size-1
        if (size > maxLabelCount) {
            var count = 0; var old = -1
            val iter = result.iterator()
            while (iter.hasNext()) {
                count++; iter.next()
                val bump = count*maxLabelCount/size
                if (bump == old) iter.remove() else old = bump
            }
        }
        return result
    }
    private fun addIndexExemplars(locale: ULocale?) {
        var exemplars = LocaleData.getExemplarSet(locale,0,LocaleData.ES_INDEX)
        if (exemplars != null && !exemplars.isEmpty()) { initialLabels.addAll(exemplars); return }
        exemplars = LocaleData.getExemplarSet(locale,0,LocaleData.ES_STANDARD)!!.cloneAsThawed()
        if (exemplars.containsSome(0x61,0x7a) || exemplars.isEmpty()) exemplars.add(0x61,0x7a)
        if (exemplars.containsSome(0xac00,0xd7a3)) {
            exemplars.remove(0xac00,0xd7a3)
            for (c in intArrayOf(0xac00,0xb098,0xb2e4,0xb77c,0xb9c8,0xbc14,0xc0ac,0xc544,0xc790,0xcc28,0xce74,0xd0c0,0xd30c,0xd558)) exemplars.add(c)
        }
        if (exemplars.containsSome(0x1200,0x137f)) {
            val ethiopic = UnicodeSet("[ሀለሐመሠረሰሸቀቈቐቘበቨተቸኀኈነኘአከኰኸዀወዐዘዠየደዸጀገጐጘጠጨጰጸፀፈፐፘ]")
            ethiopic.retainAll(exemplars)
            exemplars.remove(0x1200,0x137f).addAll(ethiopic)
        }
        for (item in exemplars) initialLabels.add(UCharacter.toUpperCase(locale,item))
    }
    private fun addChineseIndexCharacters(): Boolean {
        val contractions = UnicodeSet()
        try { collatorPrimaryOnly.internalAddContractions(BASE[0].code,contractions) } catch(e: Exception) { return false }
        if (contractions.isEmpty()) return false
        initialLabels.addAll(contractions)
        for (s in contractions) {
            if (assertionsEnabled && !s.startsWith(BASE)) throw AssertionError()
            if (s.last() in 'A'..'Z') { initialLabels.add(0x41,0x5a); break }
        }
        return true
    }
    private fun separated(item: String): String {
        val result = StringBuilder()
        var last = item[0]; result.append(last)
        for (i in 1 until item.length) {
            val ch = item[i]
            if (last !in '\ud800'..'\udbff' || ch !in '\udc00'..'\udfff') result.append('\u034f')
            result.append(ch); last = ch
        }
        return result.toString()
    }

    fun buildImmutableIndex(): ImmutableIndex<V> {
        val list = if (!inputList.isNullOrEmpty()) createBucketList() else {
            if (buckets == null) buckets = createBucketList()
            buckets!!
        }
        return ImmutableIndex(list,collatorPrimaryOnly)
    }
    fun getBucketLabels(): List<String?> { initBuckets(); return buckets!!.visible.map { it.label } }
    fun getCollator(): RuleBasedCollator {
        if (collatorExternal == null) collatorExternal = try { collatorOriginal.clone() } catch(e: Exception) {
            throw IllegalStateException("Collator cannot be cloned",e)
        }
        return collatorExternal!!
    }
    fun addRecord(name: CharSequence?, data: V): AlphabeticIndex<V> {
        buckets = null
        if (inputList == null) inputList = ArrayList()
        inputList!!.add(Record(name,data)); return this
    }
    fun getBucketIndex(name: CharSequence?): Int { initBuckets(); return buckets!!.getBucketIndex(name,collatorPrimaryOnly) }
    fun clearRecords(): AlphabeticIndex<V> {
        if (!inputList.isNullOrEmpty()) { inputList!!.clear(); buckets = null }
        return this
    }
    fun getBucketCount(): Int { initBuckets(); return buckets!!.visible.size }
    fun getRecordCount(): Int = inputList?.size ?: 0
    override fun iterator(): Iterator<Bucket<V>> { initBuckets(); return buckets!!.iterator() }

    private fun initBuckets() {
        if (buckets != null) return
        buckets = createBucketList()
        val records = inputList
        if (records.isNullOrEmpty()) return
        records.sortWith(recordComparator)
        val iterator = buckets!!.all.iterator()
        var current = iterator.next()
        var next = if (iterator.hasNext()) iterator.next() else null
        var upper = next?.lowerBoundary
        for (r in records) {
            while (upper != null && collatorPrimaryOnly.compare(r.name as Any?,upper as Any?) >= 0) {
                current = next!!
                if (iterator.hasNext()) { next = iterator.next(); upper = next.lowerBoundary } else upper = null
            }
            val bucket = current.displayBucket ?: current
            if (bucket.records == null) bucket.records = ArrayList()
            bucket.records!!.add(r)
        }
    }

    class Record<V> internal constructor(internal val name: CharSequence?, internal val data: V) {
        fun getName(): CharSequence? = name
        fun getData(): V = data
        override fun toString(): String = "$name=$data"
    }
    class Bucket<V> internal constructor(internal val label: String?, internal val lowerBoundary: String, internal val labelType: LabelType) : Iterable<Record<V>> {
        enum class LabelType { NORMAL, UNDERFLOW, INFLOW, OVERFLOW }
        internal var displayBucket: Bucket<V>? = null
        internal var displayIndex = 0
        internal var records: MutableList<Record<V>>? = null
        fun getLabel(): String? = label
        fun getLabelType(): LabelType = labelType
        fun size(): Int = records?.size ?: 0
        override fun iterator(): Iterator<Record<V>> = records?.iterator() ?: emptyList<Record<V>>().iterator()
        override fun toString(): String = "{labelType=$labelType, lowerBoundary=$lowerBoundary, label=$label}"
    }
    class ImmutableIndex<V> internal constructor(private val buckets: BucketList<V>, private val collatorPrimaryOnly: Collator) : Iterable<Bucket<V>> {
        fun getBucketCount(): Int = buckets.visible.size
        fun getBucketIndex(name: CharSequence?): Int = buckets.getBucketIndex(name,collatorPrimaryOnly)
        fun getBucket(index: Int): Bucket<V>? = buckets.visible.getOrNull(index)
        override fun iterator(): Iterator<Bucket<V>> = buckets.iterator()
    }
    internal class BucketList<V>(val all: ArrayList<Bucket<V>>, val visible: List<Bucket<V>>) : Iterable<Bucket<V>> {
        init { for ((i,bucket) in visible.withIndex()) bucket.displayIndex = i }
        fun getBucketIndex(name: CharSequence?, collator: Collator): Int {
            var start = 0; var limit = all.size
            while (start+1 < limit) {
                val i = (start+limit)/2
                if (collator.compare(name as Any?,all[i].lowerBoundary as Any?) < 0) limit = i else start = i
            }
            val bucket = all[start].displayBucket ?: all[start]
            return bucket.displayIndex
        }
        override fun iterator(): Iterator<Bucket<V>> = object : MutableIterator<Bucket<V>> {
            private var index = 0
            override fun hasNext(): Boolean = index < visible.size
            override fun next(): Bucket<V> {
                if (!hasNext()) throw NoSuchElementException()
                return visible[index++]
            }
            override fun remove(): Nothing = throw UnsupportedOperationException()
        }
    }

    private fun createBucketList(): BucketList<V> {
        val indexCharacters = initLabels()
        val variableTop = if (collatorPrimaryOnly.isAlternateHandlingShifted()) collatorPrimaryOnly.getVariableTop().toLong() and 0xffffffffL else 0L
        var hasInvisible = false
        val ascii = arrayOfNulls<Bucket<V>>(26)
        val pinyin = arrayOfNulls<Bucket<V>>(26)
        var hasPinyin = false
        val list = ArrayList<Bucket<V>>()
        list.add(Bucket(underflowLabel,"",Bucket.LabelType.UNDERFLOW))
        var scriptIndex = -1; var upper = ""
        for (current in indexCharacters) {
            if (collatorPrimaryOnly.compare(current,upper) >= 0) {
                val inflow = upper
                var skipped = false
                while (true) {
                    upper = firstCharsInScripts[++scriptIndex]
                    if (collatorPrimaryOnly.compare(current,upper) < 0) break
                    skipped = true
                }
                if (skipped && list.size > 1) list.add(Bucket(inflowLabel,inflow,Bucket.LabelType.INFLOW))
            }
            var bucket = Bucket<V>(fixLabel(current),current,Bucket.LabelType.NORMAL)
            list.add(bucket)
            if (current.length == 1 && current[0] in 'A'..'Z') ascii[current[0]-'A'] = bucket
            else if (current.length == BASE.length+1 && current.startsWith(BASE) && current[BASE.length] in 'A'..'Z') {
                pinyin[current[BASE.length]-'A'] = bucket; hasPinyin = true
            }
            if (!current.startsWith(BASE) && hasMultiplePrimaryWeights(collatorPrimaryOnly,variableTop,current) && !current.endsWith("\uffff")) {
                var i = list.size-2
                while (true) {
                    val single = list[i--]
                    if (single.labelType != Bucket.LabelType.NORMAL) break
                    if (single.displayBucket == null && !hasMultiplePrimaryWeights(collatorPrimaryOnly,variableTop,single.lowerBoundary)) {
                        bucket = Bucket("",current+"\uffff",Bucket.LabelType.NORMAL)
                        bucket.displayBucket = single; list.add(bucket); hasInvisible = true; break
                    }
                }
            }
        }
        if (list.size == 1) return BucketList(list,list)
        list.add(Bucket(overflowLabel,upper,Bucket.LabelType.OVERFLOW))
        if (hasPinyin) {
            var asciiBucket: Bucket<V>? = null
            for (i in 0 until 26) {
                if (ascii[i] != null) asciiBucket = ascii[i]
                if (pinyin[i] != null && asciiBucket != null) { pinyin[i]!!.displayBucket = asciiBucket; hasInvisible = true }
            }
        }
        if (!hasInvisible) return BucketList(list,list)
        var i = list.size-1
        var next = list[i]
        while (--i > 0) {
            val bucket = list[i]
            if (bucket.displayBucket != null) continue
            if (bucket.labelType == Bucket.LabelType.INFLOW && next.labelType != Bucket.LabelType.NORMAL) { bucket.displayBucket = next; continue }
            next = bucket
        }
        return BucketList(list,list.filter { it.displayBucket == null })
    }

    @Deprecated("This API is ICU internal, only for testing.")
    fun getFirstCharactersInScripts(): List<String> {
        val set = UnicodeSet()
        collatorPrimaryOnly.internalAddContractions(0xfdd1,set)
        if (set.isEmpty()) throw UnsupportedOperationException("AlphabeticIndex requires script-first-primary contractions")
        val result = ArrayList<String>(200)
        for (boundary in set) {
            val mask = 1 shl UCharacter.getType(UTF16.charAt(boundary,1))
            if (mask and 0x3f == 0) continue // Lu, Ll, Lt, Lm, Lo and Cn.
            result.add(boundary)
        }
        return result
    }
    private companion object {
        const val BASE = "\ufdd0"
        val binaryCmp = UTF16.StringComparator(true,false,0)
        val assertionsEnabled = collationIteratorClassAssertionsEnabled(AlphabeticIndex::class)
        fun fixLabel(current: String): String {
            if (!current.startsWith(BASE)) return current
            val rest = current[BASE.length].code
            return if (rest > 0x2800 && rest <= 0x28ff) "${rest-0x2800}\u5283" else current.substring(BASE.length)
        }
        fun isOneLabelBetterThanOther(nfkd: Normalizer2, one: String, other: String): Boolean {
            val n1 = nfkd.normalize(one); val n2 = nfkd.normalize(other)
            var result = UTF16.countCodePoint(n1)-UTF16.countCodePoint(n2)
            if (result != 0) return result < 0
            result = binaryCmp.compare(n1,n2)
            return if (result != 0) result < 0 else binaryCmp.compare(one,other) < 0
        }
        fun hasMultiplePrimaryWeights(coll: RuleBasedCollator, variableTop: Long, s: String): Boolean {
            var seen = false
            for (ce in coll.internalGetCEs(s)) {
                if ((ce ushr 32) > variableTop) { if (seen) return true; seen = true }
            }
            return false
        }
    }
}
