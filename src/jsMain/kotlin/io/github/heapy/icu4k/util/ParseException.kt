package io.github.heapy.icu4k.util

actual open class ParseException actual constructor(message: String?, private val errorOffset: Int) : Exception(message) {
    actual fun getErrorOffset(): Int = errorOffset
}
