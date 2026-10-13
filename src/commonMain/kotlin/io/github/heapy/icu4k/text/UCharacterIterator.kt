// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CharacterIteratorWrapper
import io.github.heapy.icu4k.impl.UCharacterIteratorWrapper
import io.github.heapy.icu4k.impl.ReplaceableUCharacterIterator
import io.github.heapy.icu4k.impl.UCharArrayIterator

/**
 * Bidirectional code-unit and code-point iterator. Forward access increments
 * after returning a unit; backward access decrements before returning it.
 * Code-point access at a trail surrogate returns that unit alone.
 */
abstract class UCharacterIterator protected constructor() : UForwardCharacterIterator {
    abstract fun current(): Int
    abstract fun getLength(): Int
    abstract fun getIndex(): Int
    abstract override fun next(): Int
    abstract fun previous(): Int
    abstract fun setIndex(index: Int)
    abstract fun getText(fillIn: CharArray, offset: Int): Int
    /** Copies cursor state; implementations may share their underlying storage. */
    abstract fun clone(): UCharacterIterator

    open fun getCharacterIterator(): CharacterIterator = UCharacterIteratorWrapper(this)

    open fun currentCodePoint(): Int {
        val lead = current()
        if (UTF16.isLeadSurrogate(lead)) {
            next()
            val trail = current()
            previous()
            if (UTF16.isTrailSurrogate(trail)) return toCodePoint(lead, trail)
        }
        return lead
    }

    override fun nextCodePoint(): Int {
        val lead = next()
        if (UTF16.isLeadSurrogate(lead)) {
            val trail = next()
            if (UTF16.isTrailSurrogate(trail)) return toCodePoint(lead, trail)
            if (trail != DONE) previous()
        }
        return lead
    }

    open fun previousCodePoint(): Int {
        val trail = previous()
        if (UTF16.isTrailSurrogate(trail)) {
            val lead = previous()
            if (UTF16.isLeadSurrogate(lead)) return toCodePoint(lead, trail)
            if (lead != DONE) next()
        }
        return trail
    }

    open fun setToLimit() = setIndex(getLength())
    open fun setToStart() = setIndex(0)
    fun getText(fillIn: CharArray): Int = getText(fillIn, 0)
    open fun getText(): String = CharArray(getLength()).also { getText(it) }.concatToString()

    open fun moveIndex(delta: Int): Int {
        val index = (getIndex() + delta).coerceIn(0, getLength())
        setIndex(index)
        return index
    }

    /** Throws after reaching the boundary when the requested shift exceeds text. */
    open fun moveCodePointIndex(delta: Int): Int {
        var remaining = delta
        if (remaining > 0) {
            while (remaining > 0 && nextCodePoint() != DONE) remaining--
        } else {
            while (remaining < 0 && previousCodePoint() != DONE) remaining++
        }
        if (remaining != 0) throw IndexOutOfBoundsException("Code-point shift: $delta")
        return getIndex()
    }

    private fun toCodePoint(lead: Int, trail: Int): Int = ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000

    companion object {
        const val DONE = UForwardCharacterIterator.DONE
        fun getInstance(source: CharacterIterator): UCharacterIterator = CharacterIteratorWrapper(source)
        fun getInstance(source: Replaceable): UCharacterIterator = ReplaceableUCharacterIterator(source)
        fun getInstance(source: String): UCharacterIterator = ReplaceableUCharacterIterator(source)
        fun getInstance(source: StringBuilder): UCharacterIterator = ReplaceableUCharacterIterator(source)
        fun getInstance(source: CharArray): UCharacterIterator = UCharArrayIterator(source, 0, source.size)
        fun getInstance(source: CharArray, start: Int, limit: Int): UCharacterIterator = UCharArrayIterator(source, start, limit)
    }
}
