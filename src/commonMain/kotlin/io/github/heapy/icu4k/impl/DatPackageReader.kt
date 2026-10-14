// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** Original CmnD table reader. Only validation changes the package buffer's state. */
internal object DatPackageReader {
    private const val DATA_FORMAT = 0x436d6e44
    private val acceptable = ICUBinary.Authenticate { it[0].toInt() == 1 }

    fun validate(bytes: BinaryBuffer): Boolean {
        try { readICUBinaryHeader(bytes, DATA_FORMAT, acceptable) }
        catch (_: ICUUncheckedIOException) { return false }
        val count = intAt(bytes, bytes.position)
        if (count <= 0 || bytes.position + 4 + count * (8 + 16) > bytes.capacity) return false
        return startsWithPackageName(bytes, getNameOffset(bytes, 0)) &&
            startsWithPackageName(bytes, getNameOffset(bytes, count - 1))
    }
    private fun startsWithPackageName(bytes: BinaryBuffer, start: Int): Boolean {
        var length = ICUData.PACKAGE_NAME.length - 1
        for (i in 0 until length) if (bytes.getByte(start + i).toInt() != ICUData.PACKAGE_NAME[i].code) return false
        val c = bytes.getByte(start + length++).toInt()
        return (c == 'b'.code || c == 'l'.code) && bytes.getByte(start + length).toInt() == '/'.code
    }
    fun getData(bytes: BinaryBuffer, key: CharSequence): BinaryBuffer? {
        val index = binarySearch(bytes, key)
        if (index < 0) return null
        val data = bytes.duplicate()
        data.position = getDataOffset(bytes, index)
        data.limit = getDataOffset(bytes, index + 1)
        return ICUBinary.sliceWithOrder(data)
    }
    fun addBaseNamesInFolder(bytes: BinaryBuffer, folder: String, suffix: String, names: MutableSet<String>) {
        var index = binarySearch(bytes, folder)
        if (index < 0) index = index.inv()
        val count = intAt(bytes, bytes.position)
        val builder = StringBuilder()
        while (index < count && addBaseName(bytes, index, folder, suffix, builder, names)) ++index
    }
    private fun binarySearch(bytes: BinaryBuffer, key: CharSequence): Int {
        var start = 0
        var limit = intAt(bytes, bytes.position)
        while (start < limit) {
            val mid = (start + limit) ushr 1
            val offset = getNameOffset(bytes, mid) + ICUData.PACKAGE_NAME.length + 1
            val result = ICUBinary.compareKeys(key, bytes, offset)
            when {
                result < 0 -> limit = mid
                result > 0 -> start = mid + 1
                else -> return mid
            }
        }
        return start.inv()
    }
    private fun getNameOffset(bytes: BinaryBuffer, index: Int): Int =
        bytes.position + intAt(bytes, bytes.position + 4 + index * 8)
    private fun getDataOffset(bytes: BinaryBuffer, index: Int): Int =
        if (index == intAt(bytes, bytes.position)) bytes.capacity
        else bytes.position + intAt(bytes, bytes.position + 8 + index * 8)

    internal fun addBaseName(bytes: BinaryBuffer, index: Int, folder: String, suffix: String,
                             builder: StringBuilder, names: MutableSet<String>): Boolean {
        var offset = getNameOffset(bytes, index) + ICUData.PACKAGE_NAME.length + 1
        if (folder.isNotEmpty()) {
            for (c in folder) if (bytes.getByte(offset++).toInt() != c.code) return false
            if (bytes.getByte(offset++).toInt() != '/'.code) return false
        }
        builder.setLength(0)
        while (true) {
            val byte = bytes.getByte(offset++).toInt()
            if (byte == 0) break
            val c = byte.toChar()
            if (c == '/') return true
            builder.append(c)
        }
        val nameLimit = builder.length - suffix.length
        // Upstream searches at/before this offset; it does not require an actual ending suffix.
        if (builder.toString().lastIndexOf(suffix, nameLimit) >= 0) names.add(builder.substring(0, nameLimit))
        return true
    }
    private fun intAt(bytes: BinaryBuffer, index: Int): Int {
        val a = bytes.getByte(index).toInt() and 255
        val b = bytes.getByte(index + 1).toInt() and 255
        val c = bytes.getByte(index + 2).toInt() and 255
        val d = bytes.getByte(index + 3).toInt() and 255
        return if (bytes.order == BinaryBuffer.ByteOrder.BIG_ENDIAN) (a shl 24) or (b shl 16) or (c shl 8) or d
        else (d shl 24) or (c shl 16) or (b shl 8) or a
    }
}
