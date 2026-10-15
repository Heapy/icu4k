// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Host shallow-copy bridge for open collator subclasses. */
expect abstract class CollatorCloneBase() {
    protected abstract fun clone(): Any
    protected fun cloneHost(): Any?
}
