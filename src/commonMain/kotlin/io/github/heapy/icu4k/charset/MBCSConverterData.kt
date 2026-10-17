// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceLoader
import io.github.heapy.icu4k.impl.InvalidFormatException
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException

internal class UConverterSharedData(val staticData: UConverterStaticData) {
    var mbcs = UConverterMBCSTable()
}

/** Prepared, per-converter tables and options shared by its streaming codecs. */
internal class MBCSConverterData private constructor(
    val name: String,
    val sharedData: UConverterSharedData,
    var options: Int,
) {
    var maxBytesPerChar = sharedData.staticData.maxBytesPerChar.toInt()
        private set
    val minBytesPerChar = sharedData.staticData.minBytesPerChar.toInt()
    val substitution = sharedData.staticData.subChar.copyOf(sharedData.staticData.subCharLen.toInt())
    val subChar1 = sharedData.staticData.subChar1
    init { initialize(options) }

    private fun initialize(requestedOptions: Int) {
        val table = sharedData.mbcs
        val output = table.outputType.toInt()
        var requested = requestedOptions
        if (output == 0xdb) {
            requested = requested and 0x10.inv()
            options = requested
        }
        if ((requested and 0x10) != 0 && table.swapLFNLStateTable == null) {
            try {
                if (!swapLFNL()) options = requested and 0x10.inv()
            } catch (_: Exception) { return }
        }
        val lower = name.lowercase()
        options = options or when {
            "gb18030" in lower -> 0x8000
            "keis" in lower -> 0x1000
            "jef" in lower -> 0x2000
            "jips" in lower -> 0x4000
            else -> 0
        }
        if (output == 12) maxBytesPerChar = 4
        table.extIndexes?.let { extension ->
            // Source uses byte offset 17, not an int-table index; preserve the unaligned read.
            extension.position = 0
            var max = (extension.slice(17, 4).readInt() and 255).toByte()
            if (output == 12) max = (max + 1).toByte()
            if (max > maxBytesPerChar) maxBytesPerChar = max.toInt()
        }
    }
    private fun swapLFNL(): Boolean {
        val table = sharedData.mbcs
        val output = table.outputType.toInt()
        val results = table.fromUnicodeChars
        if ((output != 0 && output != 12) || table.stateTable[0][0x25] != final(0, 0, 0xa) || table.stateTable[0][0x15] != final(0, 0, 0x85)) return false
        fun index(c: Int): Int {
            val stage = table.fromUnicodeTable[c ushr 10].code + ((c ushr 4) and 63)
            return if (output == 0) table.fromUnicodeTable[stage].code + (c and 15) else {
                val entry = table.fromUnicodeTableInts!![stage]
                if ((entry and (1 shl (16 + (c and 15)))) == 0) return -1
                16 * (entry and 65535) + (c and 15)
            }
        }
        val lf = index(0xa)
        val nl = index(0x85)
        if (lf < 0 || nl < 0 || results!![lf].code != (if (output == 0) 0xf25 else 0x25) || results[nl].code != (if (output == 0) 0xf15 else 0x15)) return false
        if (table.fromUBytesLength <= 0) throw Exception("U_INVALID_FORMAT_ERROR")
        val states = Array(table.stateTable.size) { table.stateTable[it].copyOf() }
        states[0][0x25] = final(0, 0, 0x85)
        states[0][0x15] = final(0, 0, 0xa)
        val swapped = results.copyOf()
        swapped[lf] = (if (output == 0) 0xf15 else 0x15).toChar()
        swapped[nl] = (if (output == 0) 0xf25 else 0x25).toChar()
        if (table.swapLFNLStateTable == null) {
            table.swapLFNLStateTable = states
            table.swapLFNLFromUnicodeChars = swapped
            table.swapLFNLName = name + ",swaplfnl"
        }
        return true
    }

    companion object {
        fun load(name: String, classPath: String = ICUData.ICU_BUNDLE, loader: ICUResourceLoader? = null): MBCSConverterData {
            val option = name.indexOf(",swaplfnl")
            val canonical = if (option < 0) name else name.substring(0, option)
            return MBCSConverterData(canonical, loadTable(canonical, 1, classPath, loader), if (option < 0) 0 else 0x10)
        }
        private fun loadTable(name: String, depth: Int, classPath: String, loader: ICUResourceLoader?): UConverterSharedData {
            val static = UConverterStaticData()
            val reader = try {
                val item = "$name.cnv"
                val bytes = if (loader != null) loader.getData("$classPath/$item") ?: throw ICUUncheckedIOException("Missing converter: $name") else if (classPath != ICUData.ICU_BUNDLE) ICUData.getBytes(if (classPath.startsWith("/")) "$classPath/$item" else "com/ibm/icu/impl/$classPath/$item")?.let { BinaryBuffer(it) } ?: throw ICUUncheckedIOException("Missing converter: $name") else ICUBinary.getRequiredData(item)
                UConverterDataReader(bytes).also { it.readStaticData(static) }
            } catch (error: Exception) { throw InvalidFormatException(error) }
            if (static.conversionType != 2.toByte() || static.structSize != 100) throw InvalidFormatException()
            val result = UConverterSharedData(static)
            var table = result.mbcs
            val header = MBCSHeader()
            try { reader.readMBCSHeader(header) } catch (_: ICUUncheckedIOException) { throw InvalidFormatException() }
            var noFromU = false
            if (header.version[0] == 5.toByte() && header.version[1] >= 3 && (header.options and 0xff80) == 0) noFromU = (header.options and 0x40) != 0
            else if (header.version[0] != 4.toByte()) throw InvalidFormatException()
            table.outputType = header.flags.toByte().toShort()
            val extensionOffset = header.flags ushr 8
            if (table.outputType == 14.toShort()) {
                val base = try {
                    reader.readBaseTableName().also { if (extensionOffset != 0) table.extIndexes = reader.readExtIndexes(extensionOffset - reader.bytesReadAfterStaticData()) }
                } catch (_: ICUUncheckedIOException) { throw InvalidFormatException() }
                val extension = table.extIndexes ?: throw InvalidFormatException()
                if (depth != 1 || base == static.name) throw InvalidFormatException()
                val baseData = loadTable(base, 2, classPath, loader)
                if (baseData.staticData.conversionType != 2.toByte() || baseData.mbcs.baseSharedData != null) throw InvalidFormatException()
                table = baseData.mbcs
                result.mbcs = table
                table.baseSharedData = baseData
                table.extIndexes = extension
                table.swapLFNLStateTable = null
                table.swapLFNLFromUnicodeChars = null
                table.swapLFNLName = null
                if (static.conversionType == 1.toByte() || (static.conversionType == 2.toByte() && static.minBytesPerChar >= 2)) {
                    if (table.outputType == 12.toShort()) {
                        val entry = table.stateTable[0][14]
                        if (entry < 0 && action(entry) == 8 && state(entry) != 0) {
                            table.dbcsOnlyState = state(entry).toByte()
                            table.outputType = 0xdb
                        }
                    } else if (baseData.staticData.conversionType == 2.toByte() && baseData.staticData.minBytesPerChar == 1.toByte() && baseData.staticData.maxBytesPerChar == 2.toByte() && table.countStates <= 127) {
                        val count = table.countStates.toInt()
                        // Retain the source's oversized physical state-table shape as well as its active rows.
                        val states = Array((count + 1) * 1024) { IntArray(256) }
                        for (index in table.stateTable.indices) table.stateTable[index].copyInto(states[index])
                        for (b in 0..255) if (states[0][b] < 0) states[0][b] = count shl 24
                        states[count].fill(final(0, 7, 0))
                        table.stateTable = states
                        table.countStates = (count + 1).toByte().toShort()
                        table.stateTableOwned = true
                        table.outputType = 0xdb
                    }
                }
            } else {
                if (table.outputType.toInt() !in setOf(0, 1, 2, 3, 8, 9, 12)) throw InvalidFormatException()
                table.unicodeMask = if (reader.dataFormatHasUnicodeMask()) (static.unicodeMask.toInt() and 3).toShort() else 3
                try { reader.readMBCSTable(header, table) } catch (_: ICUUncheckedIOException) { throw InvalidFormatException() }
                if (extensionOffset != 0) try { table.extIndexes = reader.readExtIndexes(extensionOffset - reader.bytesReadAfterStaticData()) } catch (_: ICUUncheckedIOException) { throw InvalidFormatException() }
                if (header.version[1] >= 3 && (table.unicodeMask.toInt() and 2) == 0 && header.version[2].toInt().toChar().code >= (if (table.countStates == 1.toShort()) 15 else 215)) {
                    table.utf8Friendly = true
                    table.maxFastUChar = (if (table.countStates == 1.toShort()) 0xfff else (header.version[2].toInt() shl 8) or 255).toChar()
                }
                var ascii = -1
                for (i in 0..<128) if (table.stateTable[0][i] != final(0, 0, i)) ascii = ascii and (1 shl (i shr 2)).inv()
                table.asciiRoundtrips = ascii
                if (noFromU) {
                    val first = if (table.hasSupplementary()) 0x440 else 0x40
                    reconstituteData(table, first, (header.offsetFromUBytes - header.offsetFromUTable) / 4 - first / 2, header.fullStage2Length)
                }
                if (table.outputType.toInt() == 0xdb || table.outputType.toInt() == 12) table.asciiRoundtrips = 0
            }
            table.mbcsIndex = null
            return result
        }
        internal fun final(state: Int, action: Int, value: Int): Int = Int.MIN_VALUE or (state shl 24) or (action shl 20) or value
        private fun state(entry: Int): Int = (entry ushr 24) and 127
        private fun action(entry: Int): Int = (entry ushr 20) and 15

        internal fun writeStage3Roundtrip(table: UConverterMBCSTable, initial: Long, codePoints: IntArray): Boolean {
            var value = initial
            when (table.outputType.toInt()) {
                8 -> if (value > 0xffff) value = value and (if (value <= 0x8effff) 0x7fff else 0xff7f)
                9 -> if (value > 0xffffff) value = value and (if (value <= 0x8effffffL) 0x7fffff else 0xff7fff)
            }
            val ints = table.fromUnicodeTableInts!!
            for (i in 0..31) {
                val c = codePoints[i]
                if (c >= 0) {
                    val second = table.fromUnicodeTable[c shr 10].code + ((c shr 4) and 63)
                    val third = (ints[second] * 16 + (c and 15)) and 65535
                    when (table.outputType.toInt()) {
                        2, 9 -> {
                            val bytes = table.fromUnicodeBytes!!
                            bytes[third * 3] = (value shr 16).toByte(); bytes[third * 3 + 1] = (value shr 8).toByte(); bytes[third * 3 + 2] = value.toByte()
                        }
                        3 -> table.fromUnicodeInts!![third] = value.toInt()
                        else -> table.fromUnicodeChars!![third] = value.toInt().toChar()
                    }
                    ints[second] = ints[second] or (1 shl (16 + (c and 15)))
                }
                value++
            }
            return true
        }
        internal fun reconstituteData(table: UConverterMBCSTable, stage1Length: Int, stage2Length: Int, fullStage2Length: Int) {
            val first = table.fromUnicodeTable
            val firstInts = stage1Length / 2
            val second = IntArray(firstInts + fullStage2Length)
            table.fromUnicodeTableInts!!.copyInto(second, fullStage2Length - stage2Length + firstInts, firstInts, firstInts + stage2Length)
            table.fromUnicodeTableInts = second
            val fastLength = (table.maxFastUChar.code + 1) shr 6
            var fast = 0
            var s1 = 0
            while (fast < fastLength) {
                var s2 = first[s1++].code
                if (s2 != stage1Length / 2) repeat(16) {
                    var s3 = table.mbcsIndex!!.slice(fast++ * 2, 2).readUnsignedShort()
                    if (s3 != 0) { s3 = s3 shr 4; repeat(4) { second[s2++] = s3++ } } else s2 += 4
                } else fast += 16
            }
            when (table.outputType.toInt()) {
                1, 12, 8 -> table.fromUnicodeChars = CharArray(table.fromUBytesLength / 2)
                2, 9 -> table.fromUnicodeBytes = ByteArray(table.fromUBytesLength)
                3 -> table.fromUnicodeInts = IntArray(table.fromUBytesLength / 4)
            }
            val properties = ByteArray(128)
            getStateProp(table.stateTable, properties, 0)
            for (s in 0..<table.countStates) if (properties[s] >= 0x40) enumToUnicode(table, properties, s, 0, 0)
        }
        private fun enumToUnicode(table: UConverterMBCSTable, properties: ByteArray, state: Int, offset: Int, initial: Int): Boolean {
            val codePoints = IntArray(32)
            val row = table.stateTable[state]
            val units = table.unicodeCodeUnits
            val value = initial shl 8
            var any = -1
            var b = (properties[state].toInt() and 0x38) shl 2
            if (b == 0 && properties[state] >= 0x40) { codePoints[0] = -1; b = 1 }
            val limit = ((properties[state].toInt() and 7) + 1) shl 5
            while (b < limit) {
                val entry = row[b]
                if (entry >= 0) {
                    val next = entry ushr 24
                    if (properties[next] >= 0 && !enumToUnicode(table, properties, next, offset + (entry and 0xffffff), value or b)) return false
                    codePoints[b and 31] = -1
                } else {
                    val c = when (action(entry)) {
                        0 -> entry and 65535
                        4 -> units[offset + (entry and 65535)].code.let { if (it < 0xfffe) it else -1 }
                        5 -> {
                            val position = offset + (entry and 65535)
                            val first = units[position].code
                            when {
                                first < 0xd800 -> first
                                first <= 0xdbff -> ((first and 1023) shl 10) + units[position + 1].code + 0x10000 - 0xdc00
                                first == 0xe000 -> units[position + 1].code
                                else -> -1
                            }
                        }
                        1 -> (entry and 0xfffff) + 0x10000
                        else -> -1
                    }
                    codePoints[b and 31] = c
                    any = any and c
                }
                b++
                if ((b and 31) == 0 && any >= 0) {
                    if (!writeStage3Roundtrip(table, (value or (b - 32)).toLong(), codePoints)) return false
                    any = -1
                }
            }
            return true
        }
        private fun getStateProp(states: Array<IntArray>, properties: ByteArray, current: Int): Byte {
            val row = states[current]
            properties[current] = 0
            var min = 0
            while (true) {
                val entry = row[min]
                val next = state(entry)
                if (properties[next] == (-1).toByte()) getStateProp(states, properties, next)
                if (if (entry >= 0) properties[next] > 0 else action(entry) < 6) break
                if (min == 255) { properties[current] = -0x40; return properties[current] }
                min++
            }
            properties[current] = (properties[current].toInt() or ((min shr 5) shl 3)).toByte()
            var max = 255
            while (min < max) {
                val entry = row[max]
                val next = state(entry)
                if (properties[next] == (-1).toByte()) getStateProp(states, properties, next)
                if (if (entry >= 0) properties[next] > 0 else action(entry) < 6) break
                max--
            }
            properties[current] = (properties[current].toInt() or (max shr 5)).toByte()
            while (min <= max) {
                val entry = row[min++]
                val next = state(entry)
                if (properties[next] == (-1).toByte()) getStateProp(states, properties, next)
                if (entry >= 0) {
                    properties[next] = (properties[next].toInt() or 0x40).toByte()
                    if (action(entry) <= 3) properties[current] = (properties[current].toInt() or 0x40).toByte()
                }
            }
            return properties[current]
        }
    }
}
