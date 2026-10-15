package io.github.heapy.icu4k.util

actual typealias ParseException = java.text.ParseException

internal actual fun attachParseExceptionCause(exception: ParseException, cause: Throwable): ParseException = exception.apply { initCause(cause) }
