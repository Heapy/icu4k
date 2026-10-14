// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2014-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.SimpleFilteredSentenceBreakIterator
import io.github.heapy.icu4k.util.ULocale

/** Builds a wrapper suppressing existing segment boundaries after specified strings. */
abstract class FilteredBreakIteratorBuilder protected constructor() {
    abstract fun suppressBreakAfter(str: CharSequence): Boolean
    abstract fun unsuppressBreakAfter(str: CharSequence): Boolean
    /** Adopts [wrappedBreakIterator]; the builder can subsequently be reused. */
    abstract fun wrapIteratorWithFilter(wrappedBreakIterator: BreakIterator): BreakIterator
    companion object {
        fun getInstance(locale: ULocale?): FilteredBreakIteratorBuilder = SimpleFilteredSentenceBreakIterator.Builder(locale)
        fun getEmptyInstance(): FilteredBreakIteratorBuilder = SimpleFilteredSentenceBreakIterator.Builder()
    }
}
