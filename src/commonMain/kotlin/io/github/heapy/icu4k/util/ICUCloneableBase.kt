// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Platform shallow cloning for open ICU classes; Native classes supply their typed copy path. */
expect abstract class ICUCloneableBase() : ICUSerializable {
    protected abstract fun clone(): Any
    protected fun cloneHost(): Any?
}
