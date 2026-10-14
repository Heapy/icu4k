package io.github.heapy.icu4k.util

/** Common counterpart of java.util.Enumeration used by ICU resource APIs. */
interface Enumeration<out E> {
    fun hasMoreElements(): Boolean
    fun nextElement(): E
}

internal fun <E> enumeration(values: Iterable<E>): Enumeration<E> = object : Enumeration<E> {
    private val iterator = values.iterator()
    override fun hasMoreElements(): Boolean = iterator.hasNext()
    override fun nextElement(): E = iterator.next()
}
