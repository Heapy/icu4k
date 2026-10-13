// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Iterates sorted integer indexes and associated values using a reusable result. */
interface ValueIterator {
    class Element {
        @kotlin.jvm.JvmField var integer: Int = 0
        @kotlin.jvm.JvmField var value: Any? = null
    }
    /** Leaves [element] unchanged when returning false. */
    fun next(element: Element): Boolean
    fun reset()
    fun setRange(start: Int, limit: Int)
}
