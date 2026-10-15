// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale

/** Factory extension point; JVM additionally preserves the java.util.Locale virtual overload. */
expect abstract class NumberFormatFactory protected constructor() {
    open fun visible(): Boolean
    abstract fun getSupportedLocaleNames(): Set<String>?
    open fun createFormat(loc: ULocale?, formatType: Int): NumberFormat?
    companion object {
        val FORMAT_NUMBER: Int
        val FORMAT_CURRENCY: Int
        val FORMAT_PERCENT: Int
        val FORMAT_SCIENTIFIC: Int
        val FORMAT_INTEGER: Int
    }
}

/** A factory adopting one immutable base locale name and a fixed visibility. */
expect abstract class SimpleNumberFormatFactory : NumberFormatFactory {
    constructor(locale: ULocale?)
    constructor(locale: ULocale?, visible: Boolean)
    final override fun visible(): Boolean
    final override fun getSupportedLocaleNames(): Set<String>
}

/** Collections.singleton mutation behavior for runtimes without java.util.Collections. */
internal fun numberFactoryLocaleNames(name: String): Set<String> = object : AbstractMutableSet<String>() {
    override val size: Int get() = 1
    override fun contains(element: String): Boolean = element == name
    override fun add(element: String): Boolean = throw UnsupportedOperationException()
    override fun iterator(): MutableIterator<String> = object : MutableIterator<String> {
        private var available = true
        override fun hasNext(): Boolean = available
        override fun next(): String {
            if (!available) throw NoSuchElementException()
            available = false
            return name
        }
        override fun remove(): Unit = throw UnsupportedOperationException()
    }
}
