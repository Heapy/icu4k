package io.github.heapy.icu4k.util

actual open class ParseException actual constructor(message: String?, private val errorOffset: Int) : Exception(message) {
    internal var attachedCause: Throwable? = null
    override val cause: Throwable? get() = attachedCause
    actual fun getErrorOffset(): Int = errorOffset
}

internal actual fun attachParseExceptionCause(exception: ParseException, cause: Throwable): ParseException = exception.apply { attachedCause = cause }
