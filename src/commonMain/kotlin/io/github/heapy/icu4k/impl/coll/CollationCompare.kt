// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

/** Compares primary through quaternary levels, retaining the iterators' buffered CEs. */
internal object CollationCompare {
    fun compareUpToQuaternary(left: CollationIterator?, right: CollationIterator?, settings: CollationSettings?): Int {
        val options = settings!!.options
        val variableTop = if ((options and CollationSettings.ALTERNATE_MASK) == 0) 0L else settings.variableTop + 1
        var anyVariable = false
        while (true) {
            var leftPrimary: Long
            do {
                var ce = left!!.nextCE(); leftPrimary = ce ushr 32
                if (leftPrimary < variableTop && leftPrimary > Collation.MERGE_SEPARATOR_PRIMARY) {
                    anyVariable = true
                    do {
                        left.setCurrentCE(ce and -0x100000000L)
                        while (true) {
                            ce = left.nextCE(); leftPrimary = ce ushr 32
                            if (leftPrimary == 0L) left.setCurrentCE(0) else break
                        }
                    } while (leftPrimary < variableTop && leftPrimary > Collation.MERGE_SEPARATOR_PRIMARY)
                }
            } while (leftPrimary == 0L)
            var rightPrimary: Long
            do {
                var ce = right!!.nextCE(); rightPrimary = ce ushr 32
                if (rightPrimary < variableTop && rightPrimary > Collation.MERGE_SEPARATOR_PRIMARY) {
                    anyVariable = true
                    do {
                        right.setCurrentCE(ce and -0x100000000L)
                        while (true) {
                            ce = right.nextCE(); rightPrimary = ce ushr 32
                            if (rightPrimary == 0L) right.setCurrentCE(0) else break
                        }
                    } while (rightPrimary < variableTop && rightPrimary > Collation.MERGE_SEPARATOR_PRIMARY)
                }
            } while (rightPrimary == 0L)
            if (leftPrimary != rightPrimary) {
                if (settings.hasReordering()) { leftPrimary = settings.reorder(leftPrimary); rightPrimary = settings.reorder(rightPrimary) }
                return if (leftPrimary < rightPrimary) Collation.LESS else Collation.GREATER
            }
            if (leftPrimary == Collation.NO_CE_PRIMARY) break
        }
        if (CollationSettings.getStrength(options) >= 1) {
            if ((options and CollationSettings.BACKWARD_SECONDARY) == 0) {
                var li = 0; var ri = 0
                while (true) {
                    var ls: Int; do { ls = left!!.getCE(li++).toInt() ushr 16 } while (ls == 0)
                    var rs: Int; do { rs = right!!.getCE(ri++).toInt() ushr 16 } while (rs == 0)
                    if (ls != rs) return if (ls < rs) Collation.LESS else Collation.GREATER
                    if (ls == Collation.NO_CE_WEIGHT16) break
                }
            } else {
                var leftStart = 0; var rightStart = 0
                while (true) {
                    var p: Long
                    var leftLimit = leftStart
                    while (true) { p = left!!.getCE(leftLimit) ushr 32; if (p > Collation.MERGE_SEPARATOR_PRIMARY || p == 0L) ++leftLimit else break }
                    var rightLimit = rightStart
                    while (true) { p = right!!.getCE(rightLimit) ushr 32; if (p > Collation.MERGE_SEPARATOR_PRIMARY || p == 0L) ++rightLimit else break }
                    var li = leftLimit; var ri = rightLimit
                    while (true) {
                        var ls = 0; while (ls == 0 && li > leftStart) ls = left!!.getCE(--li).toInt() ushr 16
                        var rs = 0; while (rs == 0 && ri > rightStart) rs = right!!.getCE(--ri).toInt() ushr 16
                        if (ls != rs) return if (ls < rs) Collation.LESS else Collation.GREATER
                        if (ls == 0) break
                    }
                    checkAssertion { left!!.getCE(leftLimit) == right!!.getCE(rightLimit) }
                    if (p == Collation.NO_CE_PRIMARY) break
                    leftStart = leftLimit + 1; rightStart = rightLimit + 1
                }
            }
        }
        if ((options and CollationSettings.CASE_LEVEL) != 0) {
            val strength = CollationSettings.getStrength(options); var li = 0; var ri = 0
            while (true) {
                var lc: Int; var rc: Int; val leftLower32: Int
                if (strength == 0) {
                    var ce: Long
                    do { ce = left!!.getCE(li++); lc = ce.toInt() } while ((ce ushr 32) == 0L || lc == 0)
                    leftLower32 = lc; lc = lc and 0xc000
                    do { ce = right!!.getCE(ri++); rc = ce.toInt() } while ((ce ushr 32) == 0L || rc == 0)
                    rc = rc and 0xc000
                } else {
                    do { lc = left!!.getCE(li++).toInt() } while ((lc and -65536) == 0)
                    leftLower32 = lc; lc = lc and 0xc000
                    do { rc = right!!.getCE(ri++).toInt() } while ((rc and -65536) == 0)
                    rc = rc and 0xc000
                }
                if (lc != rc) return if ((options and CollationSettings.UPPER_FIRST) == 0) {
                    if (lc < rc) Collation.LESS else Collation.GREATER
                } else { if (lc < rc) Collation.GREATER else Collation.LESS }
                if ((leftLower32 ushr 16) == Collation.NO_CE_WEIGHT16) break
            }
        }
        if (CollationSettings.getStrength(options) <= 1) return Collation.EQUAL
        val tertiaryMask = CollationSettings.getTertiaryMask(options)
        var li = 0; var ri = 0; var anyQuaternaries = 0
        while (true) {
            var ll: Int; var lt: Int
            do {
                ll = left!!.getCE(li++).toInt(); anyQuaternaries = anyQuaternaries or ll
                checkAssertion { (ll and Collation.ONLY_TERTIARY_MASK) != 0 || (ll and 0xc0c0) == 0 }
                lt = ll and tertiaryMask
            } while (lt == 0)
            var rl: Int; var rt: Int
            do {
                rl = right!!.getCE(ri++).toInt(); anyQuaternaries = anyQuaternaries or rl
                checkAssertion { (rl and Collation.ONLY_TERTIARY_MASK) != 0 || (rl and 0xc0c0) == 0 }
                rt = rl and tertiaryMask
            } while (rt == 0)
            if (lt != rt) {
                if (CollationSettings.sortsTertiaryUpperCaseFirst(options)) {
                    if (lt > Collation.NO_CE_WEIGHT16) { if ((ll and -65536) != 0) lt = lt xor 0xc000 else lt += 0x4000 }
                    if (rt > Collation.NO_CE_WEIGHT16) { if ((rl and -65536) != 0) rt = rt xor 0xc000 else rt += 0x4000 }
                }
                return if (lt < rt) Collation.LESS else Collation.GREATER
            }
            if (lt == Collation.NO_CE_WEIGHT16) break
        }
        if (CollationSettings.getStrength(options) <= 2) return Collation.EQUAL
        if (!anyVariable && (anyQuaternaries and 0xc0) == 0) return Collation.EQUAL
        li = 0; ri = 0
        while (true) {
            var lq: Long
            do {
                val ce = left!!.getCE(li++); lq = ce and 0xffff
                lq = if (lq <= Collation.NO_CE_WEIGHT16) ce ushr 32 else lq or 0xffffff3fL
            } while (lq == 0L)
            var rq: Long
            do {
                val ce = right!!.getCE(ri++); rq = ce and 0xffff
                rq = if (rq <= Collation.NO_CE_WEIGHT16) ce ushr 32 else rq or 0xffffff3fL
            } while (rq == 0L)
            if (lq != rq) {
                if (settings.hasReordering()) { lq = settings.reorder(lq); rq = settings.reorder(rq) }
                return if (lq < rq) Collation.LESS else Collation.GREATER
            }
            if (lq == Collation.NO_CE_PRIMARY) break
        }
        return Collation.EQUAL
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
    private val assertionsEnabled = collationIteratorClassAssertionsEnabled(CollationCompare::class)
}
