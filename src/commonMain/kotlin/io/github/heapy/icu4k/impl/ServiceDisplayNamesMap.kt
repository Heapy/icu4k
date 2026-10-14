// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.SortedMap

internal expect fun createServiceDisplayNamesMap(comparator: Comparator<Any?>?): SortedMap<String?, String>
internal expect fun readOnlyServiceDisplayNamesMap(map: SortedMap<String?, String>): SortedMap<String?, String>

/** Ordered storage shared by portable map views. Comparator equality defines key identity. */
internal class PortableServiceDisplayNamesMap private constructor(
    private val state: State,
    private val readOnly: Boolean,
    private val hasLower: Boolean = false, private val lower: String? = null,
    private val hasUpper: Boolean = false, private val upper: String? = null,
) : AbstractMutableMap<String?, String>(), SortedMap<String?, String> {
    constructor(comparator: Comparator<Any?>?) : this(State(comparator), false)
    private class Cell(val key: String?, var value: String)
    private class State(val comparator: Comparator<Any?>?) {
        val cells = ArrayList<Cell>()
        var modification = 0
        fun compare(a: String?, b: String?): Int = comparator?.compare(a, b) ?: a!!.compareTo(b!!)
        fun search(key: String?): Int {
            var lo = 0; var hi = cells.size
            while (lo < hi) {
                val middle = (lo + hi) ushr 1
                if (compare(key, cells[middle].key) > 0) lo = middle + 1 else hi = middle
            }
            return lo
        }
    }
    fun readOnly(): SortedMap<String?, String> = ReadOnlyServiceDisplayNamesMap(this)
    override fun comparator(): Comparator<in String?>? = state.comparator
    private fun inRange(key: String?): Boolean = (!hasLower || state.compare(key, lower) >= 0) && (!hasUpper || state.compare(key, upper) < 0)
    private fun checkWrite() { if (readOnly) throw UnsupportedOperationException() }
    private fun start(): Int = if (hasLower) state.search(lower) else 0
    private fun end(): Int = if (hasUpper) state.search(upper) else state.cells.size
    override val size: Int get() = end() - start()
    override fun containsKey(key: String?): Boolean {
        if (key == null && state.comparator == null) throw NullPointerException()
        if (!inRange(key)) return false
        val i = state.search(key)
        return i < state.cells.size && state.compare(key, state.cells[i].key) == 0
    }
    override fun get(key: String?): String? = if (containsKey(key)) state.cells[state.search(key)].value else null
    override fun put(key: String?, value: String): String? {
        checkWrite()
        if (!inRange(key)) throw IllegalArgumentException("key out of range")
        val i = state.search(key)
        if (i < state.cells.size && state.compare(key, state.cells[i].key) == 0) {
            val cell = state.cells[i]; val previous = cell.value; cell.value = value; return previous
        }
        state.compare(key, key) // TreeMap validates even the first key.
        state.cells.add(i, Cell(key, value)); state.modification++
        return null
    }
    override fun remove(key: String?): String? {
        checkWrite()
        if (!containsKey(key)) return null
        val removed = state.cells.removeAt(state.search(key)); state.modification++; return removed.value
    }
    override fun clear() {
        checkWrite()
        for (i in end() - 1 downTo start()) state.cells.removeAt(i)
        state.modification++
    }
    override fun firstKey(): String? = if (size == 0) throw NoSuchElementException() else state.cells[start()].key
    override fun lastKey(): String? = if (size == 0) throw NoSuchElementException() else state.cells[end() - 1].key
    private fun endpoint(key: String?, inclusive: Boolean) {
        if (hasLower && state.compare(key, lower) < 0 || hasUpper && (state.compare(key, upper) > 0 || inclusive && state.compare(key, upper) == 0)) throw IllegalArgumentException("key out of range")
        state.compare(key, key)
    }
    override fun headMap(toKey: String?): SortedMap<String?, String> {
        endpoint(toKey, false)
        return PortableServiceDisplayNamesMap(state, readOnly, hasLower, lower, true, toKey)
    }
    override fun tailMap(fromKey: String?): SortedMap<String?, String> {
        endpoint(fromKey, true)
        return PortableServiceDisplayNamesMap(state, readOnly, true, fromKey, hasUpper, upper)
    }
    override fun subMap(fromKey: String?, toKey: String?): SortedMap<String?, String> {
        endpoint(fromKey, true); endpoint(toKey, false)
        if (state.compare(fromKey, toKey) > 0) throw IllegalArgumentException("fromKey > toKey")
        return PortableServiceDisplayNamesMap(state, readOnly, true, fromKey, true, toKey)
    }
    override val keys: MutableSet<String?> get() = object : AbstractMutableSet<String?>() {
        override val size: Int get() = this@PortableServiceDisplayNamesMap.size
        override fun contains(element: String?): Boolean = containsKey(element)
        override fun remove(element: String?): Boolean = this@PortableServiceDisplayNamesMap.remove(element) != null
        override fun clear() = this@PortableServiceDisplayNamesMap.clear()
        override fun add(element: String?): Boolean = throw UnsupportedOperationException()
        override fun iterator(): MutableIterator<String?> {
            val iterator = entries.iterator()
            return object : MutableIterator<String?> {
                override fun hasNext(): Boolean = iterator.hasNext()
                override fun next(): String? = iterator.next().key
                override fun remove() = iterator.remove()
            }
        }
    }
    override val entries: MutableSet<MutableMap.MutableEntry<String?, String>> get() = object : AbstractMutableSet<MutableMap.MutableEntry<String?, String>>() {
        override val size: Int get() = this@PortableServiceDisplayNamesMap.size
        override fun add(element: MutableMap.MutableEntry<String?, String>): Boolean = throw UnsupportedOperationException()
        override fun clear() = this@PortableServiceDisplayNamesMap.clear()
        override fun contains(element: MutableMap.MutableEntry<String?, String>): Boolean = containsKey(element.key) && get(element.key) == element.value
        override fun remove(element: MutableMap.MutableEntry<String?, String>): Boolean {
            checkWrite()
            if (!contains(element)) return false
            this@PortableServiceDisplayNamesMap.remove(element.key)
            return true
        }
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<String?, String>> = object : MutableIterator<MutableMap.MutableEntry<String?, String>> {
            private var index = start()
            private var expected = state.modification
            private var removable = false
            private fun checkModification() { if (expected != state.modification) throw ConcurrentModificationException() }
            override fun hasNext(): Boolean = index < end()
            override fun next(): MutableMap.MutableEntry<String?, String> {
                checkModification()
                if (!hasNext()) throw NoSuchElementException()
                val cell = state.cells[index++]; removable = true
                return object : MutableMap.MutableEntry<String?, String> {
                    override val key: String? get() = cell.key
                    override val value: String get() = cell.value
                    override fun setValue(newValue: String): String { checkWrite(); val old = cell.value; cell.value = newValue; return old }
                    override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value
                    override fun hashCode(): Int = (key?.hashCode() ?: 0) xor value.hashCode()
                    override fun toString(): String = "$key=$value"
                }
            }
            override fun remove() {
                checkWrite(); checkModification()
                if (!removable) throw IllegalStateException()
                state.cells.removeAt(--index); state.modification++; expected = state.modification; removable = false
            }
        }
    }
}

private class ReadOnlyServiceDisplayNamesMap(private val source: SortedMap<String?, String>) :
    MutableMap<String?, String> by io.github.heapy.icu4k.impl.locale.LocaleReadOnlyMap(source), SortedMap<String?, String> {
    override fun comparator(): Comparator<in String?>? = source.comparator()
    override fun firstKey(): String? = source.firstKey()
    override fun lastKey(): String? = source.lastKey()
    override fun headMap(toKey: String?): SortedMap<String?, String> = ReadOnlyServiceDisplayNamesMap(source.headMap(toKey))
    override fun tailMap(fromKey: String?): SortedMap<String?, String> = ReadOnlyServiceDisplayNamesMap(source.tailMap(fromKey))
    override fun subMap(fromKey: String?, toKey: String?): SortedMap<String?, String> = ReadOnlyServiceDisplayNamesMap(source.subMap(fromKey, toKey))
    override fun equals(other: Any?): Boolean = source == other
    override fun hashCode(): Int = source.hashCode()
    override fun toString(): String = source.toString()
}
