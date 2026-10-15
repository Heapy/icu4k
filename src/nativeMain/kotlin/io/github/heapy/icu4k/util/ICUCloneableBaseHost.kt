package io.github.heapy.icu4k.util

actual abstract class ICUCloneableBase actual constructor() : ICUSerializable {
    protected actual abstract fun clone(): Any
    protected actual fun cloneHost(): Any? = null
}
