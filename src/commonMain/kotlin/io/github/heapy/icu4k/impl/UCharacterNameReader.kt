// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** Reads ICU token, group and algorithmic-name tables from unames.icu. */
internal class UCharacterNameReader(private val bytes: BinaryBuffer) : ICUBinary.Authenticate {
    init { ICUBinary.readHeader(bytes, DATA_FORMAT, this) }
    override fun isDataVersionAcceptable(version: ByteArray): Boolean = version[0] == 1.toByte()
    fun authenticate(format: ByteArray, version: ByteArray): Boolean =
        format.contentEquals(ICUBinary.getVersionByteArrayFromCompactInt(DATA_FORMAT)) && isDataVersionAcceptable(version)
    fun read(data: UCharacterName) {
        val tokenStringIndex = bytes.readInt()
        val groupIndex = bytes.readInt()
        val groupStringIndex = bytes.readInt()
        val algorithmIndex = bytes.readInt()
        var count = bytes.readUnsignedShort()
        val tokens = bytes.readChars(count)
        data.setToken(tokens, bytes.readBytes(groupIndex - tokenStringIndex))
        count = bytes.readUnsignedShort()
        data.setGroupCountSize(count, 3)
        val group = bytes.readChars(count * 3)
        data.setGroup(group, bytes.readBytes(algorithmIndex - groupStringIndex))
        count = bytes.readInt()
        data.setAlgorithm(Array(count) {
            readAlgorithm() ?: throw ICUUncheckedIOException("unames.icu read error: Algorithmic names creation error")
        })
    }
    private fun readAlgorithm(): UCharacterName.AlgorithmName? {
        val result = UCharacterName.AlgorithmName()
        val start = bytes.readInt()
        val end = bytes.readInt()
        val type = bytes.readByte()
        val variant = bytes.readByte()
        if (!result.setInfo(start, end, type, variant)) return null
        var size = bytes.readUnsignedShort()
        if (type == UCharacterName.AlgorithmName.TYPE_1_.toByte()) {
            result.setFactor(bytes.readChars(variant.toInt()))
            size -= variant.toInt() shl 1
        }
        val prefix = StringBuilder()
        var c = bytes.readUnsignedByte()
        while (c != 0) { prefix.append(c.toChar()); c = bytes.readUnsignedByte() }
        result.setPrefix(prefix.toString())
        size -= 12 + prefix.length + 1
        if (size > 0) result.setFactorString(bytes.readBytes(size))
        return result
    }
    companion object { private const val DATA_FORMAT = 0x756e616d }
}
