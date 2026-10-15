// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.lang.UScript

/** ICU fast Latin mini-CE decoder and comparison; -2 requests the general path. */
internal object CollationFastLatin {
    const val VERSION: Int = 2
    const val LATIN_MAX: Int = 383
    const val LATIN_LIMIT: Int = 384
    const val LATIN_MAX_UTF8_LEAD: Int = 197
    const val PUNCT_START: Int = 8192
    const val PUNCT_LIMIT: Int = 8256
    const val NUM_FAST_CHARS: Int = 448
    const val SHORT_PRIMARY_MASK: Int = 64512
    const val INDEX_MASK: Int = 1023
    const val SECONDARY_MASK: Int = 992
    const val CASE_MASK: Int = 24
    const val LONG_PRIMARY_MASK: Int = 65528
    const val TERTIARY_MASK: Int = 7
    const val CASE_AND_TERTIARY_MASK: Int = 31
    const val TWO_SHORT_PRIMARIES_MASK: Int = -67044352
    const val TWO_LONG_PRIMARIES_MASK: Int = -458760
    const val TWO_SECONDARIES_MASK: Int = 65012704
    const val TWO_CASES_MASK: Int = 1572888
    const val TWO_TERTIARIES_MASK: Int = 458759
    const val CONTRACTION: Int = 1024
    const val EXPANSION: Int = 2048
    const val MIN_LONG: Int = 3072
    const val LONG_INC: Int = 8
    const val MAX_LONG: Int = 4088
    const val MIN_SHORT: Int = 4096
    const val SHORT_INC: Int = 1024
    const val MAX_SHORT: Int = 64512
    const val MIN_SEC_BEFORE: Int = 0
    const val SEC_INC: Int = 32
    const val MAX_SEC_BEFORE: Int = 128
    const val COMMON_SEC: Int = 160
    const val MIN_SEC_AFTER: Int = 192
    const val MAX_SEC_AFTER: Int = 352
    const val MIN_SEC_HIGH: Int = 384
    const val MAX_SEC_HIGH: Int = 992
    const val SEC_OFFSET: Int = 32
    const val COMMON_SEC_PLUS_OFFSET: Int = 192
    const val TWO_SEC_OFFSETS: Int = 2097184
    const val TWO_COMMON_SEC_PLUS_OFFSET: Int = 12583104
    const val LOWER_CASE: Int = 8
    const val TWO_LOWER_CASES: Int = 524296
    const val COMMON_TER: Int = 0
    const val MAX_TER_AFTER: Int = 7
    const val TER_OFFSET: Int = 32
    const val COMMON_TER_PLUS_OFFSET: Int = 32
    const val TWO_TER_OFFSETS: Int = 2097184
    const val TWO_COMMON_TER_PLUS_OFFSET: Int = 2097184
    const val MERGE_WEIGHT: Int = 3
    const val EOS: Int = 2
    const val BAIL_OUT: Int = 1
    const val CONTR_CHAR_MASK: Int = 511
    const val CONTR_LENGTH_SHIFT: Int = 9
    const val BAIL_OUT_RESULT: Int = -2
    fun getCharIndex(c: Char): Int = when { c.code <= LATIN_MAX -> c.code; c.code in PUNCT_START until PUNCT_LIMIT -> c.code - (PUNCT_START - LATIN_LIMIT); else -> -1 }
    fun getOptions(data: CollationData?, settings: CollationSettings?, primaries: CharArray?): Int {
        val header=data!!.fastLatinTableHeader ?: return -1
        checkAssertion { (header.flAt(0) shr 8) == VERSION }
        if (primaries!!.size != LATIN_LIMIT) { checkAssertion { false }; return -1 }
        val miniVarTop: Int
        if ((settings!!.options and CollationSettings.ALTERNATE_MASK) == 0) miniVarTop=MIN_LONG-1 else {
            val headerLength=header.flAt(0) and 0xff; val i=1+settings.getMaxVariable()
            if (i >= headerLength) return -1
            miniVarTop=header.flAt(i)
        }
        var digitsAreReordered=false
        if (settings.hasReordering()) {
            var prevStart=0L; var beforeDigitStart=0L; var digitStart=0L; var afterDigitStart=0L
            for (group in 4096 until 4096+CollationData.MAX_NUM_SPECIAL_REORDER_CODES) {
                val start=settings.reorder(data.getFirstPrimaryForGroup(group))
                if (group == 4100) { beforeDigitStart=prevStart; digitStart=start } else if (start != 0L) {
                    if (start < prevStart) return -1
                    if (digitStart != 0L && afterDigitStart == 0L && prevStart == beforeDigitStart) afterDigitStart=start
                    prevStart=start
                }
            }
            val latinStart=settings.reorder(data.getFirstPrimaryForGroup(UScript.LATIN))
            if (latinStart < prevStart) return -1
            if (afterDigitStart == 0L) afterDigitStart=latinStart
            if (!(beforeDigitStart < digitStart && digitStart < afterDigitStart)) digitsAreReordered=true
        }
        val table=data.fastLatinTable
        for (c in 0 until LATIN_LIMIT) {
            var p=table!!.flAt(c)
            p=if (p >= MIN_SHORT) p and SHORT_PRIMARY_MASK else if (p > miniVarTop) p and LONG_PRIMARY_MASK else 0
            primaries.flSet(c,p)
        }
        if (digitsAreReordered || (settings.options and CollationSettings.NUMERIC) != 0) for (c in 0x30..0x39) primaries.flSet(c,0)
        return (miniVarTop shl 16) or settings.options
    }
    fun compareUTF16(table: CharArray?, primaries: CharArray?, initialOptions: Int, left: CharSequence?, right: CharSequence?, startIndex: Int): Int {
        val variableTop=initialOptions shr 16
        val options=initialOptions and 0xffff
        var leftIndex=startIndex; var rightIndex=startIndex
        var leftPair=0; var rightPair=0
        while (true) {
            while (leftPair == 0) {
                if (leftIndex == left!!.length) { leftPair=EOS; break }
                val c=left.ciChar(leftIndex++).code
                if (c <= LATIN_MAX) {
                    leftPair=primaries!!.flAt(c)
                    if (leftPair != 0) break
                    if (c <= 0x39 && c >= 0x30 && (options and CollationSettings.NUMERIC) != 0) return BAIL_OUT_RESULT
                    leftPair=table!!.flAt(c)
                } else if (c in PUNCT_START until PUNCT_LIMIT) leftPair=table!!.flAt(c-PUNCT_START+LATIN_LIMIT)
                else leftPair=lookup(table,c)
                if (leftPair >= MIN_SHORT) {
                    leftPair=leftPair and SHORT_PRIMARY_MASK; break
                } else if (leftPair > variableTop) {
                    leftPair=leftPair and LONG_PRIMARY_MASK; break
                } else {
                    var pairAndInc=nextPair(table,c,leftPair,left,leftIndex)
                    if (pairAndInc < 0) { ++leftIndex; pairAndInc=pairAndInc.inv() }
                    leftPair=pairAndInc.toInt()
                    if (leftPair == BAIL_OUT) return BAIL_OUT_RESULT
                    leftPair=getPrimaries(variableTop,leftPair)
                }
            }
            while (rightPair == 0) {
                if (rightIndex == right!!.length) { rightPair=EOS; break }
                val c=right.ciChar(rightIndex++).code
                if (c <= LATIN_MAX) {
                    rightPair=primaries!!.flAt(c)
                    if (rightPair != 0) break
                    if (c <= 0x39 && c >= 0x30 && (options and CollationSettings.NUMERIC) != 0) return BAIL_OUT_RESULT
                    rightPair=table!!.flAt(c)
                } else if (c in PUNCT_START until PUNCT_LIMIT) rightPair=table!!.flAt(c-PUNCT_START+LATIN_LIMIT)
                else rightPair=lookup(table,c)
                if (rightPair >= MIN_SHORT) {
                    rightPair=rightPair and SHORT_PRIMARY_MASK; break
                } else if (rightPair > variableTop) {
                    rightPair=rightPair and LONG_PRIMARY_MASK; break
                } else {
                    var pairAndInc=nextPair(table,c,rightPair,right,rightIndex)
                    if (pairAndInc < 0) { ++rightIndex; pairAndInc=pairAndInc.inv() }
                    rightPair=pairAndInc.toInt()
                    if (rightPair == BAIL_OUT) return BAIL_OUT_RESULT
                    rightPair=getPrimaries(variableTop,rightPair)
                }
            }
            if (leftPair == rightPair) {
                if (leftPair == EOS) break
                leftPair=0; rightPair=0; continue
            }
            var leftPrimary=leftPair and 0xffff
            var rightPrimary=rightPair and 0xffff
            if (leftPrimary != rightPrimary) {
                return if (leftPrimary < rightPrimary) Collation.LESS else Collation.GREATER
            }
            if (leftPair == EOS) break
            leftPair=leftPair ushr 16; rightPair=rightPair ushr 16
        }
        if (CollationSettings.getStrength(options) >= 1) {
        leftIndex=startIndex; rightIndex=startIndex; leftPair=0; rightPair=0
        while (true) {
            while (leftPair == 0) {
                if (leftIndex == left!!.length) { leftPair=EOS; break }
                val c=left.ciChar(leftIndex++).code
                if (c <= LATIN_MAX) {
                    leftPair=table!!.flAt(c)
                } else if (c in PUNCT_START until PUNCT_LIMIT) leftPair=table!!.flAt(c-PUNCT_START+LATIN_LIMIT)
                else leftPair=lookup(table,c)
                if (leftPair >= MIN_SHORT) {
                    leftPair=getSecondariesFromOneShortCE(leftPair); break
                } else if (leftPair > variableTop) {
                    leftPair=COMMON_SEC_PLUS_OFFSET; break
                } else {
                    var pairAndInc=nextPair(table,c,leftPair,left,leftIndex)
                    if (pairAndInc < 0) { ++leftIndex; pairAndInc=pairAndInc.inv() }
                    leftPair=getSecondaries(variableTop,pairAndInc.toInt())
                }
            }
            while (rightPair == 0) {
                if (rightIndex == right!!.length) { rightPair=EOS; break }
                val c=right.ciChar(rightIndex++).code
                if (c <= LATIN_MAX) {
                    rightPair=table!!.flAt(c)
                } else if (c in PUNCT_START until PUNCT_LIMIT) rightPair=table!!.flAt(c-PUNCT_START+LATIN_LIMIT)
                else rightPair=lookup(table,c)
                if (rightPair >= MIN_SHORT) {
                    rightPair=getSecondariesFromOneShortCE(rightPair); break
                } else if (rightPair > variableTop) {
                    rightPair=COMMON_SEC_PLUS_OFFSET; break
                } else {
                    var pairAndInc=nextPair(table,c,rightPair,right,rightIndex)
                    if (pairAndInc < 0) { ++rightIndex; pairAndInc=pairAndInc.inv() }
                    rightPair=getSecondaries(variableTop,pairAndInc.toInt())
                }
            }
            if (leftPair == rightPair) {
                if (leftPair == EOS) break
                leftPair=0; rightPair=0; continue
            }
            var leftSecondary=leftPair and 0xffff
            var rightSecondary=rightPair and 0xffff
            if (leftSecondary != rightSecondary) {
                if ((options and CollationSettings.BACKWARD_SECONDARY) != 0) return BAIL_OUT_RESULT
                return if (leftSecondary < rightSecondary) Collation.LESS else Collation.GREATER
            }
            if (leftPair == EOS) break
            leftPair=leftPair ushr 16; rightPair=rightPair ushr 16
        }
        }
        if ((options and CollationSettings.CASE_LEVEL) != 0) {
            val strengthIsPrimary=CollationSettings.getStrength(options) == 0
        leftIndex=startIndex; rightIndex=startIndex; leftPair=0; rightPair=0
        while (true) {
            while (leftPair == 0) {
                if (leftIndex == left!!.length) { leftPair=EOS; break }
                val c=left.ciChar(leftIndex++).code
                leftPair=if (c <= LATIN_MAX) table!!.flAt(c) else lookup(table,c)
                if (leftPair < MIN_LONG) {
                    var pairAndInc=nextPair(table,c,leftPair,left,leftIndex)
                    if (pairAndInc < 0) { ++leftIndex; pairAndInc=pairAndInc.inv() }
                    leftPair=pairAndInc.toInt()
                }
                leftPair=getCases(variableTop,strengthIsPrimary,leftPair)
            }
            while (rightPair == 0) {
                if (rightIndex == right!!.length) { rightPair=EOS; break }
                val c=right.ciChar(rightIndex++).code
                rightPair=if (c <= LATIN_MAX) table!!.flAt(c) else lookup(table,c)
                if (rightPair < MIN_LONG) {
                    var pairAndInc=nextPair(table,c,rightPair,right,rightIndex)
                    if (pairAndInc < 0) { ++rightIndex; pairAndInc=pairAndInc.inv() }
                    rightPair=pairAndInc.toInt()
                }
                rightPair=getCases(variableTop,strengthIsPrimary,rightPair)
            }
            if (leftPair == rightPair) {
                if (leftPair == EOS) break
                leftPair=0; rightPair=0; continue
            }
            var leftCase=leftPair and 0xffff
            var rightCase=rightPair and 0xffff
            if (leftCase != rightCase) {
                if ((options and CollationSettings.UPPER_FIRST) != 0) return if (leftCase < rightCase) Collation.GREATER else Collation.LESS
                return if (leftCase < rightCase) Collation.LESS else Collation.GREATER
            }
            if (leftPair == EOS) break
            leftPair=leftPair ushr 16; rightPair=rightPair ushr 16
        }
        }
        if (CollationSettings.getStrength(options) <= 1) return Collation.EQUAL
        val withCaseBits=CollationSettings.isTertiaryWithCaseBits(options)
        leftIndex=startIndex; rightIndex=startIndex; leftPair=0; rightPair=0
        while (true) {
            while (leftPair == 0) {
                if (leftIndex == left!!.length) { leftPair=EOS; break }
                val c=left.ciChar(leftIndex++).code
                leftPair=if (c <= LATIN_MAX) table!!.flAt(c) else lookup(table,c)
                if (leftPair < MIN_LONG) {
                    var pairAndInc=nextPair(table,c,leftPair,left,leftIndex)
                    if (pairAndInc < 0) { ++leftIndex; pairAndInc=pairAndInc.inv() }
                    leftPair=pairAndInc.toInt()
                }
                leftPair=getTertiaries(variableTop,withCaseBits,leftPair)
            }
            while (rightPair == 0) {
                if (rightIndex == right!!.length) { rightPair=EOS; break }
                val c=right.ciChar(rightIndex++).code
                rightPair=if (c <= LATIN_MAX) table!!.flAt(c) else lookup(table,c)
                if (rightPair < MIN_LONG) {
                    var pairAndInc=nextPair(table,c,rightPair,right,rightIndex)
                    if (pairAndInc < 0) { ++rightIndex; pairAndInc=pairAndInc.inv() }
                    rightPair=pairAndInc.toInt()
                }
                rightPair=getTertiaries(variableTop,withCaseBits,rightPair)
            }
            if (leftPair == rightPair) {
                if (leftPair == EOS) break
                leftPair=0; rightPair=0; continue
            }
            var leftTertiary=leftPair and 0xffff
            var rightTertiary=rightPair and 0xffff
            if (leftTertiary != rightTertiary) {
                if (CollationSettings.sortsTertiaryUpperCaseFirst(options)) {
                    if (leftTertiary > MERGE_WEIGHT) leftTertiary=leftTertiary xor CASE_MASK
                    if (rightTertiary > MERGE_WEIGHT) rightTertiary=rightTertiary xor CASE_MASK
                }
                return if (leftTertiary < rightTertiary) Collation.LESS else Collation.GREATER
            }
            if (leftPair == EOS) break
            leftPair=leftPair ushr 16; rightPair=rightPair ushr 16
        }
        if (CollationSettings.getStrength(options) <= 2) return Collation.EQUAL
        leftIndex=startIndex; rightIndex=startIndex; leftPair=0; rightPair=0
        while (true) {
            while (leftPair == 0) {
                if (leftIndex == left!!.length) { leftPair=EOS; break }
                val c=left.ciChar(leftIndex++).code
                leftPair=if (c <= LATIN_MAX) table!!.flAt(c) else lookup(table,c)
                if (leftPair < MIN_LONG) {
                    var pairAndInc=nextPair(table,c,leftPair,left,leftIndex)
                    if (pairAndInc < 0) { ++leftIndex; pairAndInc=pairAndInc.inv() }
                    leftPair=pairAndInc.toInt()
                }
                leftPair=getQuaternaries(variableTop,leftPair)
            }
            while (rightPair == 0) {
                if (rightIndex == right!!.length) { rightPair=EOS; break }
                val c=right.ciChar(rightIndex++).code
                rightPair=if (c <= LATIN_MAX) table!!.flAt(c) else lookup(table,c)
                if (rightPair < MIN_LONG) {
                    var pairAndInc=nextPair(table,c,rightPair,right,rightIndex)
                    if (pairAndInc < 0) { ++rightIndex; pairAndInc=pairAndInc.inv() }
                    rightPair=pairAndInc.toInt()
                }
                rightPair=getQuaternaries(variableTop,rightPair)
            }
            if (leftPair == rightPair) {
                if (leftPair == EOS) break
                leftPair=0; rightPair=0; continue
            }
            var leftQuaternary=leftPair and 0xffff
            var rightQuaternary=rightPair and 0xffff
            if (leftQuaternary != rightQuaternary) {
                return if (leftQuaternary < rightQuaternary) Collation.LESS else Collation.GREATER
            }
            if (leftPair == EOS) break
            leftPair=leftPair ushr 16; rightPair=rightPair ushr 16
        }
        return Collation.EQUAL
    }
    private fun lookup(table: CharArray?,c: Int): Int {
        checkAssertion { c > LATIN_MAX }
        return when { c in PUNCT_START until PUNCT_LIMIT -> table!!.flAt(c-PUNCT_START+LATIN_LIMIT); c == 0xfffe -> MERGE_WEIGHT; c == 0xffff -> MAX_SHORT or COMMON_SEC or LOWER_CASE or COMMON_TER; else -> BAIL_OUT }
    }
    private fun nextPair(table: CharArray?,c: Int,initialCE: Int,s16: CharSequence?,sIndex: Int): Long {
        var ce=initialCE
        if (ce >= MIN_LONG || ce < CONTRACTION) return ce.toLong()
        if (ce >= EXPANSION) {
            val index=NUM_FAST_CHARS+(ce and INDEX_MASK)
            return (table!!.flAt(index+1).toLong() shl 16) or table.flAt(index).toLong()
        }
        var index=NUM_FAST_CHARS+(ce and INDEX_MASK); var inc=false
        if (sIndex != s16!!.length) {
            var c2=s16.ciChar(sIndex).code
            if (c2 > LATIN_MAX) {
                if (c2 in PUNCT_START until PUNCT_LIMIT) c2=c2-PUNCT_START+LATIN_LIMIT
                else if (c2 == 0xfffe || c2 == 0xffff) c2=-1 else return BAIL_OUT.toLong()
            }
            var i=index; var head=table!!.flAt(i); var x: Int
            do { i+=head shr CONTR_LENGTH_SHIFT; head=table.flAt(i); x=head and CONTR_CHAR_MASK } while (x < c2)
            if (x == c2) { index=i; inc=true }
        }
        val length=table!!.flAt(index) shr CONTR_LENGTH_SHIFT
        if (length == 1) return BAIL_OUT.toLong()
        ce=table.flAt(index+1)
        val result=if (length == 2) ce.toLong() else (table.flAt(index+2).toLong() shl 16) or ce.toLong()
        return if (inc) result.inv() else result
    }
    private fun getPrimaries(variableTop: Int,pair: Int): Int {
        val ce=pair and 0xffff
        if (ce >= MIN_SHORT) return pair and TWO_SHORT_PRIMARIES_MASK
        if (ce > variableTop) return pair and TWO_LONG_PRIMARIES_MASK
        if (ce >= MIN_LONG) return 0
        return pair
    }
    private fun getSecondariesFromOneShortCE(initialCE: Int): Int {
        val ce=initialCE and SECONDARY_MASK
        return if (ce < MIN_SEC_HIGH) ce+SEC_OFFSET else ((ce+SEC_OFFSET) shl 16) or COMMON_SEC_PLUS_OFFSET
    }
    private fun getSecondaries(variableTop: Int,initialPair: Int): Int {
        var pair=initialPair
        if (pair <= 0xffff) {
            if (pair >= MIN_SHORT) pair=getSecondariesFromOneShortCE(pair)
            else if (pair > variableTop) pair=COMMON_SEC_PLUS_OFFSET else if (pair >= MIN_LONG) pair=0
        } else {
            val ce=pair and 0xffff
            if (ce >= MIN_SHORT) pair=(pair and TWO_SECONDARIES_MASK)+TWO_SEC_OFFSETS
            else if (ce > variableTop) pair=TWO_COMMON_SEC_PLUS_OFFSET else { checkAssertion { ce >= MIN_LONG }; pair=0 }
        }
        return pair
    }
    private fun getCases(variableTop: Int,strengthIsPrimary: Boolean,initialPair: Int): Int {
        var pair=initialPair
        if (pair <= 0xffff) {
            if (pair >= MIN_SHORT) {
                val ce=pair; pair=pair and CASE_MASK
                if (!strengthIsPrimary && (ce and SECONDARY_MASK) >= MIN_SEC_HIGH) pair=pair or (LOWER_CASE shl 16)
            } else if (pair > variableTop) pair=LOWER_CASE else if (pair >= MIN_LONG) pair=0
        } else {
            val ce=pair and 0xffff
            if (ce >= MIN_SHORT) pair=if (strengthIsPrimary && (pair and (SHORT_PRIMARY_MASK shl 16)) == 0) pair and CASE_MASK else pair and TWO_CASES_MASK
            else if (ce > variableTop) pair=TWO_LOWER_CASES else { checkAssertion { ce >= MIN_LONG }; pair=0 }
        }
        return pair
    }
    private fun getTertiaries(variableTop: Int,withCaseBits: Boolean,initialPair: Int): Int {
        var pair=initialPair
        if (pair <= 0xffff) {
            if (pair >= MIN_SHORT) {
                val ce=pair
                if (withCaseBits) {
                    pair=(pair and CASE_AND_TERTIARY_MASK)+TER_OFFSET
                    if ((ce and SECONDARY_MASK) >= MIN_SEC_HIGH) pair=pair or ((LOWER_CASE or COMMON_TER_PLUS_OFFSET) shl 16)
                } else {
                    pair=(pair and TERTIARY_MASK)+TER_OFFSET
                    if ((ce and SECONDARY_MASK) >= MIN_SEC_HIGH) pair=pair or (COMMON_TER_PLUS_OFFSET shl 16)
                }
            } else if (pair > variableTop) { pair=(pair and TERTIARY_MASK)+TER_OFFSET; if (withCaseBits) pair=pair or LOWER_CASE }
            else if (pair >= MIN_LONG) pair=0
        } else {
            val ce=pair and 0xffff
            if (ce >= MIN_SHORT) { pair=pair and (if (withCaseBits) TWO_CASES_MASK or TWO_TERTIARIES_MASK else TWO_TERTIARIES_MASK); pair+=TWO_TER_OFFSETS }
            else if (ce > variableTop) { pair=(pair and TWO_TERTIARIES_MASK)+TWO_TER_OFFSETS; if (withCaseBits) pair=pair or TWO_LOWER_CASES }
            else { checkAssertion { ce >= MIN_LONG }; pair=0 }
        }
        return pair
    }
    private fun getQuaternaries(variableTop: Int,initialPair: Int): Int {
        var pair=initialPair
        if (pair <= 0xffff) {
            if (pair >= MIN_SHORT) pair=if ((pair and SECONDARY_MASK) >= MIN_SEC_HIGH) TWO_SHORT_PRIMARIES_MASK else SHORT_PRIMARY_MASK
            else if (pair > variableTop) pair=SHORT_PRIMARY_MASK else if (pair >= MIN_LONG) pair=pair and LONG_PRIMARY_MASK
        } else {
            val ce=pair and 0xffff
            if (ce > variableTop) pair=TWO_SHORT_PRIMARIES_MASK else { checkAssertion { ce >= MIN_LONG }; pair=pair and TWO_LONG_PRIMARIES_MASK }
        }
        return pair
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
    private val assertionsEnabled=collationIteratorClassAssertionsEnabled(CollationFastLatin::class)
}
private fun CharArray.flAt(i: Int): Int { if (i < 0 || i >= size) throw IndexOutOfBoundsException(); return this[i].code }
private fun CharArray.flSet(i: Int,value: Int) { if (i < 0 || i >= size) throw IndexOutOfBoundsException(); this[i]=value.toChar() }
