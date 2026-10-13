// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.impl.UCharacterName
import io.github.heapy.icu4k.impl.UCharacterNameChoice
import io.github.heapy.icu4k.util.ValueIterator

/** Data-driven group iteration interleaved with algorithmic name ranges. */
internal class UCharacterNameIterator(name: UCharacterName?, private val choice: Int) : ValueIterator {
    private val name = requireNotNull(name) { "UCharacterName name argument cannot be null. Missing unames.icu?" }
    private var start = 0
    private var limit = 0x110000
    private var current = start
    private var groupIndex = -1
    private var algorithmIndex = -1
    private val offsets = CharArray(UCharacterName.LINES_PER_GROUP_ + 1)
    private val lengths = CharArray(UCharacterName.LINES_PER_GROUP_ + 1)

    override fun next(element: ValueIterator.Element): Boolean {
        if (current >= limit) return false
        if (choice == UCharacterNameChoice.UNICODE_CHAR_NAME || choice == UCharacterNameChoice.EXTENDED_CHAR_NAME) {
            val length = name.getAlgorithmLength()
            if (algorithmIndex < length) {
                while (algorithmIndex < length) {
                    if (algorithmIndex < 0 || name.getAlgorithmEnd(algorithmIndex) < current) ++algorithmIndex else break
                }
                if (algorithmIndex < length) {
                    val algorithmStart = name.getAlgorithmStart(algorithmIndex)
                    if (current < algorithmStart) {
                        val end = if (limit <= algorithmStart) limit else algorithmStart
                        if (!iterateGroup(element, end)) { ++current; return true }
                    }
                    if (current >= limit) return false
                    element.integer = current
                    element.value = name.getAlgorithmName(algorithmIndex, current)
                    groupIndex = -1
                    ++current
                    return true
                }
            }
        }
        if (!iterateGroup(element, limit)) { ++current; return true }
        if (choice == UCharacterNameChoice.EXTENDED_CHAR_NAME && !iterateExtended(element, limit)) { ++current; return true }
        return false
    }
    override fun reset() { current = start; groupIndex = -1; algorithmIndex = -1 }
    override fun setRange(start: Int, limit: Int) {
        require(start < limit) { "start or limit has to be valid Unicode codepoints and start < limit" }
        this.start = maxOf(start, 0)
        this.limit = minOf(limit, 0x110000)
        current = this.start
    }
    private fun iterateSingleGroup(result: ValueIterator.Element, limit: Int): Boolean {
        val index = name.getGroupLengths(groupIndex, offsets, lengths)
        while (current < limit) {
            val offset = UCharacterName.getGroupOffset(current)
            var value = name.getGroupName(index + offsets[offset].code, lengths[offset].code, choice)
            if (value.isNullOrEmpty() && choice == UCharacterNameChoice.EXTENDED_CHAR_NAME) value = name.getExtendedName(current)
            if (!value.isNullOrEmpty()) {
                result.integer = current
                result.value = value
                return false
            }
            ++current
        }
        return true
    }
    private fun iterateGroup(result: ValueIterator.Element, limit: Int): Boolean {
        if (groupIndex < 0) groupIndex = name.getGroup(current)
        while (groupIndex < name.m_groupcount_ && current < limit) {
            val startMSB = UCharacterName.getCodepointMSB(current)
            val groupMSB = name.getGroupMSB(groupIndex)
            if (startMSB == groupMSB) {
                if (startMSB == UCharacterName.getCodepointMSB(limit - 1)) return iterateSingleGroup(result, limit)
                if (!iterateSingleGroup(result, UCharacterName.getGroupLimit(groupMSB))) return false
                ++groupIndex
            } else if (startMSB > groupMSB) ++groupIndex
            else {
                val min = minOf(UCharacterName.getGroupMin(groupMSB), limit)
                if (choice == UCharacterNameChoice.EXTENDED_CHAR_NAME && !iterateExtended(result, min)) return false
                current = min
            }
        }
        return true
    }
    private fun iterateExtended(result: ValueIterator.Element, limit: Int): Boolean {
        while (current < limit) {
            val value = name.getExtendedOr10Name(current)
            if (value.isNotEmpty()) {
                result.integer = current
                result.value = value
                return false
            }
            ++current
        }
        return true
    }
}
