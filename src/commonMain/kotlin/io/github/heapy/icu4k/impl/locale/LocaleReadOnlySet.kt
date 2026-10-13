// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

/** A live view which also rejects mutation through MutableSet casts. */
internal class LocaleReadOnlySet<T : Comparable<T>>(private val values: () -> Set<T>, private val order: Comparator<T>?) : AbstractMutableSet<T>() {
    override val size: Int get() = values().size
    override fun contains(element: T): Boolean = if (order == null) values().contains(element) else
        values().any { order.compare(it, element) == 0 }
    override fun iterator(): MutableIterator<T> {
        val source = values().sortedWith(order ?: naturalOrder()).iterator()
        return object : MutableIterator<T> {
            override fun hasNext(): Boolean = source.hasNext()
            override fun next(): T = source.next()
            override fun remove(): Nothing = throw UnsupportedOperationException()
        }
    }
    override fun add(element: T): Nothing = throw UnsupportedOperationException()
    override fun addAll(elements: Collection<T>): Nothing = throw UnsupportedOperationException()
    override fun remove(element: T): Nothing = throw UnsupportedOperationException()
    override fun removeAll(elements: Collection<T>): Nothing = throw UnsupportedOperationException()
    override fun retainAll(elements: Collection<T>): Nothing = throw UnsupportedOperationException()
    override fun clear(): Nothing = throw UnsupportedOperationException()
}
