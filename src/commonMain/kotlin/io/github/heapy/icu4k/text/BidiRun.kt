// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.jvm.JvmField

/** A logical range with a resolved bidi embedding level. */
open class BidiRun internal constructor(
    @JvmField internal var start: Int = 0,
    @JvmField internal var limit: Int = 0,
    @JvmField internal var level: Byte = 0,
) {
    @JvmField internal var insertRemove: Int = 0
    internal open fun copyFrom(run: BidiRun) {
        start = run.start; limit = run.limit; level = run.level; insertRemove = run.insertRemove
    }
    open fun getStart(): Int = start
    open fun getLimit(): Int = limit
    open fun getLength(): Int = limit - start
    open fun getEmbeddingLevel(): Byte = level
    open fun isOddRun(): Boolean = (level.toInt() and 1) == 1
    open fun isEvenRun(): Boolean = (level.toInt() and 1) == 0
    open fun getDirection(): Byte = (level.toInt() and 1).toByte()
    override fun toString(): String = "BidiRun $start - $limit @ $level"
}
