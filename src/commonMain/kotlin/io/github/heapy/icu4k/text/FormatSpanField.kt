// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Extracted UFormat span-field category for composing attributed ranges. */
abstract class FormatSpanField protected constructor(name: String?) : FormatField(name) {
    companion object { private const val serialVersionUID = -4732719509273350606L }
}
