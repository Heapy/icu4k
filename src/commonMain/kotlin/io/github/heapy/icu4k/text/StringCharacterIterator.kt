// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** UTF-16 code-unit iterator with a bounded range and absolute indices. */
class StringCharacterIterator(
    private var text: String,
    private var begin: Int,
    private var end: Int,
    private var position: Int,
) : CharacterIterator {
    constructor(text: String) : this(text, 0, text.length, 0)
    constructor(text: String, position: Int) : this(text, 0, text.length, position)
    init {
        require(begin >= 0 && begin <= end && end <= text.length) { "Invalid substring range" }
        require(position in begin..end) { "Invalid position" }
    }
    fun setText(text: String) { this.text = text; begin = 0; end = text.length; position = 0 }
    override fun first(): Char { position = begin; return current() }
    override fun last(): Char { position = if (end != begin) end - 1 else end; return current() }
    override fun setIndex(position: Int): Char {
        require(position in begin..end) { "Invalid index" }
        this.position = position
        return current()
    }
    override fun current(): Char = if (position >= begin && position < end) text[position] else CharacterIterator.DONE
    override fun next(): Char {
        if (position < end - 1) return text[++position]
        position = end
        return CharacterIterator.DONE
    }
    override fun previous(): Char = if (position > begin) text[--position] else CharacterIterator.DONE
    override fun getBeginIndex(): Int = begin
    override fun getEndIndex(): Int = end
    override fun getIndex(): Int = position
    override fun equals(other: Any?): Boolean = other is StringCharacterIterator &&
        text == other.text && position == other.position && begin == other.begin && end == other.end
    override fun hashCode(): Int = text.hashCode() xor position xor begin xor end
    override fun clone(): StringCharacterIterator = StringCharacterIterator(text, begin, end, position)
}
