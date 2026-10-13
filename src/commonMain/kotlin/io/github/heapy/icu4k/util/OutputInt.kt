// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.jvm.JvmField

/** Mutable integer output parameter used by ICU span algorithms. */
open class OutputInt(@JvmField var value: Int = 0) {
    override fun toString(): String = value.toString()
}
