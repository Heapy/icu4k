@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
package io.github.heapy.icu4k.impl

internal actual fun collectionSetArray(data: Collection<*>): Array<Any?> = (data as java.util.Collection<*>).toArray()
internal actual fun <T> collectionSetTypedArray(data: Collection<*>, array: Array<T>?): Array<T> = (data as java.util.Collection<*>).toArray(array)

@Suppress("UNCHECKED_CAST")
internal actual fun <E> collectionSetMutable(data: Collection<E>): MutableCollection<E> = object : MutableCollection<E> {
    private val backing = data as java.util.Collection<E>
    override val size: Int get() = backing.size()
    override fun isEmpty(): Boolean = backing.isEmpty()
    override fun contains(element: E): Boolean = backing.contains(element)
    override fun containsAll(elements: Collection<E>): Boolean = backing.containsAll(elements)
    override fun iterator(): MutableIterator<E> = backing.iterator()
    override fun add(element: E): Boolean = backing.add(element)
    override fun remove(element: E): Boolean = backing.remove(element)
    override fun addAll(elements: Collection<E>): Boolean = backing.addAll(elements)
    override fun removeAll(elements: Collection<E>): Boolean = backing.removeAll(elements)
    override fun retainAll(elements: Collection<E>): Boolean = backing.retainAll(elements)
    override fun clear() = backing.clear()
}
internal actual fun <E> collectionSetIterator(data: Collection<E>): MutableIterator<E> = (data as java.util.Collection<E>).iterator()
