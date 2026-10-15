package io.github.heapy.icu4k.impl.number

actual abstract class MacroPropsCloneBase actual constructor() {
    protected actual abstract fun clone(): Any
    protected actual fun cloneHost(): Any? = null
}
