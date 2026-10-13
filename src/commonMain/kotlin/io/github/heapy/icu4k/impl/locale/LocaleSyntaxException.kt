// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

class LocaleSyntaxException(message: String, private val errorIndex: Int = 0) : Exception(message) {
    fun getErrorIndex(): Int = errorIndex
}
