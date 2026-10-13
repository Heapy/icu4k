// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.CharacterIterator

/** Code-point traversal preserving ICU's code-unit cursor conventions. */
internal object CharacterIteration {
    const val DONE32 = Int.MAX_VALUE
    fun next32(ci: CharacterIterator): Int {
        var c = ci.current().code
        if (c in 0xd800..0xdbff) {
            c = ci.next().code
            if (c !in 0xdc00..0xdfff) ci.previous()
        }
        c = ci.next().code
        if (c >= 0xd800) c = nextTrail32(ci, c)
        if (c >= 0x10000 && c != DONE32) ci.previous()
        return c
    }
    /** Leaves a valid pair positioned on its trail unit, unlike [next32]. */
    fun nextTrail32(ci: CharacterIterator, lead: Int): Int {
        if (lead == 0xffff && ci.getIndex() >= ci.getEndIndex()) return DONE32
        var result = lead
        if (lead <= 0xdbff) {
            val trail = ci.next().code
            if (trail in 0xdc00..0xdfff) result = ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
            else ci.previous()
        }
        return result
    }
    fun previous32(ci: CharacterIterator): Int {
        if (ci.getIndex() <= ci.getBeginIndex()) return DONE32
        val trail = ci.previous().code
        var result = trail
        if (trail in 0xdc00..0xdfff && ci.getIndex() > ci.getBeginIndex()) {
            val lead = ci.previous().code
            if (lead in 0xd800..0xdbff) result = ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
            else ci.next()
        }
        return result
    }
    fun current32(ci: CharacterIterator): Int {
        val lead = ci.current().code
        if (lead < 0xd800) return lead
        if (lead in 0xd800..0xdbff) {
            val trail = ci.next().code
            ci.previous()
            if (trail in 0xdc00..0xdfff) return ((lead - 0xd800) shl 10) + trail - 0xdc00 + 0x10000
        } else if (lead == 0xffff && ci.getIndex() >= ci.getEndIndex()) return DONE32
        return lead
    }
}
