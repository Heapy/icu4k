// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// Locale resource loading from ICU4J 78.3 RuleBasedNumberFormat's constructor.
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.ICUResourceLoader
import io.github.heapy.icu4k.impl.defaultICUResourceLoader
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** Predefined descriptions and optional display-name tables, before rule parsing. */
internal class RbnfRuleData private constructor(
    val description: String,
    val localizations: Array<Array<String>>?,
    val actualLocale: ULocale,
) {
    companion object {
        private val ruleNames = arrayOf("SpelloutRules", "OrdinalRules", "DurationRules", "NumberingSystemRules")
        private val localizationNames = arrayOf("SpelloutLocalizations", "OrdinalLocalizations", "DurationLocalizations", "NumberingSystemLocalizations")

        /** The owner installs bundle locale metadata before rules/display names are accessed. */
        fun load(locale: ULocale?, format: Int, loader: ICUResourceLoader? = defaultICUResourceLoader(), onBundleLocale: (ULocale) -> Unit = {}): RbnfRuleData {
            val bundle = UResourceBundle.getBundleInstance(ICUData.ICU_RBNF_BASE_NAME, locale, loader) as ICUResourceBundle
            val actual = bundle.getULocale()
            onBundleLocale(actual)
            val description = StringBuilder()
            try {
                val index = format - 1
                // Kotlin/JS Array.get is unchecked; Java fails here before resource lookup.
                if (index !in ruleNames.indices) throw IndexOutOfBoundsException()
                val rules = bundle.getWithFallback("RBNFRules/" + ruleNames[index])
                val iterator = rules.getIterator()
                while (iterator.hasNext()) description.append(iterator.nextString())
            } catch (_: MissingResourceException) {
                // A missing rule group is passed to init as an empty description in ICU4J.
            }
            val locNamesBundle = bundle.findTopLevel(localizationNames[format - 1])
            val localizations = locNamesBundle?.let { names -> Array(names.getSize()) { names[it].getStringArray() } }
            return RbnfRuleData(description.toString(), localizations, actual)
        }
    }
}
