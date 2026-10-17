// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** BreakTransliterator's UTF-16 CharacterIterator over shared mutable Replaceable text. */
internal class ReplaceableCharacterIterator private constructor(private var text: Replaceable,
    private var begin: Int, private var end: Int, private var position: Int, validate: Boolean) : CharacterIterator {
    constructor(text: Replaceable?, begin: Int, end: Int, position: Int) :
        this(text ?: throw NullPointerException(), begin, end, position, true)
    init {
        if (validate) {
            if (begin < 0 || begin > end || end > text.length()) throw IllegalArgumentException("Invalid substring range")
            if (position < begin || position > end) throw IllegalArgumentException("Invalid position")
        }
    }
    fun setText(value: Replaceable?) {
        text = value ?: throw NullPointerException()
        begin = 0; end = value.length(); position = 0
    }
    override fun first(): Char { position = begin; return current() }
    override fun last(): Char { position = if (end != begin) end - 1 else end; return current() }
    override fun setIndex(position: Int): Char {
        if (position < begin || position > end) throw IllegalArgumentException("Invalid index")
        this.position = position
        return current()
    }
    override fun current(): Char = if (position >= begin && position < end) text.charAt(position) else CharacterIterator.DONE
    override fun next(): Char {
        if (position < end - 1) { position++; return text.charAt(position) }
        position = end
        return CharacterIterator.DONE
    }
    override fun previous(): Char {
        if (position > begin) { position--; return text.charAt(position) }
        return CharacterIterator.DONE
    }
    override fun getBeginIndex(): Int = begin
    override fun getEndIndex(): Int = end
    override fun getIndex(): Int = position
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplaceableCharacterIterator || hashCode() != other.hashCode()) return false
        return text == other.text && position == other.position && begin == other.begin && end == other.end
    }
    override fun hashCode(): Int = text.hashCode() xor position xor begin xor end
    override fun clone(): ReplaceableCharacterIterator = ReplaceableCharacterIterator(text, begin, end, position, false)
}
