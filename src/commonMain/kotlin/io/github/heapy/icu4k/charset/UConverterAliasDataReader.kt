// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.charset

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.util.BinaryBuffer

internal class UConverterAliasDataReader(private val bytes: BinaryBuffer) : ICUBinary.Authenticate {
    init { ICUBinary.readHeader(bytes, 0x4376416c, this) }
    fun readToc(count: Int): IntArray = ICUBinary.getInts(bytes, count, 0)
    override fun isDataVersionAcceptable(version: ByteArray): Boolean =
        version.size >= 3 && version[0] == 3.toByte() && version[1] == 0.toByte() && version[2] == 1.toByte()
}
