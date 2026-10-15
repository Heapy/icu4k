package io.github.heapy.icu4k.text

actual typealias UFormatBase = PortableUFormatBase
internal actual fun clonePortableUFormat(value: Any): Any? = js("Object.create(Object.getPrototypeOf(value), Object.getOwnPropertyDescriptors(value))")
