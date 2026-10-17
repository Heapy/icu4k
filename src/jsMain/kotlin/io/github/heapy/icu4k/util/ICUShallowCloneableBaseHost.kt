// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util
actual abstract class ICUShallowCloneableBase actual constructor() {
    protected actual abstract fun clone(): Any
    protected actual fun cloneHost(): Any? = js("Object.create(Object.getPrototypeOf(this), Object.getOwnPropertyDescriptors(this))")
}
