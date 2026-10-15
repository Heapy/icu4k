// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CaseMapImpl
import io.github.heapy.icu4k.impl.UCaseProps
import io.github.heapy.icu4k.util.ULocale

/** Immutable options for locale-sensitive Unicode case mapping. */
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
    class Title private constructor(options: Int) : CaseMap(options) {
        fun wholeString(): Title = Title(CaseMapImpl.addTitleIteratorOption(internalOptions, CaseMapImpl.TITLECASE_WHOLE_STRING))
        fun sentences(): Title = Title(CaseMapImpl.addTitleIteratorOption(internalOptions, CaseMapImpl.TITLECASE_SENTENCES))
        override fun omitUnchangedText(): Title = if (internalOptions == 0 || internalOptions == CaseMapImpl.OMIT_UNCHANGED_TEXT) OMIT_UNCHANGED else Title(internalOptions or CaseMapImpl.OMIT_UNCHANGED_TEXT)
        fun noLowercase(): Title = Title(internalOptions or 0x100)
        fun noBreakAdjustment(): Title = Title(CaseMapImpl.addTitleAdjustmentOption(internalOptions, 0x200))
        fun adjustToCased(): Title = Title(CaseMapImpl.addTitleAdjustmentOption(internalOptions, CaseMapImpl.TITLECASE_ADJUST_TO_CASED))
        private fun selectIterator(locale: ULocale?, iterator: BreakIterator?): Pair<ULocale?, BreakIterator> {
            val where = if (iterator == null && locale == null) ULocale.getDefault() else locale
            val mode = internalOptions and 0xe0
            if (mode != 0 && iterator != null) throw IllegalArgumentException("titlecasing iterator option together with an explicit iterator")
            val selected = iterator ?: when (mode) {
                0 -> BreakIterator.getWordInstance(where)
                CaseMapImpl.TITLECASE_WHOLE_STRING -> WholeStringTitleIterator()
                CaseMapImpl.TITLECASE_SENTENCES -> BreakIterator.getSentenceInstance(where)
                else -> throw IllegalArgumentException("unknown titlecasing iterator option")
            }
            return where to selected
        }
        private fun boundaries(selected: BreakIterator): CaseMapImpl.TitleIterator = object : CaseMapImpl.TitleIterator {
            override fun first(): Int = selected.first()
            override fun next(): Int = selected.next()
        }
        private fun prepare(locale: ULocale?, iterator: BreakIterator?, src: CharSequence?): Pair<Int, CaseMapImpl.TitleIterator> {
            val (where, selected) = selectIterator(locale, iterator)
            selected.setText(src)
            return caseLocale(where) to boundaries(selected)
        }
        /** UCharacter passes nullable String directly to the virtual String overload. */
        internal fun applyToString(locale: ULocale?, iterator: BreakIterator?, src: String?): String {
            val (where, selected) = selectIterator(locale, iterator)
            selected.setText(src)
            val case = caseLocale(where)
            return CaseMapImpl.toTitle(case, internalOptions, boundaries(selected), src!!)
        }
        fun apply(locale: ULocale?, iterator: BreakIterator?, src: CharSequence?): String {
            val (case, boundaries) = prepare(locale, iterator, src)
            return CaseMapImpl.toTitle(case, internalOptions, boundaries, src!!)
        }
        fun <A : Appendable?> apply(locale: ULocale?, iterator: BreakIterator?, src: CharSequence?, dest: A, edits: Edits?): A {
            val (case, boundaries) = prepare(locale, iterator, src)
            return mapToDestination(src, dest, edits) { text, output -> CaseMapImpl.toTitle(case, internalOptions, boundaries, text, output, edits) }
        }
        internal companion object {
            fun withOptions(options: Int): Title = Title(options)
            val DEFAULT = Title(0)
            private val OMIT_UNCHANGED = Title(CaseMapImpl.OMIT_UNCHANGED_TEXT)
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
        fun toTitle(): Title = Title.DEFAULT
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

private class WholeStringTitleIterator : BreakIterator() {
    private var length = 0
    private fun unsupported(): Nothing = throw UnsupportedOperationException("should not occur")
    override fun clone(): BreakIterator = WholeStringTitleIterator().also { it.length = length; copyLocaleMetadataTo(it) }
    override fun first(): Int = 0
    override fun next(): Int = length
    override fun last(): Int = unsupported()
    override fun next(n: Int): Int = unsupported()
    override fun previous(): Int = unsupported()
    override fun following(offset: Int): Int = unsupported()
    override fun current(): Int = unsupported()
    override fun getText(): CharacterIterator = unsupported()
    override fun setText(newText: CharacterIterator) { length = newText.getEndIndex() }
    override fun setText(newText: CharSequence?) { length = newText!!.length }
    override fun setText(newText: String?) { length = newText!!.length }
}
