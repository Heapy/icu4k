// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.Replaceable
import io.github.heapy.icu4k.text.ReplaceableString
import io.github.heapy.icu4k.text.UCharacterIterator

/** Cursor over a shared Replaceable. Cloning copies the cursor, not the text. */
class ReplaceableUCharacterIterator(private val replaceable: Replaceable) : UCharacterIterator() {
    constructor(text: String) : this(ReplaceableString(text))
    constructor(buffer: StringBuilder) : this(ReplaceableString(buffer))

    private var index = 0
    override fun current(): Int = if (index < replaceable.length()) replaceable.charAt(index).code else DONE
    override fun getLength(): Int = replaceable.length()
    override fun getIndex(): Int = index
    override fun next(): Int = if (index < replaceable.length()) replaceable.charAt(index++).code else DONE
    override fun previous(): Int = if (index > 0) replaceable.charAt(--index).code else DONE
    override fun setIndex(index: Int) {
        if (index < 0 || index > replaceable.length()) throw IndexOutOfBoundsException("Index: $index")
        this.index = index
    }
    override fun getText(fillIn: CharArray, offset: Int): Int {
        val length = replaceable.length()
        if (offset < 0 || offset > fillIn.size - length) throw IndexOutOfBoundsException("Insufficient text capacity")
        replaceable.getChars(0, length, fillIn, offset)
        return length
    }
    override fun clone(): ReplaceableUCharacterIterator = ReplaceableUCharacterIterator(replaceable).also { it.index = index }
}
