// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2012, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

class IllformedLocaleException private constructor(message: String?, private val errorIndex: Int, indexed: Boolean) :
    RuntimeException(if (indexed) "$message${if (errorIndex < 0) "" else " [at index $errorIndex]"}" else message) {
    constructor() : this(null, -1, false)
    constructor(message: String?) : this(message, -1, false)
    constructor(message: String?, errorIndex: Int) : this(message, errorIndex, true)
    fun getErrorIndex(): Int = errorIndex
}
