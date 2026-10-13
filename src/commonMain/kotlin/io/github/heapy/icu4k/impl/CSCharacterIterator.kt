// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CharacterIterator

/** UTF-16 CharacterIterator retaining the supplied CharSequence by reference. */
open class CSCharacterIterator(text: CharSequence?) : CharacterIterator {
    private val seq = text ?: throw NullPointerException()
    private var index = 0
    override fun first(): Char { index = 0; return current() }
    override fun last(): Char { index = seq.length; return previous() }
    override fun current(): Char {
        if (index == seq.length) return CharacterIterator.DONE
        if (index !in 0 until seq.length) throw IndexOutOfBoundsException()
        return seq[index]
    }
    override fun next(): Char { if (index < seq.length) ++index; return current() }
    override fun previous(): Char {
        if (index == 0) return CharacterIterator.DONE
        --index
        return current()
    }
    override fun setIndex(position: Int): Char {
        require(position in 0..seq.length)
        index = position
        return current()
    }
    override fun getBeginIndex(): Int = 0
    override fun getEndIndex(): Int = seq.length
    override fun getIndex(): Int = index
    override fun clone(): CSCharacterIterator = CSCharacterIterator(seq).also { it.setIndex(index) }
}
