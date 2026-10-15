package io.github.heapy.icu4k.text

actual typealias UFormatBase = PortableUFormatBase
// Typed format subclasses supply their own Native copy path, as ICUCloneableBase does.
internal actual fun clonePortableUFormat(value: Any): Any? = null
