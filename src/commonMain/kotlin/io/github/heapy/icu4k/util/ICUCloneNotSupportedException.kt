// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Unchecked wrapper for an unsupported clone operation. */
open class ICUCloneNotSupportedException : ICUException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(cause: Throwable?) : super(cause)
    constructor(message: String?, cause: Throwable?) : super(message,cause)
    private companion object { const val serialVersionUID = -4824446458488194964L }
}
