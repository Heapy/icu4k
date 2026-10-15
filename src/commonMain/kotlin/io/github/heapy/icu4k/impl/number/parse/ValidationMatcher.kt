// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StringSegment

abstract class ValidationMatcher : NumberParseMatcher {
    override fun match(segment: StringSegment?, result: ParsedNumber?): Boolean = false
    override fun smokeTest(segment: StringSegment?): Boolean = false
}
