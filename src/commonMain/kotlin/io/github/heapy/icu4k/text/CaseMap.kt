// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CaseMapImpl

/** Immutable options for Unicode case mapping. Locale-sensitive adapters are still being ported. */
sealed class CaseMap private constructor(protected val internalOptions: Int) {
    abstract fun omitUnchangedText(): CaseMap

    /** Locale-independent folding, optionally using the Turkic dotted-I mappings. */
    class Fold private constructor(options: Int) : CaseMap(options) {
        override fun omitUnchangedText(): Fold = if (internalOptions and 1 == 0) OMIT_UNCHANGED else TURKIC_OMIT_UNCHANGED
        fun turkic(): Fold = if (internalOptions and CaseMapImpl.OMIT_UNCHANGED_TEXT == 0) TURKIC else TURKIC_OMIT_UNCHANGED
        fun apply(src: CharSequence): String = CaseMapImpl.fold(internalOptions, src)
        fun <A : Appendable> apply(src: CharSequence, dest: A, edits: Edits?): A = CaseMapImpl.fold(internalOptions, src, dest, edits)

        internal companion object {
            val DEFAULT = Fold(0)
            private val TURKIC = Fold(1)
            private val OMIT_UNCHANGED = Fold(CaseMapImpl.OMIT_UNCHANGED_TEXT)
            private val TURKIC_OMIT_UNCHANGED = Fold(1 or CaseMapImpl.OMIT_UNCHANGED_TEXT)
        }
    }
    companion object {
        fun fold(): Fold = Fold.DEFAULT
    }
}
