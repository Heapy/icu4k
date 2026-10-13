// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.UCharacterIterator

class UCharacterIteratorWrapper(private val iterator: UCharacterIterator) : CharacterIterator {
    override fun first(): Char { iterator.setToStart(); return iterator.current().toChar() }
    override fun last(): Char { iterator.setToLimit(); return iterator.previous().toChar() }
    override fun current(): Char = iterator.current().toChar()
    override fun next(): Char { iterator.next(); return iterator.current().toChar() }
    override fun previous(): Char = iterator.previous().toChar()
    override fun setIndex(position: Int): Char { iterator.setIndex(position); return iterator.current().toChar() }
    override fun getBeginIndex(): Int = 0
    override fun getEndIndex(): Int = iterator.getLength()
    override fun getIndex(): Int = iterator.getIndex()
    override fun clone(): UCharacterIteratorWrapper = UCharacterIteratorWrapper(iterator.clone())
}
