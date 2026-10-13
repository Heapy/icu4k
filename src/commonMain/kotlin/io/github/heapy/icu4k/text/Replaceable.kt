// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/**
 * Mutable UTF-16 text, optionally carrying metadata such as styling.
 * Ranges are [start, limit). Implementations must leave metadata unchanged when
 * replacement text is identical. Newly inserted text normally inherits metadata
 * from the first replaced unit, then the preceding or following unit if empty.
 * [copy] must retain metadata; its destination must not overlap its source range.
 */
interface Replaceable {
    fun length(): Int
    fun charAt(offset: Int): Char
    fun char32At(offset: Int): Int
    fun getChars(srcStart: Int, srcLimit: Int, dst: CharArray, dstStart: Int)
    fun replace(start: Int, limit: Int, text: String)
    fun replace(start: Int, limit: Int, chars: CharArray, charsStart: Int, charsLen: Int)
    fun copy(start: Int, limit: Int, dest: Int)
    fun hasMetaData(): Boolean
}
