// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

expect open class IllegalIcuArgumentException : IllegalArgumentException {
    constructor(message: String?)
    constructor(cause: Throwable?)
    constructor(message: String?, cause: Throwable?)
    open fun initCause(cause: Throwable?): IllegalIcuArgumentException
}
