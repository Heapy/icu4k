// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

/** A number skeleton syntax failure, retaining the source token and cause diagnostics. */
open class SkeletonSyntaxException : IllegalArgumentException {
    constructor(message: String?, token: CharSequence?) : super("Syntax error in skeleton string: $message: $token")
    constructor(message: String?, token: CharSequence?, cause: Throwable?) : super("Syntax error in skeleton string: $message: $token", cause)
    private companion object { private const val serialVersionUID = 7733971331648360554L }
}
