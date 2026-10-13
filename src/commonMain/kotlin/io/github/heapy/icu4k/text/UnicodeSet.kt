// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.OutputInt
import io.github.heapy.icu4k.impl.BMPSet

/** ICU inversion-list core. Pattern/property parsing and frozen span acceleration are pending. */
class UnicodeSet() : Iterable<String> {
    enum class SpanCondition { NOT_CONTAINED, CONTAINED, SIMPLE, CONDITION_COUNT }
    private var list = IntArray(25).also { it[0] = HIGH }
    private var len = 1
    private var bmpSet: BMPSet? = null
    private var buffer: IntArray? = null
    private var stringElements = ArrayList<String>()
    constructor(other: UnicodeSet) : this() { set(other) }
    constructor(start: Int, end: Int) : this() { add(start, end) }
    constructor(vararg pairs: Int) : this() {
        require(pairs.size and 1 == 0) { "Must have even number of integers" }
        list = IntArray(pairs.size + 1); len = list.size
        var last = -1
        var i = 0
        while (i < pairs.size) {
            val start = pairs[i]
            require(last < start) { "Must be monotonically increasing." }
            list[i++] = start
            val limit = pairs[i] + 1
            require(start < limit) { "Must be monotonically increasing." }
            last = limit; list[i++] = limit
        }
        list[i] = HIGH
    }
    fun set(other: UnicodeSet): UnicodeSet { checkFrozen();
        list = other.list.copyOf(other.len); len = other.len
        stringElements = ArrayList(other.stringElements)
        return this
    }
    fun clear(): UnicodeSet { checkFrozen(); list[0] = HIGH; len = 1; stringElements.clear(); return this }
    fun compact(): UnicodeSet { checkFrozen(); if (len + 7 < list.size) list = list.copyOf(len); buffer = null; if (stringElements.isEmpty()) stringElements = ArrayList(); return this }
    fun getRangeCount(): Int = len / 2
    fun getRangeStart(index: Int): Int = list[index * 2]
    fun getRangeEnd(index: Int): Int = list[index * 2 + 1] - 1
    fun hasStrings(): Boolean = stringElements.isNotEmpty()
    fun isEmpty(): Boolean = len == 1 && !hasStrings()
    fun size(): Int {
        var result = stringElements.size
        for (i in 0 until getRangeCount()) result += getRangeEnd(i) - getRangeStart(i) + 1
        return result
    }
    fun contains(c: Int): Boolean { validate(c); return bmpSet?.contains(c) ?: (findCodePoint(c) and 1 != 0) }
    fun contains(start: Int, end: Int): Boolean {
        validate(start); validate(end)
        val i = findCodePoint(start)
        return i and 1 != 0 && end < list[i]
    }
    fun contains(s: CharSequence): Boolean {
        val cp = singleCP(s)
        return if (cp >= 0) contains(cp) else stringElements.binarySearch(s.toString()) >= 0
    }
    fun add(s: CharSequence): UnicodeSet { checkFrozen();
        val cp = singleCP(s)
        if (cp >= 0) return add(cp)
        val str = s.toString()
        val index = stringElements.binarySearch(str)
        if (index < 0) stringElements.add(-index - 1, str)
        return this
    }
    fun add(c: Int): UnicodeSet { checkFrozen();
        validate(c)
        val i = findCodePoint(c)
        if (i and 1 != 0) return this
        if (c == list[i] - 1) {
            list[i] = c
            if (c == MAX_VALUE) { ensureCapacity(len + 1); list[len++] = HIGH }
            if (i > 0 && c == list[i - 1]) {
                list.copyInto(list, i - 1, i + 1, len); len -= 2
            }
        } else if (i > 0 && c == list[i - 1]) ++list[i - 1]
        else {
            if (len + 2 > list.size) {
                val temp = IntArray(nextCapacity(len + 2))
                list.copyInto(temp, 0, 0, i); list.copyInto(temp, i + 2, i, len); list = temp
            } else list.copyInto(list, i + 2, i, len)
            list[i] = c; list[i + 1] = c + 1; len += 2
        }
        return this
    }
    fun add(start: Int, end: Int): UnicodeSet { checkFrozen();
        validate(start); validate(end)
        if (start == end) return add(start)
        if (start > end) return this
        val limit = end + 1
        if (len and 1 != 0) {
            val lastLimit = if (len == 1) -2 else list[len - 2]
            if (lastLimit <= start) {
                if (lastLimit == start) {
                    list[len - 2] = limit
                    if (limit == HIGH) --len
                } else {
                    list[len - 1] = start
                    if (limit < HIGH) {
                        ensureCapacity(len + 2); list[len++] = limit; list[len++] = HIGH
                    } else { ensureCapacity(len + 1); list[len++] = HIGH }
                }
                return this
            }
        }
        return union(intArrayOf(start, limit, HIGH), 2)
    }
    fun addAll(other: UnicodeSet): UnicodeSet { checkFrozen();
        union(other.list, other.len)
        // Snapshot handles adding a set to itself.
        for (s in other.stringElements.toList()) add(s)
        return this
    }
    fun remove(c: Int): UnicodeSet = remove(c, c)
    fun remove(start: Int, end: Int): UnicodeSet { checkFrozen();
        validate(start); validate(end)
        if (start <= end) intersect(intArrayOf(start, end + 1, HIGH), 2, 2)
        return this
    }
    fun retain(c: Int): UnicodeSet = retain(c, c)
    fun retain(start: Int, end: Int): UnicodeSet { checkFrozen();
        validate(start); validate(end)
        if (start <= end) intersect(intArrayOf(start, end + 1, HIGH), 2, 0) else clear()
        return this
    }
    fun complement(c: Int): UnicodeSet = complement(c, c)
    fun complement(start: Int, end: Int): UnicodeSet { checkFrozen();
        validate(start); validate(end)
        if (start <= end) xor(intArrayOf(start, end + 1, HIGH), 2)
        return this
    }
    fun complement(): UnicodeSet { checkFrozen();
        if (list[0] == MIN_VALUE) { list.copyInto(list, 0, 1, len); --len }
        else { ensureCapacity(len + 1); list.copyInto(list, 1, 0, len); list[0] = MIN_VALUE; ++len }
        return this
    }
    fun remove(s: CharSequence): UnicodeSet { checkFrozen();
        val cp = singleCP(s)
        if (cp >= 0) return remove(cp)
        val i = stringElements.binarySearch(s.toString())
        if (i >= 0) stringElements.removeAt(i)
        return this
    }
    fun retain(s: CharSequence): UnicodeSet { checkFrozen();
        val cp = singleCP(s)
        if (cp >= 0) return retain(cp)
        val str = s.toString()
        val present = stringElements.binarySearch(str) >= 0
        if (present && getRangeCount() == 0 && size() == 1) return this
        clear()
        if (present) add(str)
        return this
    }
    fun complement(s: CharSequence): UnicodeSet { checkFrozen();
        val cp = singleCP(s)
        if (cp >= 0) return complement(cp)
        val str = s.toString()
        val i = stringElements.binarySearch(str)
        if (i >= 0) stringElements.removeAt(i) else stringElements.add(-i - 1, str)
        return this
    }
    fun removeAllStrings(): UnicodeSet { checkFrozen(); stringElements.clear(); return this }
    fun retainAll(other: UnicodeSet): UnicodeSet { checkFrozen();
        intersect(other.list, other.len, 0)
        stringElements.removeAll { other.stringElements.binarySearch(it) < 0 }
        return this
    }
    fun removeAll(other: UnicodeSet): UnicodeSet { checkFrozen();
        intersect(other.list, other.len, 2)
        if (this === other) stringElements.clear()
        else stringElements.removeAll { other.stringElements.binarySearch(it) >= 0 }
        return this
    }
    fun complementAll(other: UnicodeSet): UnicodeSet { checkFrozen();
        xor(other.list, other.len)
        for (str in other.stringElements.toList()) complement(str)
        return this
    }
    private fun xor(other: IntArray, otherLen: Int): UnicodeSet {
        ensureBufferCapacity(len + otherLen)
        val out = buffer!!
        var i = 0; var j = 0; var k = 0
        var a = list[i++]; var b = other[j++]
        while (true) {
            if (a < b) { out[k++] = a; a = list[i++] }
            else if (b < a) { out[k++] = b; b = other[j++] }
            else if (a != HIGH) { a = list[i++]; b = other[j++] }
            else { out[k++] = HIGH; len = k; break }
        }
        buffer = list; list = out
        return this
    }
    private fun intersect(other: IntArray, otherLen: Int, initialPolarity: Int): UnicodeSet {
        ensureBufferCapacity(len + otherLen)
        val out = buffer!!
        var i = 0; var j = 0; var k = 0
        var a = list[i++]; var b = other[j++]
        var polarity = initialPolarity
        main@ while (true) {
            when (polarity) {
                0 -> {
                    if (a < b) { a = list[i++]; polarity = polarity xor 1 }
                    else if (b < a) { b = other[j++]; polarity = polarity xor 2 }
                    else {
                        if (a == HIGH) break@main
                        out[k++] = a; a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
                3 -> {
                    if (a < b) { out[k++] = a; a = list[i++]; polarity = polarity xor 1 }
                    else if (b < a) { out[k++] = b; b = other[j++]; polarity = polarity xor 2 }
                    else {
                        if (a == HIGH) break@main
                        out[k++] = a; a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
                1 -> {
                    if (a < b) { a = list[i++]; polarity = polarity xor 1 }
                    else if (b < a) { out[k++] = b; b = other[j++]; polarity = polarity xor 2 }
                    else {
                        if (a == HIGH) break@main
                        a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
                2 -> {
                    if (b < a) { b = other[j++]; polarity = polarity xor 2 }
                    else if (a < b) { out[k++] = a; a = list[i++]; polarity = polarity xor 1 }
                    else {
                        if (a == HIGH) break@main
                        a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
            }
        }
        out[k++] = HIGH; len = k
        buffer = list; list = out
        return this
    }
    internal fun stringIterator(): Iterator<String> = stringElements.iterator()
    /** Read-only view over the current string storage; empty sets return an empty collection. */
    fun strings(): Collection<String> {
        if (!hasStrings()) return emptyList()
        val data = stringElements
        return object : AbstractCollection<String>() {
            override val size: Int get() = data.size
            override fun iterator(): Iterator<String> {
                val iterator = data.iterator()
                return object : Iterator<String> {
                    override fun hasNext(): Boolean = iterator.hasNext()
                    override fun next(): String = iterator.next()
                }
            }
        }
    }
    override fun iterator(): MutableIterator<String> = object : MutableIterator<String> {
        private var sourceList: IntArray? = if (len > 1) list else null
        private val sourceLength = len - 1
        private val sourceStrings = stringElements
        private var item = 0
        private var current = 0
        private var limit = 0
        private var strings: Iterator<String>? = null
        init {
            if (sourceList != null) { current = sourceList!![item++]; limit = sourceList!![item++] }
            else strings = sourceStrings.iterator()
        }
        override fun hasNext(): Boolean = sourceList != null || strings!!.hasNext()
        override fun next(): String {
            val data = sourceList ?: return strings!!.next()
            val cp = current++
            if (current >= limit) {
                if (item >= sourceLength) { strings = sourceStrings.iterator(); sourceList = null }
                else { current = data[item++]; limit = data[item++] }
            }
            return UTF16.valueOf(cp)
        }
        override fun remove(): Nothing = throw UnsupportedOperationException()
    }
    fun isFrozen(): Boolean = bmpSet != null
    fun cloneAsThawed(): UnicodeSet = UnicodeSet(this)
    fun clone(): UnicodeSet = if (isFrozen()) this else UnicodeSet(this)
    /** Internal code-point-only freezing used by the string span engine. */
    internal fun freezeCodePointSet(): UnicodeSet {
        check(!hasStrings())
        if (bmpSet == null) { compact(); bmpSet = BMPSet(list, len) }
        return this
    }
    private fun checkFrozen() { if (isFrozen()) throw UnsupportedOperationException("Attempt to modify frozen object") }
    /** Original unfrozen code-point span fallback; caller handles empty/clamped input. */
    internal fun spanCodePointsAndCount(s: CharSequence, start: Int, condition: SpanCondition, outCount: OutputInt?): Int {
        bmpSet?.let { return it.span(s, start, condition, outCount) }
        val contained = condition != SpanCondition.NOT_CONTAINED
        var next = start
        var count = 0
        do {
            val lead = s[next].code
            var cp = lead
            if (lead in 0xd800..0xdbff && next + 1 < s.length && s[next + 1].code in 0xdc00..0xdfff)
                cp = ((lead - 0xd800) shl 10) + s[next + 1].code - 0xdc00 + 0x10000
            if (contained != contains(cp)) break
            ++count
            next += if (cp > 0xffff) 2 else 1
        } while (next < s.length)
        if (outCount != null) outCount.value = count
        return next
    }
    internal fun spanCodePointsBack(s: CharSequence, fromIndex: Int, condition: SpanCondition): Int {
        bmpSet?.let { return it.spanBack(s, fromIndex, condition) }
        val contained = condition != SpanCondition.NOT_CONTAINED
        var previous = fromIndex
        do {
            val trail = s[previous - 1].code
            var cp = trail
            if (trail in 0xdc00..0xdfff && previous >= 2 && s[previous - 2].code in 0xd800..0xdbff)
                cp = ((s[previous - 2].code - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
            if (contained != contains(cp)) break
            previous -= if (cp > 0xffff) 2 else 1
        } while (previous > 0)
        return previous
    }
    private fun union(other: IntArray, otherLen: Int): UnicodeSet {
        ensureBufferCapacity(len + otherLen)
        val out = buffer!!
        var i = 0; var j = 0; var k = 0
        var a = list[i++]; var b = other[j++]
        var polarity = 0
        main@ while (true) {
            when (polarity) {
                0 -> {
                    if (a < b) {
                        if (k > 0 && a <= out[k - 1]) a = maxOf(list[i], out[--k])
                        else { out[k++] = a; a = list[i] }
                        ++i; polarity = polarity xor 1
                    } else if (b < a) {
                        if (k > 0 && b <= out[k - 1]) b = maxOf(other[j], out[--k])
                        else { out[k++] = b; b = other[j] }
                        ++j; polarity = polarity xor 2
                    } else {
                        if (a == HIGH) break@main
                        if (k > 0 && a <= out[k - 1]) a = maxOf(list[i], out[--k])
                        else { out[k++] = a; a = list[i] }
                        ++i; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
                3 -> {
                    if (b <= a) { if (a == HIGH) break@main; out[k++] = a }
                    else { if (b == HIGH) break@main; out[k++] = b }
                    a = list[i++]; polarity = polarity xor 1
                    b = other[j++]; polarity = polarity xor 2
                }
                1 -> {
                    if (a < b) { out[k++] = a; a = list[i++]; polarity = polarity xor 1 }
                    else if (b < a) { b = other[j++]; polarity = polarity xor 2 }
                    else {
                        if (a == HIGH) break@main
                        a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
                2 -> {
                    if (b < a) { out[k++] = b; b = other[j++]; polarity = polarity xor 2 }
                    else if (a < b) { a = list[i++]; polarity = polarity xor 1 }
                    else {
                        if (a == HIGH) break@main
                        a = list[i++]; polarity = polarity xor 1
                        b = other[j++]; polarity = polarity xor 2
                    }
                }
            }
        }
        out[k++] = HIGH; len = k
        buffer = list; list = out
        return this
    }
    private fun findCodePoint(c: Int): Int {
        if (c < list[0]) return 0
        if (len >= 2 && c >= list[len - 2]) return len - 1
        var low = 0; var high = len - 1
        while (true) {
            val i = (low + high) ushr 1
            if (i == low) return high
            if (c < list[i]) high = i else low = i
        }
    }
    private fun nextCapacity(min: Int): Int = if (min < 25) min + 25 else if (min <= 2500) 5 * min else minOf(2 * min, MAX_LENGTH)
    private fun ensureCapacity(size: Int) { if (size > list.size) list = list.copyOf(nextCapacity(minOf(size, MAX_LENGTH))) }
    private fun ensureBufferCapacity(size: Int) {
        val bounded = minOf(size, MAX_LENGTH)
        if (buffer == null || buffer!!.size < bounded) buffer = IntArray(nextCapacity(bounded))
    }
    override fun equals(other: Any?): Boolean = other is UnicodeSet && len == other.len &&
        (0 until len).all { list[it] == other.list[it] } && stringElements == other.stringElements
    override fun hashCode(): Int {
        var result = len
        for (i in 0 until len) result = result * 1000003 + list[i]
        return result
    }
    companion object {
        const val MIN_VALUE = 0
        const val MAX_VALUE = 0x10ffff
        private const val HIGH = 0x110000
        private const val MAX_LENGTH = HIGH + 1
        private fun validate(c: Int) { require(c in MIN_VALUE..MAX_VALUE) { "Invalid code point U+${c.toUInt().toString(16).uppercase().padStart(6, '0')}" } }
        private fun singleCP(s: CharSequence): Int {
            if (s.length == 1) return s[0].code
            if (s.length == 2 && s[0].code in 0xd800..0xdbff && s[1].code in 0xdc00..0xdfff)
                return ((s[0].code - 0xd800) shl 10) + s[1].code - 0xdc00 + 0x10000
            return -1
        }
    }
}
