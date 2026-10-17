// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util
actual abstract class ICUShallowCloneableBase actual constructor() : Cloneable {
    protected actual abstract override fun clone(): Any
    protected actual fun cloneHost(): Any? = super.clone()
}
