package io.github.heapy.icu4k.impl.locale

/** Read-only locale list view; the backing array/list may change through its original owner. */
internal class LocaleReadOnlyList<E>(private val source: List<E>) : AbstractMutableList<E>(), RandomAccess {
    override val size: Int get() = source.size
    override fun get(index: Int): E = source[index]
    override fun set(index: Int, element: E): Nothing = throw UnsupportedOperationException()
    override fun add(index: Int, element: E): Nothing = throw UnsupportedOperationException()
    override fun add(element: E): Nothing = throw UnsupportedOperationException()
    override fun addAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
    override fun addAll(index: Int, elements: Collection<E>): Nothing = throw UnsupportedOperationException()
    override fun removeAt(index: Int): Nothing = throw UnsupportedOperationException()
    override fun remove(element: E): Nothing = throw UnsupportedOperationException()
    override fun removeAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
    override fun retainAll(elements: Collection<E>): Nothing = throw UnsupportedOperationException()
    override fun clear(): Nothing = throw UnsupportedOperationException()
    override fun iterator(): MutableIterator<E> = listIterator()
    override fun listIterator(): MutableListIterator<E> = listIterator(0)
    override fun listIterator(index: Int): MutableListIterator<E> {
        val iterator = source.listIterator(index)
        return object : MutableListIterator<E> {
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): E = iterator.next()
            override fun nextIndex(): Int = iterator.nextIndex()
            override fun hasPrevious(): Boolean = iterator.hasPrevious()
            override fun previous(): E = iterator.previous()
            override fun previousIndex(): Int = iterator.previousIndex()
            override fun remove(): Nothing = throw UnsupportedOperationException()
            override fun add(element: E): Nothing = throw UnsupportedOperationException()
            override fun set(element: E): Nothing = throw UnsupportedOperationException()
        }
    }
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> = LocaleReadOnlyList(source.subList(fromIndex, toIndex))
}
internal expect fun <E> localeReadOnlyList(source: List<E>): List<E>
