// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2000-2009, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacterDirection
import kotlin.jvm.JvmField

/** Callback for customizing bidi classes. The default sentinel requests Unicode property lookup. */
open class BidiClassifier(@JvmField protected var context: Any?) {
    open fun setContext(context: Any?) { this.context = context }
    open fun getContext(): Any? = context
    open fun classify(c: Int): Int = UCharacterDirection.CHAR_DIRECTION_COUNT
}
