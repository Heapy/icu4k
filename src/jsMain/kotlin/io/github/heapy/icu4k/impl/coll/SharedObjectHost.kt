// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

internal actual abstract class SharedObjectCloneBase actual constructor() {
    protected actual abstract fun clone(): Any
    protected actual fun cloneHost(): Any? = js("Object.create(Object.getPrototypeOf(this), Object.getOwnPropertyDescriptors(this))")
}
@JsName("FinalizationRegistry")
private external class SharedReferenceRegistry(cleanup: (SharedReferenceState<*>) -> Unit) {
    fun register(target: Any, heldValue: SharedReferenceState<*>)
}
private val sharedReferenceRegistry = SharedReferenceRegistry { it.clear() }
internal actual fun registerSharedReferenceCleanup(owner: Any, state: SharedReferenceState<*>): Any? {
    sharedReferenceRegistry.register(owner, state)
    return null
}
internal actual fun settingsNegativeArraySizeException(): RuntimeException = IllegalArgumentException("Negative array size")
