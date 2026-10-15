// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

internal actual abstract class SharedObjectCloneBase actual constructor() : Cloneable {
    protected actual abstract override fun clone(): Any
    protected actual fun cloneHost(): Any? = super.clone()
}
internal actual fun registerSharedReferenceCleanup(owner: Any, state: SharedReferenceState<*>): Any? = SharedReferenceFinalizer(state)
private class SharedReferenceFinalizer(private val state: SharedReferenceState<*>) {
    @Suppress("deprecation") protected fun finalize() { state.clear() }
}
internal actual fun settingsNegativeArraySizeException(): RuntimeException = NegativeArraySizeException()
