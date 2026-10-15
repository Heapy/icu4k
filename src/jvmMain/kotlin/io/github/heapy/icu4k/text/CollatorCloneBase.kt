// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

actual abstract class CollatorCloneBase actual constructor() : Cloneable {
    protected actual abstract override fun clone(): Any
    protected actual fun cloneHost(): Any? = super.clone()
}
