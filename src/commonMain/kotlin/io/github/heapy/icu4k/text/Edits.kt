// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.BufferOverflowException

/**
 * Compact edit spans relating source text to transformed text. Lengths and
 * positions are UTF-16 code units. Fine iterators retain individual changes;
 * coarse iterators merge adjacent changes. Iterators also map source/destination
 * indices and positions within replacement-only output.
 */
class Edits {
    private var array = CharArray(STACK_CAPACITY)
    private var length = 0
    private var delta = 0
    private var numChanges = 0

    fun reset() { length = 0; delta = 0; numChanges = 0 }
    private fun setLastUnit(last: Int) { array[length - 1] = last.toChar() }
    private fun lastUnit(): Int = if (length > 0) array[length - 1].code else 0xffff

    fun addUnchanged(unchangedLength: Int) {
        require(unchangedLength >= 0) { "addUnchanged($unchangedLength): length must not be negative" }
        var remainingLength = unchangedLength
        val last = lastUnit()
        if (last < MAX_UNCHANGED) {
            val remaining = MAX_UNCHANGED - last
            if (remaining >= remainingLength) { setLastUnit(last + remainingLength); return }
            setLastUnit(MAX_UNCHANGED)
            remainingLength -= remaining
        }
        while (remainingLength >= MAX_UNCHANGED_LENGTH) {
            append(MAX_UNCHANGED)
            remainingLength -= MAX_UNCHANGED_LENGTH
        }
        if (remainingLength > 0) append(remainingLength - 1)
    }

    fun addReplace(oldLength: Int, newLength: Int) {
        require(oldLength >= 0 && newLength >= 0) { "addReplace($oldLength, $newLength): both lengths must be non-negative" }
        if (oldLength == 0 && newLength == 0) return
        numChanges++
        val newDelta = newLength - oldLength
        if (newDelta != 0) {
            if ((newDelta > 0 && delta >= 0 && newDelta > Int.MAX_VALUE - delta) ||
                (newDelta < 0 && delta < 0 && newDelta < Int.MIN_VALUE - delta)) throw IndexOutOfBoundsException()
            delta += newDelta
        }
        if (oldLength in 1..MAX_SHORT_CHANGE_OLD_LENGTH && newLength <= MAX_SHORT_CHANGE_NEW_LENGTH) {
            val unit = (oldLength shl 12) or (newLength shl 9)
            val last = lastUnit()
            if (last > MAX_UNCHANGED && last < MAX_SHORT_CHANGE &&
                (last and SHORT_CHANGE_NUM_MASK.inv()) == unit && (last and SHORT_CHANGE_NUM_MASK) < SHORT_CHANGE_NUM_MASK) {
                setLastUnit(last + 1)
                return
            }
            append(unit)
            return
        }
        var head = 0x7000
        if (oldLength < LENGTH_IN_1TRAIL && newLength < LENGTH_IN_1TRAIL) {
            append(head or (oldLength shl 6) or newLength)
        } else {
            if (array.size - length < 5) growArray()
            var limit = length + 1
            when {
                oldLength < LENGTH_IN_1TRAIL -> head = head or (oldLength shl 6)
                oldLength <= 0x7fff -> {
                    head = head or (LENGTH_IN_1TRAIL shl 6)
                    array[limit++] = (0x8000 or oldLength).toChar()
                }
                else -> {
                    head = head or ((LENGTH_IN_2TRAIL + (oldLength shr 30)) shl 6)
                    array[limit++] = (0x8000 or (oldLength shr 15)).toChar()
                    array[limit++] = (0x8000 or oldLength).toChar()
                }
            }
            when {
                newLength < LENGTH_IN_1TRAIL -> head = head or newLength
                newLength <= 0x7fff -> {
                    head = head or LENGTH_IN_1TRAIL
                    array[limit++] = (0x8000 or newLength).toChar()
                }
                else -> {
                    head = head or (LENGTH_IN_2TRAIL + (newLength shr 30))
                    array[limit++] = (0x8000 or (newLength shr 15)).toChar()
                    array[limit++] = (0x8000 or newLength).toChar()
                }
            }
            array[length] = head.toChar()
            length = limit
        }
    }

    private fun append(unit: Int) {
        if (length == array.size) growArray()
        array[length++] = unit.toChar()
    }
    private fun growArray() {
        val newCapacity = when {
            array.size == STACK_CAPACITY -> 2000
            array.size == Int.MAX_VALUE -> throw BufferOverflowException()
            array.size >= Int.MAX_VALUE / 2 -> Int.MAX_VALUE
            else -> array.size * 2
        }
        if (newCapacity - array.size < 5) throw BufferOverflowException()
        array = array.copyOf(newCapacity)
    }
    fun lengthDelta(): Int = delta
    fun hasChanges(): Boolean = numChanges != 0
    fun numberOfChanges(): Int = numChanges

    class Iterator internal constructor(
        private val array: CharArray,
        private val length: Int,
        private val onlyChanges: Boolean,
        private val coarse: Boolean,
    ) {
        private var index = 0
        private var remaining = 0
        private var direction = 0
        private var changed = false
        private var oldLengthValue = 0
        private var newLengthValue = 0
        private var srcIndex = 0
        private var replIndex = 0
        private var destIndex = 0

        private fun readLength(head: Int): Int = when {
            head < LENGTH_IN_1TRAIL -> head
            head < LENGTH_IN_2TRAIL -> array[index++].code and 0x7fff
            else -> {
                val value = ((head and 1) shl 30) or ((array[index].code and 0x7fff) shl 15) or (array[index + 1].code and 0x7fff)
                index += 2
                value
            }
        }
        private fun updateNextIndexes() {
            srcIndex += oldLengthValue
            if (changed) replIndex += newLengthValue
            destIndex += newLengthValue
        }
        private fun updatePreviousIndexes() {
            srcIndex -= oldLengthValue
            if (changed) replIndex -= newLengthValue
            destIndex -= newLengthValue
        }
        private fun noNext(): Boolean {
            direction = 0
            changed = false
            oldLengthValue = 0
            newLengthValue = 0
            return false
        }
        fun next(): Boolean = next(onlyChanges)
        private fun next(changesOnly: Boolean): Boolean {
            if (direction > 0) {
                updateNextIndexes()
            } else {
                if (direction < 0 && remaining > 0) {
                    index++
                    direction = 1
                    return true
                }
                direction = 1
            }
            if (remaining >= 1) {
                if (remaining > 1) { remaining--; return true }
                remaining = 0
            }
            if (index >= length) return noNext()
            var unit = array[index++].code
            if (unit <= MAX_UNCHANGED) {
                changed = false
                oldLengthValue = unit + 1
                while (index < length) {
                    unit = array[index].code
                    if (unit > MAX_UNCHANGED) break
                    index++
                    oldLengthValue += unit + 1
                }
                newLengthValue = oldLengthValue
                if (changesOnly) {
                    updateNextIndexes()
                    if (index >= length) return noNext()
                    index++
                } else return true
            }
            changed = true
            if (unit <= MAX_SHORT_CHANGE) {
                val old = unit shr 12
                val new = (unit shr 9) and MAX_SHORT_CHANGE_NEW_LENGTH
                val count = (unit and SHORT_CHANGE_NUM_MASK) + 1
                if (coarse) {
                    oldLengthValue = count * old
                    newLengthValue = count * new
                } else {
                    oldLengthValue = old
                    newLengthValue = new
                    if (count > 1) remaining = count
                    return true
                }
            } else {
                oldLengthValue = readLength((unit shr 6) and 0x3f)
                newLengthValue = readLength(unit and 0x3f)
                if (!coarse) return true
            }
            while (index < length) {
                unit = array[index].code
                if (unit <= MAX_UNCHANGED) break
                index++
                if (unit <= MAX_SHORT_CHANGE) {
                    val count = (unit and SHORT_CHANGE_NUM_MASK) + 1
                    oldLengthValue += (unit shr 12) * count
                    newLengthValue += ((unit shr 9) and MAX_SHORT_CHANGE_NEW_LENGTH) * count
                } else {
                    oldLengthValue += readLength((unit shr 6) and 0x3f)
                    newLengthValue += readLength(unit and 0x3f)
                }
            }
            return true
        }

        private fun previous(): Boolean {
            if (direction >= 0) {
                if (direction > 0) {
                    if (remaining > 0) { index--; direction = -1; return true }
                    updateNextIndexes()
                }
                direction = -1
            }
            if (remaining > 0) {
                val unit = array[index].code
                if (remaining <= (unit and SHORT_CHANGE_NUM_MASK)) {
                    remaining++
                    updatePreviousIndexes()
                    return true
                }
                remaining = 0
            }
            if (index <= 0) return noNext()
            var unit = array[--index].code
            if (unit <= MAX_UNCHANGED) {
                changed = false
                oldLengthValue = unit + 1
                while (index > 0) {
                    unit = array[index - 1].code
                    if (unit > MAX_UNCHANGED) break
                    index--
                    oldLengthValue += unit + 1
                }
                newLengthValue = oldLengthValue
                updatePreviousIndexes()
                return true
            }
            changed = true
            if (unit <= MAX_SHORT_CHANGE) {
                val old = unit shr 12
                val new = (unit shr 9) and MAX_SHORT_CHANGE_NEW_LENGTH
                val count = (unit and SHORT_CHANGE_NUM_MASK) + 1
                if (coarse) {
                    oldLengthValue = count * old
                    newLengthValue = count * new
                } else {
                    oldLengthValue = old
                    newLengthValue = new
                    if (count > 1) remaining = 1
                    updatePreviousIndexes()
                    return true
                }
            } else {
                if (unit <= 0x7fff) {
                    oldLengthValue = readLength((unit shr 6) and 0x3f)
                    newLengthValue = readLength(unit and 0x3f)
                } else {
                    do { unit = array[--index].code } while (unit > 0x7fff)
                    val headIndex = index++
                    oldLengthValue = readLength((unit shr 6) and 0x3f)
                    newLengthValue = readLength(unit and 0x3f)
                    index = headIndex
                }
                if (!coarse) { updatePreviousIndexes(); return true }
            }
            while (index > 0) {
                unit = array[index - 1].code
                if (unit <= MAX_UNCHANGED) break
                index--
                if (unit <= MAX_SHORT_CHANGE) {
                    val count = (unit and SHORT_CHANGE_NUM_MASK) + 1
                    oldLengthValue += (unit shr 12) * count
                    newLengthValue += ((unit shr 9) and MAX_SHORT_CHANGE_NEW_LENGTH) * count
                } else if (unit <= 0x7fff) {
                    val headIndex = index++
                    oldLengthValue += readLength((unit shr 6) and 0x3f)
                    newLengthValue += readLength(unit and 0x3f)
                    index = headIndex
                }
            }
            updatePreviousIndexes()
            return true
        }

        fun findSourceIndex(i: Int): Boolean = findIndex(i, true) == 0
        fun findDestinationIndex(i: Int): Boolean = findIndex(i, false) == 0
        private fun findIndex(i: Int, findSource: Boolean): Int {
            if (i < 0) return -1
            var spanStart = if (findSource) srcIndex else destIndex
            var spanLength = if (findSource) oldLengthValue else newLengthValue
            if (i < spanStart) {
                if (i >= spanStart / 2) {
                    while (true) {
                        previous()
                        spanStart = if (findSource) srcIndex else destIndex
                        if (i >= spanStart) return 0
                        if (remaining > 0) {
                            spanLength = if (findSource) oldLengthValue else newLengthValue
                            val unit = array[index].code
                            val count = (unit and SHORT_CHANGE_NUM_MASK) + 1 - remaining
                            val len = count * spanLength
                            if (i >= spanStart - len) {
                                val n = (spanStart - i - 1) / spanLength + 1
                                srcIndex -= n * oldLengthValue
                                replIndex -= n * newLengthValue
                                destIndex -= n * newLengthValue
                                remaining += n
                                return 0
                            }
                            srcIndex -= count * oldLengthValue
                            replIndex -= count * newLengthValue
                            destIndex -= count * newLengthValue
                            remaining = 0
                        }
                    }
                }
                direction = 0
                index = 0
                remaining = 0
                oldLengthValue = 0
                newLengthValue = 0
                srcIndex = 0
                replIndex = 0
                destIndex = 0
            } else if (i < spanStart + spanLength) return 0
            while (next(false)) {
                spanStart = if (findSource) srcIndex else destIndex
                spanLength = if (findSource) oldLengthValue else newLengthValue
                if (i < spanStart + spanLength) return 0
                if (remaining > 1) {
                    val len = remaining * spanLength
                    if (i < spanStart + len) {
                        val n = (i - spanStart) / spanLength
                        srcIndex += n * oldLengthValue
                        replIndex += n * newLengthValue
                        destIndex += n * newLengthValue
                        remaining -= n
                        return 0
                    }
                    oldLengthValue *= remaining
                    newLengthValue *= remaining
                    remaining = 0
                }
            }
            return 1
        }
        fun destinationIndexFromSourceIndex(i: Int): Int {
            val where = findIndex(i, true)
            return when {
                where < 0 -> 0
                where > 0 || i == srcIndex -> destIndex
                changed -> destIndex + newLengthValue
                else -> destIndex + (i - srcIndex)
            }
        }
        fun sourceIndexFromDestinationIndex(i: Int): Int {
            val where = findIndex(i, false)
            return when {
                where < 0 -> 0
                where > 0 || i == destIndex -> srcIndex
                changed -> srcIndex + oldLengthValue
                else -> srcIndex + (i - destIndex)
            }
        }
        fun hasChange(): Boolean = changed
        fun oldLength(): Int = oldLengthValue
        fun newLength(): Int = newLengthValue
        fun sourceIndex(): Int = srcIndex
        fun replacementIndex(): Int = replIndex
        fun destinationIndex(): Int = destIndex
        override fun toString(): String = buildString {
            append(this@Iterator::class.simpleName)
            append("{ src[$srcIndex..${srcIndex + oldLengthValue}]")
            append(if (changed) " ⇝ dest[" else " ≡ dest[")
            append("$destIndex..${destIndex + newLengthValue}]")
            if (changed) append(", repl[$replIndex..${replIndex + newLengthValue}] }") else append(" (no-change) }")
        }
    }

    fun getCoarseChangesIterator(): Iterator = Iterator(array, length, true, true)
    fun getCoarseIterator(): Iterator = Iterator(array, length, false, true)
    fun getFineChangesIterator(): Iterator = Iterator(array, length, true, false)
    fun getFineIterator(): Iterator = Iterator(array, length, false, false)

    /** Composes a→b and b→c edits, appending the corresponding a→c edits. */
    fun mergeAndAppend(ab: Edits, bc: Edits): Edits {
        val abIterator = ab.getFineIterator()
        val bcIterator = bc.getFineIterator()
        var abHasNext = true
        var bcHasNext = true
        var aLength = 0
        var abLength = 0
        var bcLength = 0
        var cLength = 0
        var pendingA = 0
        var pendingC = 0
        while (true) {
            // Fetch b→c first: insertions must precede a→b deletions at the same b index.
            if (bcLength == 0) {
                if (bcHasNext) bcHasNext = bcIterator.next()
                if (bcHasNext) {
                    bcLength = bcIterator.oldLength()
                    cLength = bcIterator.newLength()
                    if (bcLength == 0) {
                        if (abLength == 0 || !abIterator.hasChange()) {
                            addReplace(pendingA, pendingC + cLength)
                            pendingA = 0; pendingC = 0
                        } else pendingC += cLength
                        continue
                    }
                }
            }
            if (abLength == 0) {
                if (abHasNext) abHasNext = abIterator.next()
                if (abHasNext) {
                    aLength = abIterator.oldLength()
                    abLength = abIterator.newLength()
                    if (abLength == 0) {
                        if (bcLength == bcIterator.oldLength() || !bcIterator.hasChange()) {
                            addReplace(pendingA + aLength, pendingC)
                            pendingA = 0; pendingC = 0
                        } else pendingA += aLength
                        continue
                    }
                } else if (bcLength == 0) break
                else throw IllegalArgumentException("The ab output string is shorter than the bc input string.")
            }
            if (bcLength == 0) throw IllegalArgumentException("The bc input string is shorter than the ab output string.")
            if (!abIterator.hasChange() && !bcIterator.hasChange()) {
                if (pendingA != 0 || pendingC != 0) {
                    addReplace(pendingA, pendingC)
                    pendingA = 0; pendingC = 0
                }
                val unchanged = minOf(aLength, cLength)
                addUnchanged(unchanged)
                aLength -= unchanged; abLength = aLength
                cLength -= unchanged; bcLength = cLength
                continue
            }
            if (!abIterator.hasChange() && bcIterator.hasChange()) {
                if (abLength >= bcLength) {
                    addReplace(pendingA + bcLength, pendingC + cLength)
                    pendingA = 0; pendingC = 0
                    abLength -= bcLength; aLength = abLength
                    bcLength = 0
                    continue
                }
            } else if (abIterator.hasChange() && !bcIterator.hasChange()) {
                if (abLength <= bcLength) {
                    addReplace(pendingA + aLength, pendingC + abLength)
                    pendingA = 0; pendingC = 0
                    bcLength -= abLength; cLength = bcLength
                    abLength = 0
                    continue
                }
            } else if (abLength == bcLength) {
                addReplace(pendingA + aLength, pendingC + cLength)
                pendingA = 0; pendingC = 0
                abLength = 0; bcLength = 0
                continue
            }
            pendingA += aLength
            pendingC += cLength
            if (abLength < bcLength) {
                bcLength -= abLength
                cLength = 0; abLength = 0
            } else {
                abLength -= bcLength
                aLength = 0; bcLength = 0
            }
        }
        if (pendingA != 0 || pendingC != 0) addReplace(pendingA, pendingC)
        return this
    }

    companion object {
        private const val MAX_UNCHANGED_LENGTH = 0x1000
        private const val MAX_UNCHANGED = MAX_UNCHANGED_LENGTH - 1
        private const val MAX_SHORT_CHANGE_OLD_LENGTH = 6
        private const val MAX_SHORT_CHANGE_NEW_LENGTH = 7
        private const val SHORT_CHANGE_NUM_MASK = 0x1ff
        private const val MAX_SHORT_CHANGE = 0x6fff
        private const val LENGTH_IN_1TRAIL = 61
        private const val LENGTH_IN_2TRAIL = 62
        private const val STACK_CAPACITY = 100
    }
}
