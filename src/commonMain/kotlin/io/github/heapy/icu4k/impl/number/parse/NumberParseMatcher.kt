// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.StringSegment

interface NumberParseMatcher {
    interface Flexible
    fun match(segment: StringSegment?, result: ParsedNumber?): Boolean
    fun smokeTest(segment: StringSegment?): Boolean
    fun postProcess(result: ParsedNumber?)
}
