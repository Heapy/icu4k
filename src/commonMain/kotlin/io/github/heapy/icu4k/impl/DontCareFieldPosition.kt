// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.FormatFieldPosition

internal class DontCareFieldPosition private constructor() : FormatFieldPosition(-913028704) {
    override fun setBeginIndex(index: Int) {}
    override fun setEndIndex(index: Int) {}
    companion object { val INSTANCE = DontCareFieldPosition() }
}
