// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

open class RequireAffixValidator : ValidationMatcher() {
    override fun postProcess(result: ParsedNumber?) { if (result!!.prefix == null || result.suffix == null) result!!.flags = result.flags or ParsedNumber.FLAG_FAIL }
    override fun toString(): String = "<RequireAffix>"
}
