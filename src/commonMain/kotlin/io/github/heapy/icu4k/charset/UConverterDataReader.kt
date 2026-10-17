// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.InvalidFormatException
import io.github.heapy.icu4k.util.BinaryBuffer

/** Common reader for ICU's cnvt data format, including encoder tables and extension views. */
internal class UConverterDataReader(private val bytes: BinaryBuffer) {
    private var positionAfterStaticData = 0
    init {
        ICUBinary.readHeader(bytes, 0x636e7674, object : ICUBinary.Authenticate {
            override fun isDataVersionAcceptable(version: ByteArray): Boolean = version[0] == 6.toByte()
        })
    }
    fun readStaticData(data: UConverterStaticData) {
        data.structSize = bytes.readInt()
        // Java's US-ASCII decoder replaces high bytes and retains the NUL padding.
        data.name = bytes.readBytes(60).map { if (it >= 0) it.toInt().toChar() else '\ufffd' }.joinToString("")
        data.codepage = bytes.readInt()
        data.platform = bytes.readByte()
        data.conversionType = bytes.readByte()
        data.minBytesPerChar = bytes.readByte()
        data.maxBytesPerChar = bytes.readByte()
        bytes.readBytes(4).copyInto(data.subChar)
        data.subCharLen = bytes.readByte()
        data.hasToUnicodeFallback = bytes.readByte()
        data.hasFromUnicodeFallback = bytes.readByte()
        data.unicodeMask = bytes.readUnsignedByte().toShort()
        data.subChar1 = bytes.readByte()
        bytes.readBytes(19).copyInto(data.reserved)
        positionAfterStaticData = bytes.position
    }
    fun bytesReadAfterStaticData(): Int = bytes.position - positionAfterStaticData
    fun readMBCSHeader(header: MBCSHeader) {
        bytes.readBytes(4).copyInto(header.version)
        header.countStates = bytes.readInt()
        header.countToUFallbacks = bytes.readInt()
        header.offsetToUCodeUnits = bytes.readInt()
        header.offsetFromUTable = bytes.readInt()
        header.offsetFromUBytes = bytes.readInt()
        header.flags = bytes.readInt()
        header.fromUBytesLength = bytes.readInt()
        if (header.version[0] == 5.toByte() && header.version[1] >= 3) {
            header.options = bytes.readInt()
            if ((header.options and 0x40) != 0) header.fullStage2Length = bytes.readInt()
        }
    }
    fun readMBCSTable(header: MBCSHeader, table: UConverterMBCSTable) {
        table.countStates = header.countStates.toByte().toShort()
        val integers = bytes.slice()
        table.stateTable = Array(header.countStates) { integers.readInts(256) }
        table.countToUFallbacks = header.countToUFallbacks
        table.toUFallbacks = Array(header.countToUFallbacks) { MBCSToUFallback(integers.readInt(), integers.readInt()) }
        ICUBinary.skipBytes(bytes, integers.position)
        var length = header.offsetFromUTable - header.offsetToUCodeUnits
        table.unicodeCodeUnits = bytes.slice().readChars(length / 2)
        ICUBinary.skipBytes(bytes, length)
        length = header.offsetFromUBytes - header.offsetFromUTable
        val charCount = if (table.outputType == 0.toShort()) length / 2 else if (table.hasSupplementary()) 0x440 else 0x40
        table.fromUnicodeTable = bytes.slice().readChars(charCount)
        if (table.outputType != 0.toShort()) table.fromUnicodeTableInts = bytes.slice().readInts(length / 4)
        ICUBinary.skipBytes(bytes, length)
        table.fromUBytesLength = header.fromUBytesLength
        if ((header.options and 0x40) == 0) {
            when (table.outputType.toInt()) {
                0, 1, 12, 8 -> table.fromUnicodeChars = bytes.readChars(header.fromUBytesLength / 2)
                2, 9 -> table.fromUnicodeBytes = bytes.readBytes(header.fromUBytesLength)
                3 -> table.fromUnicodeInts = bytes.readInts(header.fromUBytesLength / 4)
            }
        } else {
            table.mbcsIndex = bytes.slice(bytes.position, bytes.remaining / 2 * 2)
        }
    }
    fun readBaseTableName(): String = buildString {
        while (true) {
            val c = bytes.readByte().toInt().toChar()
            if (c == '\u0000') break
            append(c)
        }
    }
    fun readExtIndexes(skip: Int): BinaryBuffer {
        ICUBinary.skipBytes(bytes, skip)
        val extension = bytes.slice()
        if (extension.slice(0, 4).readInt() < 32) throw InvalidFormatException()
        val size = extension.slice(31 * 4, 4).readInt()
        extension.limit = size
        ICUBinary.skipBytes(bytes, size)
        return extension
    }
    fun dataFormatHasUnicodeMask(): Boolean {
        val major = bytes.getByte(16).toInt() and 255
        return major > 6 || (major == 6 && bytes.getByte(17) != 0.toByte())
    }
}
