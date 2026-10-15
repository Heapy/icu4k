// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

actual abstract class CollatorCloneBase actual constructor() {
    protected actual abstract fun clone(): Any
    protected actual fun cloneHost(): Any? = js("Object.create(Object.getPrototypeOf(this), Object.getOwnPropertyDescriptors(this))")
}
