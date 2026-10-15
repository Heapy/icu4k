// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)
package io.github.heapy.icu4k.impl.coll

import kotlin.native.ref.createCleaner

internal actual abstract class SharedObjectCloneBase actual constructor() {
    protected actual abstract fun clone(): Any
    protected actual fun cloneHost(): Any? = null
}
internal actual fun registerSharedReferenceCleanup(owner: Any, state: SharedReferenceState<*>): Any? = createCleaner(state) { it.clear() }
internal actual fun settingsNegativeArraySizeException(): RuntimeException = IllegalArgumentException("Negative array size")
