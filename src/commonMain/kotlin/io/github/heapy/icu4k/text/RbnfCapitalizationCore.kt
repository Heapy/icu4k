// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// RuleBasedNumberFormat capitalization state and context adjustment, ICU4J 78.3.
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Mutable, shallow-cloned state; like the source RBNF formatter, it is not synchronized. */
internal class RbnfCapitalizationCore(
    private val locale: ULocale?,
    private val contextFlagsLoader: (ULocale?) -> IntArray? = ::loadContextFlags,
    private val sentenceIteratorFactory: (ULocale?) -> BreakIterator = { BreakIterator.getSentenceInstance(it) },
) {
    private var infoIsSet = false
    private var forListOrMenu = false
    private var forStandAlone = false
    private var iterator: BreakIterator? = null

    /** Parent NumberFormat context mutation happens even if subsequent initialization fails. */
    fun setContext(context: DisplayContext?, setParentContext: (DisplayContext?) -> Unit) {
        setParentContext(context)
        if (!infoIsSet && (context == DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU || context == DisplayContext.CAPITALIZATION_FOR_STANDALONE)) {
            val flags = contextFlagsLoader(locale)
            if (flags != null && flags.size >= 2) {
                forListOrMenu = flags[0] != 0
                forStandAlone = flags[1] != 0
            }
            infoIsSet = true
        }
        if (iterator == null && needsTitlecase(context)) iterator = sentenceIteratorFactory(locale)
    }
    private fun needsTitlecase(context: DisplayContext?): Boolean =
        context == DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE ||
            (context == DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU && forListOrMenu) ||
            (context == DisplayContext.CAPITALIZATION_FOR_STANDALONE && forStandAlone)

    /** Read the facade's virtual context before checking the result, as ICU4J does. */
    fun adjustForContext(result: String?, readContext: () -> DisplayContext?): String? {
        val capitalization = readContext()
        if (capitalization != DisplayContext.CAPITALIZATION_NONE && result != null && result.isNotEmpty() &&
            UCharacter.isLowerCase(UTF16.charAt(result, 0)) && needsTitlecase(capitalization)) {
            if (iterator == null) iterator = sentenceIteratorFactory(locale)
            return CaseMap.toTitle().noLowercase().noBreakAdjustment().apply(locale, iterator, result)
        }
        return result
    }
    /** Preserve the already-created iterator's identity and position, not a deep iterator clone. */
    fun copy(): RbnfCapitalizationCore = RbnfCapitalizationCore(locale, contextFlagsLoader, sentenceIteratorFactory).also {
        it.infoIsSet = infoIsSet
        it.forListOrMenu = forListOrMenu
        it.forStandAlone = forStandAlone
        it.iterator = iterator
    }
    private companion object {
        fun loadContextFlags(locale: ULocale?): IntArray? {
            // The bundle lookup is outside the missing-key catch in ICU4J.
            val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
            return try { bundle.getWithFallback("contextTransforms/number-spellout").getIntVector() }
            catch (_: MissingResourceException) { null }
        }
    }
}
