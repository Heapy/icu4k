// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import io.github.heapy.icu4k.util.VersionInfo

/** Portable ICU data-header authentication and binary array operations. */
object ICUBinary {
    fun getData(itemPath: String): BinaryBuffer? = ICUData.getBytes(itemPath)?.let { BinaryBuffer(it) }
    fun getRequiredData(itemPath: String): BinaryBuffer = getData(itemPath)
        ?: throw ICUUncheckedIOException("Missing ICU data resource: $itemPath")

    fun interface Authenticate { fun isDataVersionAcceptable(version: ByteArray): Boolean }

    fun readHeaderAndDataVersion(bytes: BinaryBuffer, dataFormat: Int, authenticate: Authenticate?): VersionInfo =
        getVersionInfoFromCompactInt(readHeader(bytes, dataFormat, authenticate))

    /** Sets byte order from UDataInfo and advances past the complete ICU header. */
    fun readHeader(bytes: BinaryBuffer, dataFormat: Int, authenticate: Authenticate?): Int {
        fun byte(index: Int): Int = bytes.getByte(index).toInt() and 255
        val magic1 = byte(2); val magic2 = byte(3)
        if (magic1 != 0xda || magic2 != 0x27) throw ICUUncheckedIOException("ICU data file error: Not an ICU data file")
        val authenticationError = "ICU data file error: Header authentication failed, please check if you have a valid ICU data file"
        val bigEndian = byte(8)
        val charsetFamily = byte(9); val ucharSize = byte(10)
        if (bigEndian !in 0..1 || charsetFamily != 0 || ucharSize != 2) throw ICUUncheckedIOException(authenticationError)
        bytes.order = if (bigEndian == 1) BinaryBuffer.ByteOrder.BIG_ENDIAN else BinaryBuffer.ByteOrder.LITTLE_ENDIAN
        fun shortAt(index: Int): Int = if (bigEndian == 1) (byte(index) shl 8) or byte(index + 1)
            else (byte(index + 1) shl 8) or byte(index)
        val headerSize = shortAt(0)
        val infoSize = shortAt(4)
        if (infoSize < 20 || headerSize < infoSize + 4) throw ICUUncheckedIOException("Internal Error: Header size error")
        val formatVersion = ByteArray(4) { bytes.getByte(16 + it) }
        val actualFormat = (byte(12) shl 24) or (byte(13) shl 16) or (byte(14) shl 8) or byte(15)
        if (actualFormat != dataFormat || (authenticate != null && !authenticate.isDataVersionAcceptable(formatVersion))) {
            val format = (12..15).joinToString("") { byte(it).toString(16).padStart(2, '0') }
            val version = formatVersion.joinToString(".") { (it.toInt() and 255).toString() }
            throw ICUUncheckedIOException("$authenticationError; data format $format, format version $version")
        }
        if (headerSize > bytes.limit) throw IllegalArgumentException("newPosition > limit: ($headerSize > ${bytes.limit})")
        bytes.position = headerSize
        return (byte(20) shl 24) or (byte(21) shl 16) or (byte(22) shl 8) or byte(23)
    }

    /** Common equivalent of writing an ICU header to a DataOutputStream. */
    fun writeHeader(dataFormat: Int, formatVersion: Int, dataVersion: Int): ByteArray {
        val bytes = BinaryBuffer(ByteArray(32))
        bytes.writeShort(32)
        bytes.writeByte(0xda)
        bytes.writeByte(0x27)
        bytes.writeShort(20)
        bytes.writeShort(0)
        bytes.writeByte(1)
        bytes.writeByte(0)
        bytes.writeByte(2)
        bytes.writeByte(0)
        bytes.writeInt(dataFormat)
        bytes.writeInt(formatVersion)
        bytes.writeInt(dataVersion)
        bytes.writeInt(0)
        bytes.writeInt(0)
        return bytes.bytes
    }

    fun skipBytes(bytes: BinaryBuffer, skipLength: Int) { if (skipLength > 0) bytes.skip(skipLength) }
    fun getBytes(bytes: BinaryBuffer, length: Int, additionalSkipLength: Int): ByteArray =
        bytes.readBytes(length).also { skipBytes(bytes, additionalSkipLength) }
    fun getChars(bytes: BinaryBuffer, length: Int, additionalSkipLength: Int): CharArray =
        bytes.readChars(length).also { skipBytes(bytes, additionalSkipLength) }
    fun getString(bytes: BinaryBuffer, length: Int, additionalSkipLength: Int): String =
        getChars(bytes, length, additionalSkipLength).concatToString()
    fun getShorts(bytes: BinaryBuffer, length: Int, additionalSkipLength: Int): ShortArray =
        getChars(bytes, length, additionalSkipLength).let { chars -> ShortArray(length) { chars[it].code.toShort() } }
    fun getInts(bytes: BinaryBuffer, length: Int, additionalSkipLength: Int): IntArray =
        bytes.readInts(length).also { skipBytes(bytes, additionalSkipLength) }
    fun getLongs(bytes: BinaryBuffer, length: Int, additionalSkipLength: Int): LongArray {
        if (length < 0 || length > bytes.remaining / 8) throw ICUUncheckedIOException("Buffer too short for $length longs")
        return LongArray(length) { bytes.readLong() }.also { skipBytes(bytes, additionalSkipLength) }
    }
    fun sliceWithOrder(bytes: BinaryBuffer): BinaryBuffer = bytes.slice()
    fun getVersionInfoFromCompactInt(version: Int): VersionInfo =
        VersionInfo.getInstance(version ushr 24, (version shr 16) and 255, (version shr 8) and 255, version and 255)
    fun getVersionByteArrayFromCompactInt(version: Int): ByteArray =
        byteArrayOf((version shr 24).toByte(), (version shr 16).toByte(), (version shr 8).toByte(), version.toByte())
    /** Compares a length-specified sequence with an ASCII NUL-terminated resource key. */
    internal fun compareKeys(key: CharSequence, bytes: ByteArray, offset: Int): Int {
        var index = 0
        var position = offset
        while (true) {
            val c = bytes[position].toInt()
            if (c == 0) return if (index == key.length) 0 else 1
            if (index == key.length) return -1
            val difference = key[index].code - c
            if (difference != 0) return difference
            ++index; ++position
        }
    }
    internal fun compareKeys(key: CharSequence, bytes: BinaryBuffer, offset: Int): Int {
        var index = 0
        var position = offset
        while (true) {
            val c = bytes.getByte(position).toInt()
            if (c == 0) return if (index == key.length) 0 else 1
            if (index == key.length) return -1
            val difference = key[index].code - c
            if (difference != 0) return difference
            ++index; ++position
        }
    }

}
