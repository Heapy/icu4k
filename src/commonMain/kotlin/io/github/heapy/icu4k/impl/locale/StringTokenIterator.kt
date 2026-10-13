// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

/** Delimiter-set tokenization preserving empty tokens and UTF-16 source offsets. */
class StringTokenIterator(private var text: String, private val delimiters: String) {
    private var token: String? = null
    private var start = 0
    private var end = 0
    private var done = false
    init { setStart(0) }
    fun first(): String? { setStart(0); return token }
    fun current(): String? = token
    fun currentStart(): Int = start
    fun currentEnd(): Int = end
    fun isDone(): Boolean = done
    fun next(): String? {
        if (hasNext()) {
            start = end + 1
            end = nextDelimiter(start)
            token = text.substring(start, end)
        } else {
            start = end
            token = null
            done = true
        }
        return token
    }
    fun hasNext(): Boolean = end < text.length
    fun setStart(offset: Int): StringTokenIterator {
        if (offset > text.length) throw IndexOutOfBoundsException()
        start = offset
        end = nextDelimiter(start)
        token = text.substring(start, end)
        done = false
        return this
    }
    fun setText(text: String): StringTokenIterator { this.text = text; setStart(0); return this }
    private fun nextDelimiter(start: Int): Int {
        var i = start
        while (i < text.length && !delimiters.contains(text[i])) i++
        return i
    }
}
