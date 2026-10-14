// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Stateful resource iteration; the size is captured at construction. */
class UResourceBundleIterator(private val bundle: UResourceBundle) {
    private var index = 0
    private val size = bundle.getSize()
    fun next(): UResourceBundle {
        if (index < size) return bundle[index++]
        throw NoSuchElementException()
    }
    fun nextString(): String {
        if (index < size) return bundle.getString(index++)
        throw NoSuchElementException()
    }
    fun reset() { index = 0 }
    fun hasNext(): Boolean = index < size
}
