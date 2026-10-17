// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Enumerates maximal half-open integer ranges with a common value. */
interface RangeValueIterator {
    open class Element {
        @kotlin.jvm.JvmField var start=0
        @kotlin.jvm.JvmField var limit=0
        @kotlin.jvm.JvmField var value=0
    }
    /** When false, leaves the result untouched. */
    fun next(element: Element): Boolean
    fun reset()
}
