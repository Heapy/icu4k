package io.github.heapy.icu4k.util

actual abstract class ICUCloneableBase actual constructor() : Cloneable, java.io.Serializable {
    protected actual abstract override fun clone(): Any
    protected actual fun cloneHost(): Any? = super.clone()
    private companion object { private const val serialVersionUID: Long = 1L }
}
