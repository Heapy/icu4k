// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** ICU argument failure; causes are supplied at construction on common targets. */
open class IllegalIcuArgumentException : IllegalArgumentException {
    constructor(errorMessage: String) : super(errorMessage)
    constructor(cause: Throwable) : super(cause)
    constructor(errorMessage: String, cause: Throwable) : super(errorMessage, cause)
}
