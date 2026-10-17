// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.UCaseProps

/** Mutable text cursor used by full case mappings and incremental context lookahead. */
internal class ReplaceableContextIterator : UCaseProps.ContextIterator {
    private var rep: Replaceable? = null
    private var index = 0
    private var limit = 0
    private var cpStart = 0
    private var cpLimit = 0
    private var contextStart = 0
    private var contextLimit = 0
    private var direction = 0
    private var reachedLimit = false

    fun setText(text: Replaceable?) {
        rep = text
        limit = text!!.length(); contextLimit = limit
        cpStart = 0; cpLimit = 0; index = 0; contextStart = 0
        direction = 0; reachedLimit = false
    }
    fun setIndex(value: Int) {
        cpStart = value; cpLimit = value; index = 0
        direction = 0; reachedLimit = false
    }
    fun getCaseMapCPStart(): Int = cpStart
    fun setLimit(value: Int) {
        limit = if (value >= 0 && value <= rep!!.length()) value else rep!!.length()
        reachedLimit = false
    }
    fun setContextLimits(start: Int, end: Int) {
        contextStart = if (start < 0) 0 else if (start <= rep!!.length()) start else rep!!.length()
        contextLimit = if (end < contextStart) contextStart else if (end <= rep!!.length()) end else rep!!.length()
        reachedLimit = false
    }
    fun nextCaseMapCP(): Int {
        if (cpLimit >= limit) return -1
        cpStart = cpLimit
        val c = rep!!.char32At(cpLimit)
        cpLimit += UTF16.getCharCount(c)
        return c
    }
    fun replace(text: String): Int {
        val delta = text.length - (cpLimit - cpStart)
        rep!!.replace(cpStart, cpLimit, text)
        cpLimit += delta; limit += delta; contextLimit += delta
        return delta
    }
    fun didReachLimit(): Boolean = reachedLimit
    override fun reset(dir: Int) {
        when {
            dir > 0 -> { direction = 1; index = cpLimit }
            dir < 0 -> { direction = -1; index = cpStart }
            else -> { direction = 0; index = 0 }
        }
        reachedLimit = false
    }
    override fun next(): Int {
        if (direction > 0) {
            if (index < contextLimit) {
                val c = rep!!.char32At(index)
                index += UTF16.getCharCount(c)
                return c
            }
            reachedLimit = true
        } else if (direction < 0 && index > contextStart) {
            val c = rep!!.char32At(index - 1)
            index -= UTF16.getCharCount(c)
            return c
        }
        return -1
    }
}
