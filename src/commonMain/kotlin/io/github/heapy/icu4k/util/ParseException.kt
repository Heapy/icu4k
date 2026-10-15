// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

expect open class ParseException(message: String?, errorOffset: Int) : Exception {
    fun getErrorOffset(): Int
}

internal expect fun attachParseExceptionCause(exception: ParseException, cause: Throwable): ParseException
