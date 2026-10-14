// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** A formatted string with associated field attributes. */
interface FormattedValue : CharSequence {
    override fun toString(): String
    fun <A : Appendable> appendTo(appendable: A): A
    fun nextPosition(position: ConstrainedFieldPosition): Boolean
    fun toCharacterIterator(): FormatAttributedCharacterIterator
}
