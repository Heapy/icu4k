// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

/** Root CE weights and tailoring boundaries; the constructor retains the caller's array. */
internal class CollationRootElements(private val elements: LongArray?) {
    fun getTertiaryBoundary(): Int = (element(IX_SEC_TER_BOUNDARIES).toInt() shl 8) and 0xff00
    fun getFirstTertiaryCE(): Long = element(element(IX_FIRST_TERTIARY_INDEX).toInt()) and SEC_TER_DELTA_FLAG.toLong().inv()
    fun getLastTertiaryCE(): Long = element(element(IX_FIRST_SECONDARY_INDEX).toInt() - 1) and SEC_TER_DELTA_FLAG.toLong().inv()
    fun getLastCommonSecondary(): Int = (element(IX_SEC_TER_BOUNDARIES).toInt() shr 16) and 0xff00
    fun getSecondaryBoundary(): Int = (element(IX_SEC_TER_BOUNDARIES).toInt() shr 8) and 0xff00
    fun getFirstSecondaryCE(): Long = element(element(IX_FIRST_SECONDARY_INDEX).toInt()) and SEC_TER_DELTA_FLAG.toLong().inv()
    fun getLastSecondaryCE(): Long = element(element(IX_FIRST_PRIMARY_INDEX).toInt() - 1) and SEC_TER_DELTA_FLAG.toLong().inv()
    fun getFirstPrimary(): Long = element(element(IX_FIRST_PRIMARY_INDEX).toInt())
    fun getFirstPrimaryCE(): Long = Collation.makeCE(getFirstPrimary())

    fun lastCEWithPrimaryBefore(primary: Long): Long {
        var p = primary
        if (p == 0L) return 0
        checkAssertion { p > element(element(IX_FIRST_PRIMARY_INDEX).toInt()) }
        var index = findP(p)
        var q = element(index)
        var secTer: Long
        if (p == (q and 0xffffff00L)) {
            checkAssertion { (q and PRIMARY_STEP_MASK.toLong()) == 0L }
            secTer = element(index - 1)
            if ((secTer and SEC_TER_DELTA_FLAG.toLong()) == 0L) {
                p = secTer and 0xffffff00L
                secTer = Collation.COMMON_SEC_AND_TER_CE.toLong()
            } else {
                index -= 2
                while (true) {
                    p = element(index)
                    if ((p and SEC_TER_DELTA_FLAG.toLong()) == 0L) { p = p and 0xffffff00L; break }
                    --index
                }
            }
        } else {
            p = q and 0xffffff00L
            secTer = Collation.COMMON_SEC_AND_TER_CE.toLong()
            while (true) {
                q = element(++index)
                if ((q and SEC_TER_DELTA_FLAG.toLong()) == 0L) {
                    checkAssertion { (q and PRIMARY_STEP_MASK.toLong()) == 0L }
                    break
                }
                secTer = q
            }
        }
        return (p shl 32) or (secTer and SEC_TER_DELTA_FLAG.toLong().inv())
    }
    fun firstCEWithPrimaryAtLeast(primary: Long): Long {
        var p = primary
        if (p == 0L) return 0
        var index = findP(p)
        if (p != (element(index) and 0xffffff00L)) {
            while (true) {
                p = element(++index)
                if ((p and SEC_TER_DELTA_FLAG.toLong()) == 0L) {
                    checkAssertion { (p and PRIMARY_STEP_MASK.toLong()) == 0L }
                    break
                }
            }
        }
        return (p shl 32) or Collation.COMMON_SEC_AND_TER_CE.toLong()
    }
    fun getPrimaryBefore(primary: Long, isCompressible: Boolean): Long {
        var p = primary
        var index = findPrimary(p)
        val q = element(index)
        val step: Int
        if (p == (q and 0xffffff00L)) {
            step = q.toInt() and PRIMARY_STEP_MASK
            if (step == 0) {
                do { p = element(--index) } while ((p and SEC_TER_DELTA_FLAG.toLong()) != 0L)
                return p and 0xffffff00L
            }
        } else {
            val next = element(index + 1)
            checkAssertion { isEndOfPrimaryRange(next) }
            step = next.toInt() and PRIMARY_STEP_MASK
        }
        return if ((p and 0xffffL) == 0L) Collation.decTwoBytePrimaryByOneStep(p, isCompressible, step)
        else Collation.decThreeBytePrimaryByOneStep(p, isCompressible, step)
    }
    fun getSecondaryBefore(p: Long, s: Int): Int {
        var index: Int
        var previous: Int
        var sec: Int
        if (p == 0L) {
            index = element(IX_FIRST_SECONDARY_INDEX).toInt()
            previous = 0
            sec = (element(index) shr 16).toInt()
        } else {
            index = findPrimary(p) + 1
            previous = Collation.BEFORE_WEIGHT16
            sec = getFirstSecTerForPrimary(index).toInt() ushr 16
        }
        checkAssertion { s >= sec }
        while (s > sec) {
            previous = sec
            checkAssertion { (element(index) and SEC_TER_DELTA_FLAG.toLong()) != 0L }
            sec = (element(index++) shr 16).toInt()
        }
        checkAssertion { sec == s }
        return previous
    }
    fun getTertiaryBefore(p: Long, s: Int, t: Int): Int {
        checkAssertion { (t and Collation.ONLY_TERTIARY_MASK.inv()) == 0 }
        var index: Int
        var previous: Int
        var secTer: Long
        if (p == 0L) {
            if (s == 0) { index = element(IX_FIRST_TERTIARY_INDEX).toInt(); previous = 0 }
            else { index = element(IX_FIRST_SECONDARY_INDEX).toInt(); previous = Collation.BEFORE_WEIGHT16 }
            secTer = element(index) and SEC_TER_DELTA_FLAG.toLong().inv()
        } else {
            index = findPrimary(p) + 1
            previous = Collation.BEFORE_WEIGHT16
            secTer = getFirstSecTerForPrimary(index)
        }
        val st = (s.toLong() shl 16) or t.toLong()
        while (st > secTer) {
            if ((secTer shr 16).toInt() == s) previous = secTer.toInt()
            checkAssertion { (element(index) and SEC_TER_DELTA_FLAG.toLong()) != 0L }
            secTer = element(index++) and SEC_TER_DELTA_FLAG.toLong().inv()
        }
        checkAssertion { secTer == st }
        return previous and 0xffff
    }
    fun findPrimary(p: Long): Int {
        checkAssertion { (p and 0xffL) == 0L }
        val index = findP(p)
        checkAssertion { isEndOfPrimaryRange(element(index + 1)) || p == (element(index) and 0xffffff00L) }
        return index
    }
    fun getPrimaryAfter(p: Long, primaryIndex: Int, isCompressible: Boolean): Long {
        var index = primaryIndex
        checkAssertion { p == (element(index) and 0xffffff00L) || isEndOfPrimaryRange(element(index + 1)) }
        var q = element(++index)
        val step = q.toInt() and PRIMARY_STEP_MASK
        if ((q and SEC_TER_DELTA_FLAG.toLong()) == 0L && step != 0) {
            return if ((p and 0xffffL) == 0L) Collation.incTwoBytePrimaryByOffset(p, isCompressible, step)
            else Collation.incThreeBytePrimaryByOffset(p, isCompressible, step)
        }
        while ((q and SEC_TER_DELTA_FLAG.toLong()) != 0L) q = element(++index)
        checkAssertion { (q and PRIMARY_STEP_MASK.toLong()) == 0L }
        return q
    }
    fun getSecondaryAfter(primaryIndex: Int, s: Int): Int {
        var index = primaryIndex
        var secTer: Long
        val limit: Int
        if (index == 0) {
            checkAssertion { s != 0 }
            index = element(IX_FIRST_SECONDARY_INDEX).toInt()
            secTer = element(index)
            limit = 0x10000
        } else {
            checkAssertion { index >= element(IX_FIRST_PRIMARY_INDEX).toInt() }
            secTer = getFirstSecTerForPrimary(index + 1)
            limit = getSecondaryBoundary()
        }
        while (true) {
            val sec = (secTer shr 16).toInt()
            if (sec > s) return sec
            secTer = element(++index)
            if ((secTer and SEC_TER_DELTA_FLAG.toLong()) == 0L) return limit
        }
    }
    fun getTertiaryAfter(primaryIndex: Int, s: Int, t: Int): Int {
        var index = primaryIndex
        var secTer: Long
        val limit: Int
        if (index == 0) {
            if (s == 0) {
                checkAssertion { t != 0 }
                index = element(IX_FIRST_TERTIARY_INDEX).toInt()
                limit = 0x4000
            } else { index = element(IX_FIRST_SECONDARY_INDEX).toInt(); limit = getTertiaryBoundary() }
            secTer = element(index) and SEC_TER_DELTA_FLAG.toLong().inv()
        } else {
            checkAssertion { index >= element(IX_FIRST_PRIMARY_INDEX).toInt() }
            secTer = getFirstSecTerForPrimary(index + 1)
            limit = getTertiaryBoundary()
        }
        val st = ((s.toLong() and 0xffffffffL) shl 16) or t.toLong()
        while (true) {
            if (secTer > st) {
                checkAssertion { (secTer shr 16) == s.toLong() }
                return secTer.toInt() and 0xffff
            }
            secTer = element(++index)
            if ((secTer and SEC_TER_DELTA_FLAG.toLong()) == 0L || (secTer shr 16) > s) return limit
            secTer = secTer and SEC_TER_DELTA_FLAG.toLong().inv()
        }
    }
    private fun getFirstSecTerForPrimary(index: Int): Long {
        var secTer = element(index)
        if ((secTer and SEC_TER_DELTA_FLAG.toLong()) == 0L) return Collation.COMMON_SEC_AND_TER_CE.toLong()
        secTer = secTer and SEC_TER_DELTA_FLAG.toLong().inv()
        return if (secTer > Collation.COMMON_SEC_AND_TER_CE) Collation.COMMON_SEC_AND_TER_CE.toLong() else secTer
    }
    private fun findP(p: Long): Int {
        checkAssertion { (p shr 24) != Collation.UNASSIGNED_IMPLICIT_BYTE.toLong() }
        var start = element(IX_FIRST_PRIMARY_INDEX).toInt()
        checkAssertion { p >= element(start) }
        var limit = elements!!.size - 1
        checkAssertion { element(limit) >= PRIMARY_SENTINEL }
        checkAssertion { p < element(limit) }
        while (start + 1 < limit) {
            var i = ((start.toLong() + limit.toLong()) / 2).toInt()
            var q = element(i)
            if ((q and SEC_TER_DELTA_FLAG.toLong()) != 0L) {
                var j = i + 1
                while (true) {
                    if (j == limit) break
                    q = element(j)
                    if ((q and SEC_TER_DELTA_FLAG.toLong()) == 0L) { i = j; break }
                    ++j
                }
                if ((q and SEC_TER_DELTA_FLAG.toLong()) != 0L) {
                    j = i - 1
                    while (true) {
                        if (j == start) break
                        q = element(j)
                        if ((q and SEC_TER_DELTA_FLAG.toLong()) == 0L) { i = j; break }
                        --j
                    }
                    if ((q and SEC_TER_DELTA_FLAG.toLong()) != 0L) break
                }
            }
            if (p < (q and 0xffffff00L)) limit = i else start = i
        }
        return start
    }
    private fun element(index: Int): Long {
        val data = elements!!
        if (index < 0 || index >= data.size) throw IndexOutOfBoundsException()
        return data[index]
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
    companion object {
        const val PRIMARY_SENTINEL = 0xffffff00L
        const val SEC_TER_DELTA_FLAG = 0x80
        const val PRIMARY_STEP_MASK = 0x7f
        const val IX_FIRST_TERTIARY_INDEX = 0
        const val IX_FIRST_SECONDARY_INDEX = 1
        const val IX_FIRST_PRIMARY_INDEX = 2
        const val IX_COMMON_SEC_AND_TER_CE = 3
        const val IX_SEC_TER_BOUNDARIES = 4
        const val IX_COUNT = 5
        private val assertionsEnabled = collationRootElementsAssertionsEnabled()
        private fun isEndOfPrimaryRange(q: Long): Boolean = (q and SEC_TER_DELTA_FLAG.toLong()) == 0L && (q and PRIMARY_STEP_MASK.toLong()) != 0L
    }
}
internal expect fun collationRootElementsAssertionsEnabled(): Boolean
