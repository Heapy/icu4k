// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Input rejected because its processing would require impractical resources. */
open class ICUInputTooLongException : ICUException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(cause: Throwable?) : super(cause)
    constructor(message: String?,cause: Throwable?) : super(message,cause)
    private companion object { const val serialVersionUID = -2602876786689338226L }
}
