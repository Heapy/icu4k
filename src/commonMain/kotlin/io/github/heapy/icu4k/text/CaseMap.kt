// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CaseMapImpl
import io.github.heapy.icu4k.impl.UCaseProps
import io.github.heapy.icu4k.util.ULocale

/** Immutable options for Unicode case mapping. Titlecasing adapters are still being ported. */
sealed class CaseMap private constructor(protected val internalOptions: Int) {
    abstract fun omitUnchangedText(): CaseMap

    class Lower private constructor(options: Int) : CaseMap(options) {
        override fun omitUnchangedText(): Lower = OMIT_UNCHANGED
        fun apply(locale: ULocale?, src: CharSequence?): String = CaseMapImpl.toLower(caseLocale(locale), internalOptions, src!!)
        fun <A : Appendable?> apply(locale: ULocale?, src: CharSequence?, dest: A, edits: Edits?): A {
            val case = caseLocale(locale)
            return mapToDestination(src, dest, edits) { text, output -> CaseMapImpl.toLower(case, internalOptions, text, output, edits) }
        }
        internal companion object {
            val DEFAULT = Lower(0)
            private val OMIT_UNCHANGED = Lower(CaseMapImpl.OMIT_UNCHANGED_TEXT)
        }
    }
    class Upper private constructor(options: Int) : CaseMap(options) {
        override fun omitUnchangedText(): Upper = OMIT_UNCHANGED
        fun apply(locale: ULocale?, src: CharSequence?): String = CaseMapImpl.toUpper(caseLocale(locale), internalOptions, src!!)
        fun <A : Appendable?> apply(locale: ULocale?, src: CharSequence?, dest: A, edits: Edits?): A {
            val case = caseLocale(locale)
            return mapToDestination(src, dest, edits) { text, output -> CaseMapImpl.toUpper(case, internalOptions, text, output, edits) }
        }
        internal companion object {
            val DEFAULT = Upper(0)
            private val OMIT_UNCHANGED = Upper(CaseMapImpl.OMIT_UNCHANGED_TEXT)
        }
    }
    /** Locale-independent folding, optionally using the Turkic dotted-I mappings. */
    class Fold private constructor(options: Int) : CaseMap(options) {
        override fun omitUnchangedText(): Fold = if (internalOptions and 1 == 0) OMIT_UNCHANGED else TURKIC_OMIT_UNCHANGED
        fun turkic(): Fold = if (internalOptions and CaseMapImpl.OMIT_UNCHANGED_TEXT == 0) TURKIC else TURKIC_OMIT_UNCHANGED
        fun apply(src: CharSequence?): String = CaseMapImpl.fold(internalOptions, src!!)
        fun <A : Appendable?> apply(src: CharSequence?, dest: A, edits: Edits?): A = mapToDestination(src, dest, edits) { text, output ->
            CaseMapImpl.fold(internalOptions, text, output, edits)
        }

        internal companion object {
            val DEFAULT = Fold(0)
            private val TURKIC = Fold(1)
            private val OMIT_UNCHANGED = Fold(CaseMapImpl.OMIT_UNCHANGED_TEXT)
            private val TURKIC_OMIT_UNCHANGED = Fold(1 or CaseMapImpl.OMIT_UNCHANGED_TEXT)
        }
    }
    companion object {
        private fun caseLocale(locale: ULocale?): Int = UCaseProps.getCaseLocale((locale ?: ULocale.getDefault()).getLanguage())
        fun toLower(): Lower = Lower.DEFAULT
        fun toUpper(): Upper = Upper.DEFAULT
        fun fold(): Fold = Fold.DEFAULT
    }
}

internal expect fun rethrowCaseMapException(failure: Exception): Nothing

private object MissingCaseDestination : Appendable {
    override fun append(value: Char): Appendable = throw NullPointerException()
    override fun append(value: CharSequence?): Appendable = throw NullPointerException()
    override fun append(value: CharSequence?, start: Int, end: Int): Appendable = throw NullPointerException()
}
private fun <A : Appendable?> mapToDestination(src: CharSequence?, dest: A, edits: Edits?, operation: (CharSequence, Appendable) -> Unit): A {
    try {
        if (src == null) { edits?.reset(); throw NullPointerException() }
        operation(src, dest ?: MissingCaseDestination)
        return dest
    } catch (failure: Exception) { rethrowCaseMapException(failure) }
}
