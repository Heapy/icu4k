package io.github.heapy.icu4k.impl.locale

/** Read-only map view, including entries and collection views, matching unmodifiableMap. */
internal class LocaleReadOnlyMap<K, V>(private val source: Map<K, V>) : AbstractMutableMap<K, V>() {
    override val size: Int get() = source.size
    override fun get(key: K): V? = source[key]
    override fun containsKey(key: K): Boolean = source.containsKey(key)
    override fun containsValue(value: V): Boolean = source.containsValue(value)
    override fun put(key: K, value: V): Nothing = throw UnsupportedOperationException()
    override fun putAll(from: Map<out K, V>): Nothing = throw UnsupportedOperationException()
    override fun remove(key: K): Nothing = throw UnsupportedOperationException()
    override fun clear(): Nothing = throw UnsupportedOperationException()
    override val keys: MutableSet<K> = ReadOnlySet(source.keys)
    override val values: MutableCollection<V> = ReadOnlyCollection(source.values)
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size: Int get() = source.size
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            val entries = source.entries.iterator()
            return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                override fun hasNext(): Boolean = entries.hasNext()
                override fun next(): MutableMap.MutableEntry<K, V> = ReadOnlyEntry(entries.next())
                override fun remove(): Nothing = throw UnsupportedOperationException()
            }
        }
        override fun add(element: MutableMap.MutableEntry<K, V>): Nothing = throw UnsupportedOperationException()
        override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Nothing = throw UnsupportedOperationException()
        override fun remove(element: MutableMap.MutableEntry<K, V>): Nothing = throw UnsupportedOperationException()
        override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Nothing = throw UnsupportedOperationException()
        override fun retainAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Nothing = throw UnsupportedOperationException()
        override fun clear(): Nothing = throw UnsupportedOperationException()
    }
    private class ReadOnlyEntry<K, V>(private val entry: Map.Entry<K, V>) : MutableMap.MutableEntry<K, V> {
        override val key: K get() = entry.key
        override val value: V get() = entry.value
        override fun setValue(newValue: V): Nothing = throw UnsupportedOperationException()
        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value
        override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
        override fun toString(): String = "$key=$value"
    }
    private class ReadOnlySet<E>(private val values: Set<E>) : AbstractMutableSet<E>() {
        override val size: Int get() = values.size
        override fun contains(element: E): Boolean = values.contains(element)
        override fun iterator(): MutableIterator<E> = readOnlyIterator(values.iterator())
        override fun add(element: E): Nothing = throw UnsupportedOperationException()
        override fun addAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
        override fun remove(element: E): Nothing = throw UnsupportedOperationException()
        override fun removeAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
        override fun retainAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
        override fun clear(): Nothing = throw UnsupportedOperationException()
    }
    private class ReadOnlyCollection<E>(private val values: Collection<E>) : AbstractMutableCollection<E>() {
        override val size: Int get() = values.size
        override fun contains(element: E): Boolean = values.contains(element)
        override fun iterator(): MutableIterator<E> = readOnlyIterator(values.iterator())
        override fun add(element: E): Nothing = throw UnsupportedOperationException()
        override fun addAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
        override fun remove(element: E): Nothing = throw UnsupportedOperationException()
        override fun removeAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
        override fun retainAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
        override fun clear(): Nothing = throw UnsupportedOperationException()
    }
    companion object {
        private fun <E> readOnlyIterator(iterator: Iterator<E>): MutableIterator<E> = object : MutableIterator<E> {
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): E = iterator.next()
            override fun remove(): Nothing = throw UnsupportedOperationException()
        }
    }
}

/** JVM also seals Java Map default mutators at the platform boundary. */
internal expect fun <K, V> localeReadOnlyMap(source: Map<K, V>): Map<K, V>
