// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

class ParseStatus {
    internal var _parseLength = 0
    internal var _errorIndex = -1
    internal var _errorMsg: String? = null
    fun reset() { _parseLength = 0; _errorIndex = -1; _errorMsg = null }
    fun isError(): Boolean = _errorIndex >= 0
    fun getErrorIndex(): Int = _errorIndex
    fun getParseLength(): Int = _parseLength
    fun getErrorMessage(): String? = _errorMsg
}
