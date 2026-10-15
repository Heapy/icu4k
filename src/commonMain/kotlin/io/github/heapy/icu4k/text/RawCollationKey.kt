// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ByteArrayWrapper
import io.github.heapy.icu4k.util.byteArrayWrapperNegativeSizeException

/** Reusable, mutable storage for a null-terminated collation sort key. */
class RawCollationKey : ByteArrayWrapper {
    constructor() : super()
    constructor(capacity: Int) : super() {
        if(capacity<0) throw byteArrayWrapperNegativeSizeException()
        bytes=ByteArray(capacity)
    }
    constructor(bytes: ByteArray?) : super() { this.bytes=bytes }
    constructor(bytesToAdopt: ByteArray?,size: Int) : super(bytesToAdopt,size)
    // This is an overload: comparison through ByteArrayWrapper retains its full difference.
    fun compareTo(rhs: RawCollationKey): Int {
        val result=super.compareTo(rhs)
        return if(result<0) -1 else if(result==0) 0 else 1
    }
}
