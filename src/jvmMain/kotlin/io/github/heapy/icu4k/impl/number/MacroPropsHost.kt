package io.github.heapy.icu4k.impl.number

actual abstract class MacroPropsCloneBase actual constructor() : Cloneable {
    protected actual abstract override fun clone(): Any
    protected actual fun cloneHost(): Any? = super.clone()
}
