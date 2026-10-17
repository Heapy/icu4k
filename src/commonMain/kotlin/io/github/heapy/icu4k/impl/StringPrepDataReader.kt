// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

class StringPrepDataReader(private val bytes: BinaryBuffer) : ICUBinary.Authenticate {
    private val unicodeVersion=ICUBinary.readHeader(bytes,0x53505250,this)
    fun read(length: Int): CharArray = ICUBinary.getChars(bytes,length,0)
    override fun isDataVersionAcceptable(version: ByteArray): Boolean = version[0]==3.toByte() && version[2]==5.toByte() && version[3]==2.toByte()
    fun readIndexes(length: Int): IntArray = IntArray(length){bytes.readInt()}
    fun getUnicodeVersion(): ByteArray = ICUBinary.getVersionByteArrayFromCompactInt(unicodeVersion)
}
