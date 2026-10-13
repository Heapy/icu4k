// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Forward UTF-16 iteration with post-increment semantics. */
interface UForwardCharacterIterator {
    fun next(): Int
    fun nextCodePoint(): Int

    companion object {
        const val DONE = -1
    }
}
