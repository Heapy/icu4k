// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UCharacterIterator

/** Array slice iterator. Indices are relative to [start], and storage is shared. */
class UCharArrayIterator(private val text: CharArray, private val start: Int, private val limit: Int) : UCharacterIterator() {
    init {
        require(start >= 0 && start <= limit && limit <= text.size) { "Invalid text slice" }
    }
    private var position = start
    override fun current(): Int = if (position < limit) text[position].code else DONE
    override fun getLength(): Int = limit - start
    override fun getIndex(): Int = position - start
    override fun next(): Int = if (position < limit) text[position++].code else DONE
    override fun previous(): Int = if (position > start) text[--position].code else DONE
    override fun setIndex(index: Int) {
        if (index < 0 || index > limit - start) throw IndexOutOfBoundsException("Index: $index")
        position = start + index
    }
    override fun getText(fillIn: CharArray, offset: Int): Int {
        text.copyInto(fillIn, offset, start, limit)
        return limit - start
    }
    override fun clone(): UCharArrayIterator = UCharArrayIterator(text, start, limit).also { it.position = position }
}
