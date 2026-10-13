// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009-2010, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

open class Extension protected constructor(private val key: Char) {
    protected var _value: String? = null
    internal constructor(key: Char, value: String?) : this(key) { _value = value }
    fun getKey(): Char = key
    fun getValue(): String? = _value
    fun getID(): String = "$key${LanguageTag.SEP}$_value"
    override fun toString(): String = getID()
}
