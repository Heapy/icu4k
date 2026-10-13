// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.util.BytesTrie
import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** Original pnames.icu value maps, name groups and loose alias trie lookup. */
internal class UPropertyAliases private constructor() {
    private val valueMaps: IntArray
    private val bytesTries: ByteArray
    private val nameGroups: String
    init {
        val bytes = ICUBinary.getRequiredData("pnames.icu")
        ICUBinary.readHeader(bytes, 0x706e616d, ICUBinary.Authenticate { it[0].toInt() == 2 })
        val count = bytes.readInt() / 4
        if (count < 8) throw ICUUncheckedIOException("pnames.icu: not enough indexes")
        val indexes = IntArray(count)
        indexes[0] = count * 4
        for (i in 1 until count) indexes[i] = bytes.readInt()
        valueMaps = ICUBinary.getInts(bytes, (indexes[1] - indexes[0]) / 4, 0)
        bytesTries = bytes.readBytes(indexes[2] - indexes[1])
        nameGroups = buildString(indexes[3] - indexes[2]) {
            repeat(indexes[3] - indexes[2]) { append(bytes.readByte().toInt().toChar()) }
        }
    }
    private fun findProperty(property: Int): Int {
        var i = 1
        repeat(valueMaps[0]) {
            val start = valueMaps[i]; val limit = valueMaps[i + 1]; i += 2
            if (property < start) return 0
            if (property < limit) return i + (property - start) * 2
            i += (limit - start) * 2
        }
        return 0
    }
    private fun findPropertyValueNameGroup(initialIndex: Int, value: Int): Int {
        if (initialIndex == 0) return 0
        var index = initialIndex + 1
        val numRanges = valueMaps[index++]
        if (numRanges < 16) {
            repeat(numRanges) {
                val start = valueMaps[index]; val limit = valueMaps[index + 1]; index += 2
                if (value < start) return 0
                if (value < limit) return valueMaps[index + value - start]
                index += limit - start
            }
        } else {
            val valuesStart = index
            val namesStart = index + numRanges - 16
            do {
                val v = valueMaps[index]
                if (value < v) return 0
                if (value == v) return valueMaps[namesStart + index - valuesStart]
                ++index
            } while (index < namesStart)
        }
        return 0
    }
    private fun getName(initialIndex: Int, choice: Int): String? {
        var index = initialIndex
        val count = nameGroups[index++].code
        if (choice < 0 || choice >= count) throw IllegalIcuArgumentException("Invalid property (value) name choice")
        repeat(choice) { while (nameGroups[index++] != '\u0000') { } }
        val start = index
        while (nameGroups[index] != '\u0000') ++index
        return if (start == index) null else nameGroups.substring(start, index)
    }
    private fun propertyIndex(property: Int): Int = findProperty(property).also {
        require(it != 0) { "Invalid property enum $property (0x${property.toUInt().toString(16)})" }
    }
    private fun noValues(property: Int): Nothing = throw IllegalArgumentException("Property $property (0x${property.toUInt().toString(16)}) does not have named values")
    fun getPropertyName(property: Int, nameChoice: Int): String? = getName(valueMaps[propertyIndex(property)], nameChoice)
    fun getPropertyValueName(property: Int, value: Int, nameChoice: Int): String? {
        val offset = findPropertyValueNameGroup(valueMaps[propertyIndex(property) + 1], value)
        if (offset == 0) noValues(property)
        return getName(offset, nameChoice)
    }
    private fun getPropertyOrValueEnum(offset: Int, alias: CharSequence): Int {
        val trie = BytesTrie(bytesTries, offset)
        var result = BytesTrie.Result.NO_VALUE
        for (char in alias) {
            val c = char.code
            if (isDelimiter(c)) continue
            if (!result.hasNext()) return UProperty.UNDEFINED
            result = trie.next(asciiToLowercase(c))
        }
        return if (result.hasValue()) trie.getValue() else UProperty.UNDEFINED
    }
    fun getPropertyEnum(alias: CharSequence): Int = getPropertyOrValueEnum(0, alias)
    fun getPropertyValueEnum(property: Int, alias: CharSequence): Int {
        val index = valueMaps[propertyIndex(property) + 1]
        if (index == 0) noValues(property)
        return getPropertyOrValueEnum(valueMaps[index], alias)
    }
    fun getPropertyValueEnumNoThrow(property: Int, alias: CharSequence): Int {
        val propertyIndex = findProperty(property)
        if (propertyIndex == 0) return UProperty.UNDEFINED
        val index = valueMaps[propertyIndex + 1]
        return if (index == 0) UProperty.UNDEFINED else getPropertyOrValueEnum(valueMaps[index], alias)
    }
    companion object {
        val INSTANCE: UPropertyAliases by lazy { UPropertyAliases() }
        private fun asciiToLowercase(c: Int): Int = if (c in 65..90) c + 32 else c
        private fun isDelimiter(c: Int): Boolean = c == 45 || c == 95 || c == 32 || c in 9..13
        fun compare(a: String, b: String): Int {
            var i = 0; var j = 0
            var ca = 0; var cb = 0
            while (true) {
                while (i < a.length) { ca = a[i].code; if (!isDelimiter(ca)) break; ++i }
                while (j < b.length) { cb = b[j].code; if (!isDelimiter(cb)) break; ++j }
                val endA = i == a.length; val endB = j == b.length
                if (endA) { if (endB) return 0; ca = 0 } else if (endB) cb = 0
                val result = asciiToLowercase(ca) - asciiToLowercase(cb)
                if (result != 0) return result
                ++i; ++j
            }
        }
    }
}
