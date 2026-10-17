// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** A transformation from a source value to a destination value. */
fun interface Transform<S, D> {
    fun transform(source: S): D
}
