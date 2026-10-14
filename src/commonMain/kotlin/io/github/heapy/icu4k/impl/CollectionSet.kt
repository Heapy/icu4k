// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Live Set-shaped collection view. Uniqueness is the caller's responsibility; equality is identity. */
open class CollectionSet<E>(private val data: Collection<E>?) : MutableSet<E> {
    override val size: Int get() = data!!.size
    override fun isEmpty(): Boolean = data!!.isEmpty()
    override fun contains(element: E): Boolean = data!!.contains(element)
    override fun iterator(): MutableIterator<E> = collectionSetIterator(data!!)
    override fun add(element: E): Boolean = collectionSetMutable(data!!).add(element)
    override fun remove(element: E): Boolean = collectionSetMutable(data!!).remove(element)
    override fun containsAll(elements: Collection<E>): Boolean = data!!.containsAll(elements)
    override fun addAll(elements: Collection<E>): Boolean = collectionSetMutable(data!!).addAll(elements)
    override fun retainAll(elements: Collection<E>): Boolean = collectionSetMutable(data!!).retainAll(elements)
    override fun removeAll(elements: Collection<E>): Boolean = collectionSetMutable(data!!).removeAll(elements)
    override fun clear() = collectionSetMutable(data!!).clear()
    open fun toArray(): Array<Any?> = collectionSetArray(data!!)
    open fun <T> toArray(array: Array<T>?): Array<T> = collectionSetTypedArray(data!!, array)
}
internal expect fun collectionSetArray(data: Collection<*>): Array<Any?>
internal expect fun <T> collectionSetTypedArray(data: Collection<*>, array: Array<T>?): Array<T>
@Suppress("UNCHECKED_CAST")
internal fun <T> collectionSetTypedArrayPortable(data: Collection<*>, array: Array<T>?): Array<T> {
    val input = array!!
    val result = if (input.size < data.size) input.copyOf(data.size) as Array<T> else input
    val target = result as Array<Any?>
    var index = 0
    for (value in data) target[index++] = value
    if (result.size > data.size) target[data.size] = null
    return result
}

internal expect fun <E> collectionSetMutable(data: Collection<E>): MutableCollection<E>
internal expect fun <E> collectionSetIterator(data: Collection<E>): MutableIterator<E>
