// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** ICU converter aliases and per-standard canonical/preferred names. */
internal object UConverterAlias {
    const val UNNORMALIZED = 0
    const val STD_NORMALIZED = 1
    const val AMBIGUOUS_ALIAS_MAP_BIT = 0x8000
    const val CONTAINS_OPTION_BIT = 0x4000
    const val CONVERTER_INDEX_MASK = 0xfff
    const val NUM_RESERVED_TAGS = 2
    const val NUM_HIDDEN_TAGS = 1

    private class AliasData {
        val converters: CharArray
        val tags: CharArray
        val aliases: CharArray
        val untagged: CharArray
        val tagged: CharArray
        val lists: CharArray
        val options: CharArray
        val strings: ByteArray
        val normalized: ByteArray
        init {
            val bytes = ICUBinary.getRequiredData("cnvalias.icu")
            val toc = UConverterAliasDataReader(bytes).readToc(10)
            if (toc[0] < 9) throw ICUUncheckedIOException("Invalid data format.")
            converters = bytes.readChars(toc[1])
            tags = bytes.readChars(toc[2])
            aliases = bytes.readChars(toc[3])
            untagged = bytes.readChars(toc[4])
            tagged = bytes.readChars(toc[5])
            lists = bytes.readChars(toc[6])
            options = bytes.readChars(toc[7])
            strings = bytes.readBytes(toc[8] * 2)
            normalized = bytes.readBytes(toc[9] * 2)
            if (options[0].code != STD_NORMALIZED) throw ICUUncheckedIOException("Unsupported alias normalization")
        }
        fun string(index: Int): String = extract(strings, index * 2)
        fun normalized(index: Int): String = extract(normalized, index * 2)
        private fun extract(table: ByteArray, start: Int): String {
            var end = start
            // Retain the source's length calculation even for an unterminated table.
            while (end < table.size && table[end++].toInt() != 0) { }
            val length = end - start - 1
            if (length < 0) throw IndexOutOfBoundsException()
            return buildString { repeat(length) { append((table[start + it].toInt() and 255).toChar()) } }
        }
    }
    private val data by lazy { AliasData() }
    private val available by lazy { data.converters.map { data.string(it.code) } }
    private fun isAlias(alias: String?): Boolean {
        require(alias != null) { "Alias param is null!" }
        return alias.isNotEmpty()
    }
    private fun findConverter(alias: String, ambiguous: BooleanArray): Int {
        val normalized = stripForCompare(StringBuilder(), alias).toString()
        var start = 0
        var limit = data.untagged.size
        var lastMid = Int.MAX_VALUE
        while (true) {
            val mid = (start + limit) / 2
            if (lastMid == mid) return Int.MAX_VALUE
            lastMid = mid
            val result = normalized.compareTo(data.normalized(data.aliases[mid].code))
            when {
                result < 0 -> limit = mid
                result > 0 -> start = mid
                else -> {
                    if ((data.untagged[mid].code and AMBIGUOUS_ALIAS_MAP_BIT) != 0) ambiguous[0] = true
                    return data.untagged[mid].code and CONVERTER_INDEX_MASK
                }
            }
        }
    }
    private fun charType(c: Char): Int = when (c) {
        '0' -> 1
        in '1'..'9' -> 2
        in 'A'..'Z' -> c.code + 32
        in 'a'..'z' -> c.code
        else -> 0
    }
    fun stripForCompare(destination: StringBuilder, name: String): StringBuilder {
        var index = 0
        var afterDigit = false
        while (index < name.length) {
            var c = name[index++]
            when (val type = charType(c)) {
                0 -> { afterDigit = false; continue }
                1 -> if (!afterDigit && index < name.length && charType(name[index]) in 1..2) continue
                2 -> afterDigit = true
                else -> { c = type.toChar(); afterDigit = false }
            }
            destination.append(c)
        }
        return destination
    }
    private fun charAt(name: String, index: Int): Char {
        // A source comparator branch can address the end of the second name.
        if (index !in name.indices) throw IndexOutOfBoundsException()
        return name[index]
    }
    fun compareNames(name1: String, name2: String): Int {
        var first = 0
        var second = 0
        var c1 = '\u0000'
        var c2 = '\u0000'
        var afterDigit1 = false
        var afterDigit2 = false
        while (true) {
            while (first < name1.length) {
                c1 = charAt(name1, first++)
                when (val type = charType(c1)) {
                    0 -> { afterDigit1 = false; continue }
                    1 -> if (!afterDigit1 && first < name1.length && charType(charAt(name1, first)) in 1..2) continue
                    2 -> afterDigit1 = true
                    else -> { c1 = type.toChar(); afterDigit1 = false }
                }
                break
            }
            while (second < name2.length) {
                c2 = charAt(name2, second++)
                when (val type = charType(c2)) {
                    0 -> { afterDigit2 = false; continue }
                    // The source uses the first name's index/length here.
                    1 -> if (!afterDigit2 && first < name1.length && charType(charAt(name2, second)) in 1..2) continue
                    2 -> afterDigit2 = true
                    else -> { c2 = type.toChar(); afterDigit2 = false }
                }
                break
            }
            // Preserve the source's termination order, including its last-character equivalence.
            if (first >= name1.length && second >= name2.length) return 0
            val result = c1.code - c2.code
            if (result != 0) return result
        }
    }
    fun countAliases(alias: String?): Int {
        val d = data
        if (isAlias(alias)) {
            val converter = findConverter(alias!!, BooleanArray(1))
            if (converter < d.converters.size) {
                val offset = d.tagged[(d.tags.size - 1) * d.converters.size + converter].code
                if (offset != 0) return d.lists[offset].code
            }
        }
        return 0
    }
    fun getAlias(alias: String?, index: Int): String? {
        val d = data
        if (isAlias(alias)) {
            val converter = findConverter(alias!!, BooleanArray(1))
            if (converter < d.converters.size) {
                val offset = d.tagged[(d.tags.size - 1) * d.converters.size + converter].code
                if (offset != 0) return d.string(listItem(offset + 1 + index).code)
            }
        }
        return null
    }
    private fun listItem(index: Int): Char {
        if (index !in data.lists.indices) throw IndexOutOfBoundsException()
        return data.lists[index]
    }
    fun getStandardName(alias: String?, standard: String?): String? {
        val d = data
        if (isAlias(alias)) {
            val offset = findTaggedAliasListsOffset(alias!!, standard)
            if (offset > 0 && offset < d.lists.size && d.lists[0].code != 0) return d.string(d.lists[offset + 1].code)
        }
        return null
    }
    fun getCanonicalName(alias: String?, standard: String?): String? {
        val d = data
        if (isAlias(alias)) {
            val converter = findTaggedConverterNum(alias!!, standard)
            if (converter < d.converters.size) return d.string(d.converters[converter].code)
        }
        return null
    }
    private fun getTagNumber(standard: String?): Int {
        val tag = standard!!
        for (index in data.tags.indices) if (tag == data.string(data.tags[index].code)) return index
        return Int.MAX_VALUE
    }
    private fun findTaggedAliasListsOffset(alias: String, standard: String?): Int {
        val d = data
        val tag = getTagNumber(standard)
        val ambiguous = BooleanArray(1)
        val converter = findConverter(alias, ambiguous)
        if (tag < d.tags.size - NUM_HIDDEN_TAGS && converter < d.converters.size) {
            val offset = d.tagged[tag * d.converters.size + converter].code
            if (offset != 0 && d.lists[offset + 1].code != 0) return offset
            if (ambiguous[0]) for (index in d.tagged.indices) {
                val candidate = d.tagged[index].code
                if (candidate != 0 && isAliasInList(alias, candidate)) {
                    val candidateConverter = index % d.converters.size
                    val preferred = d.tagged[tag * d.converters.size + candidateConverter].code
                    if (preferred != 0 && d.lists[preferred + 1].code != 0) return preferred
                }
            }
            return 0
        }
        return Int.MAX_VALUE
    }
    private fun findTaggedConverterNum(alias: String, standard: String?): Int {
        val d = data
        val tag = getTagNumber(standard)
        val ambiguous = BooleanArray(1)
        val converter = findConverter(alias, ambiguous)
        if (tag < d.tags.size - NUM_HIDDEN_TAGS && converter < d.converters.size) {
            val offset = d.tagged[tag * d.converters.size + converter].code
            if (offset != 0 && isAliasInList(alias, offset)) return converter
            if (ambiguous[0]) {
                val start = tag * d.converters.size
                for (index in start..<(start + d.converters.size)) {
                    val candidate = d.tagged[index].code
                    if (candidate != 0 && isAliasInList(alias, candidate)) return index - start
                }
            }
        }
        return Int.MAX_VALUE
    }
    private fun isAliasInList(alias: String, offset: Int): Boolean {
        if (offset != 0) {
            for (index in 0..<data.lists[offset].code) {
                val name = data.lists[offset + 1 + index].code
                if (name != 0 && compareNames(alias, data.string(name)) == 0) return true
            }
        }
        return false
    }
    fun countAvailable(): Int = try { available.size } catch (_: ICUUncheckedIOException) { -1 }
    fun getAvailableName(index: Int): String? = try {
        if (index in 0..0xffff && index < available.size) available[index] else null
    } catch (_: ICUUncheckedIOException) { null }
}
