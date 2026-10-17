// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.util.BinaryBuffer

internal class UConverterStaticData {
    var structSize = 0
    var name = ""
    var codepage = 0
    var platform: Byte = 0
    var conversionType: Byte = 0
    var minBytesPerChar: Byte = 0
    var maxBytesPerChar: Byte = 0
    val subChar = ByteArray(4)
    var subCharLen: Byte = 0
    var hasToUnicodeFallback: Byte = 0
    var hasFromUnicodeFallback: Byte = 0
    var unicodeMask: Short = 0
    var subChar1: Byte = 0
    val reserved = ByteArray(19)
    companion object { const val SIZE_OF_UCONVERTER_STATIC_DATA = 100 }
}

internal class MBCSHeader {
    val version = ByteArray(4)
    var countStates = 0
    var countToUFallbacks = 0
    var offsetToUCodeUnits = 0
    var offsetFromUTable = 0
    var offsetFromUBytes = 0
    var flags = 0
    var fromUBytesLength = 0
    var options = 0
    var fullStage2Length = 0
}

internal class MBCSToUFallback(val offset: Int, val codePoint: Int)

/** Serialized conversion tables; runtime conversion state belongs to a decoder/encoder. */
internal class UConverterMBCSTable {
    var countStates: Short = 0
    var countToUFallbacks = 0
    var stateTable = emptyArray<IntArray>()
    var toUFallbacks = emptyArray<MBCSToUFallback>()
    var unicodeCodeUnits = charArrayOf()
    var fromUnicodeTable = charArrayOf()
    var fromUnicodeTableInts: IntArray? = null
    var fromUnicodeBytes: ByteArray? = null
    var fromUnicodeChars: CharArray? = null
    var fromUnicodeInts: IntArray? = null
    var fromUBytesLength = 0
    var outputType: Short = 0
    var unicodeMask: Short = 0
    /** UTF-16 view of optional UTF-8-friendly data; shares storage and does not move the reader. */
    var mbcsIndex: BinaryBuffer? = null
    fun hasSupplementary(): Boolean = (unicodeMask.toInt() and 1) != 0
}
