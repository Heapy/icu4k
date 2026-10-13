// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.UCharacterIterator

/** Preserves ICU's CharacterIterator adapter semantics, including absolute indices. */
class CharacterIteratorWrapper(private val iterator: CharacterIterator) : UCharacterIterator() {
    private fun unit(c: Char): Int = if (c == CharacterIterator.DONE) DONE else c.code
    override fun current(): Int = unit(iterator.current())
    override fun getLength(): Int = iterator.getEndIndex() - iterator.getBeginIndex()
    override fun getIndex(): Int = iterator.getIndex()
    override fun next(): Int {
        val c = iterator.current()
        iterator.next()
        return unit(c)
    }
    override fun previous(): Int = unit(iterator.previous())
    override fun setIndex(index: Int) {
        try { iterator.setIndex(index) } catch (_: IllegalArgumentException) { throw IndexOutOfBoundsException() }
    }
    override fun setToLimit() { iterator.setIndex(iterator.getEndIndex()) }
    override fun getText(fillIn: CharArray, offset: Int): Int {
        val length = getLength()
        val saved = iterator.getIndex()
        if (offset < 0 || offset + length > fillIn.size) throw IndexOutOfBoundsException(length.toString())
        var index = offset
        var c = iterator.first()
        while (c != CharacterIterator.DONE) {
            fillIn[index++] = c
            c = iterator.next()
        }
        iterator.setIndex(saved)
        return length
    }
    override fun clone(): CharacterIteratorWrapper = CharacterIteratorWrapper(iterator.clone())
    override fun moveIndex(delta: Int): Int = iterator.setIndex((iterator.getIndex() + delta).coerceIn(0, getLength())).code
    override fun getCharacterIterator(): CharacterIterator = iterator.clone()
}
