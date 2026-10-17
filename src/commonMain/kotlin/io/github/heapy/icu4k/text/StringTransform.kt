// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** A transform whose input and output are strings. */
fun interface StringTransform : Transform<String, String> {
    override fun transform(source: String): String
}
