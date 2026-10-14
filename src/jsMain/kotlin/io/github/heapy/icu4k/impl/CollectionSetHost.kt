package io.github.heapy.icu4k.impl

internal actual fun collectionSetArray(data: Collection<*>): Array<Any?> = data.toTypedArray()
internal actual fun <T> collectionSetTypedArray(data: Collection<*>, array: Array<T>?): Array<T> = collectionSetTypedArrayPortable(data, array)

@Suppress("UNCHECKED_CAST")
internal actual fun <E> collectionSetMutable(data: Collection<E>): MutableCollection<E> = data as? MutableCollection<E> ?: throw UnsupportedOperationException()
internal actual fun <E> collectionSetIterator(data: Collection<E>): MutableIterator<E> {
    val iterator = data.iterator()
    if (iterator is MutableIterator<E>) return iterator
    return object : MutableIterator<E> {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): E = iterator.next()
        override fun remove(): Nothing = throw UnsupportedOperationException()
    }
}
