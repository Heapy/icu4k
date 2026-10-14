// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.BreakIterator
import io.github.heapy.icu4k.text.FilteredBreakIteratorBuilder
import io.github.heapy.icu4k.text.RuleBasedBreakIterator
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale

/** Resource-based creation extracted from BreakIteratorFactory pending service registration. */
internal object BreakIteratorDataFactory {
    private val kinds = arrayOf("grapheme", "word", "line", "sentence", "title")
    fun create(locale: ULocale?, kind: Int): BreakIterator {
        val bundle = ICUResourceBundle.getBundleInstance(ICUData.ICU_BRKITR_BASE_NAME, locale, ICUResourceBundle.OpenType.LOCALE_ROOT)
        var extension = ""
        if (kind == BreakIterator.KIND_LINE) {
            val line = locale!!.getKeywordValue("lb")
            if (line == "strict" || line == "normal" || line == "loose") extension = "_$line"
            val language = locale.getLanguage()
            if (language == "ja" || language == "ko") {
                val word = locale.getKeywordValue("lw")
                if (word == "phrase") extension += "_$word"
            }
        }
        val file: String
        val bytes: io.github.heapy.icu4k.util.BinaryBuffer
        try {
            file = bundle.getStringWithFallback("boundaries/" + checkedArrayElement(kinds, kind) + extension)
            bytes = ICUBinary.getRequiredData("${ICUData.ICU_BRKITR_NAME}/$file")
        } catch (failure: Exception) { throw MissingResourceException(failure.toString(), "", "") }
        val iterator = try { RuleBasedBreakIterator.getInstanceFromCompiledRules(bytes, "phrase" in file) }
        catch (failure: ICUUncheckedIOException) { failBreakRuleIOException(failure) }
        val valid = bundle.getULocale()
        iterator.setLocale(valid, valid)
        if (kind == BreakIterator.KIND_SENTENCE && locale!!.getKeywordValue("ss") == "standard") {
            return FilteredBreakIteratorBuilder.getInstance(ULocale(locale.getBaseName())).wrapIteratorWithFilter(iterator)
        }
        return iterator
    }
}

internal fun failBreakRuleIOException(failure: ICUUncheckedIOException): Nothing {
    val description = failure.cause?.toString() ?: failure.message?.let { "java.io.IOException: $it" } ?: "java.io.IOException"
    throw IllegalStateException("failure '$description'")
}
