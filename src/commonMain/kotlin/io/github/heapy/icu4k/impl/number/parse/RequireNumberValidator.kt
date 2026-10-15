// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

open class RequireNumberValidator : ValidationMatcher() {
    override fun postProcess(result: ParsedNumber?) { if (!result!!.seenNumber()) result!!.flags = result.flags or ParsedNumber.FLAG_FAIL }
    override fun toString(): String = "<RequireNumber>"
}
