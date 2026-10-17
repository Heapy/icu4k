// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Shallow platform cloning without adding Java serialization to the public contract. */
expect abstract class ICUShallowCloneableBase() {
    protected abstract fun clone(): Any
    protected fun cloneHost(): Any?
}
