// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.concurrent.atomics.AtomicReference

/** Miscellaneous CLDR exemplar, delimiter, display-pattern and measurement data. */
class LocaleData private constructor() {
    private var noSubstitute = false
    private lateinit var bundle: ICUResourceBundle
    private var langBundle: ICUResourceBundle? = null
    private var langBundleException: MissingResourceException? = null
    fun setNoSubstitute(setting: Boolean) { noSubstitute = setting }
    fun getNoSubstitute(): Boolean = noSubstitute
    fun getExemplarSet(options: Int, extype: Int): UnicodeSet? {
        if (extype == ES_CURRENCY) return if (noSubstitute) null else UnicodeSet.EMPTY
        try {
            val key = checkedArrayElement(exemplarTypes, extype)
            val stringBundle = bundle[key] as ICUResourceBundle
            if (noSubstitute && !bundle.isRoot() && stringBundle.isRoot()) return null
            return UnicodeSet(stringBundle.getString(), UnicodeSet.IGNORE_SPACE or options)
        } catch (failure: Exception) {
            if (extype !in exemplarTypes.indices || localeDataArrayBounds(failure)) throw IllegalArgumentException(failure)
            return if (noSubstitute) null else UnicodeSet.EMPTY
        }
    }
    fun getDelimiter(type: Int): String? {
        val delimiters = bundle["delimiters"] as ICUResourceBundle
        val stringBundle = delimiters.getWithFallback(checkedArrayElement(delimiterTypes, type))
        if (noSubstitute && !bundle.isRoot() && stringBundle.isRoot()) return null
        return stringBundle.getString()
    }
    fun getLocaleDisplayPattern(): String {
        val language = langBundle ?: throw langBundleException!!
        return (language["localeDisplayPattern"] as ICUResourceBundle).getStringWithFallback("pattern")
    }
    fun getLocaleSeparator(): String {
        val language = langBundle ?: throw langBundleException!!
        val separator = (language["localeDisplayPattern"] as ICUResourceBundle).getStringWithFallback("separator")
        val zero = separator.indexOf("{0}")
        val one = separator.indexOf("{1}")
        return if (zero >= 0 && one >= 0 && zero <= one) separator.substring(zero + 3, one) else separator
    }
    class MeasurementSystem private constructor() {
        companion object {
            val SI = MeasurementSystem()
            val US = MeasurementSystem()
            val UK = MeasurementSystem()
        }
    }
    class PaperSize private constructor(private val height: Int, private val width: Int) {
        fun getHeight(): Int = height
        fun getWidth(): Int = width
        companion object { internal fun create(height: Int, width: Int): PaperSize = PaperSize(height, width) }
    }
    companion object {
        const val ES_STANDARD = 0
        const val ES_AUXILIARY = 1
        const val ES_INDEX = 2
        @Deprecated("Currency symbol exemplars are no longer supported.") const val ES_CURRENCY = 3
        const val ES_PUNCTUATION = 4
        @Deprecated("The numeric value may change over time.") const val ES_COUNT = 5
        const val QUOTATION_START = 0
        const val QUOTATION_END = 1
        const val ALT_QUOTATION_START = 2
        const val ALT_QUOTATION_END = 3
        @Deprecated("The numeric value may change over time.") const val DELIMITER_COUNT = 4
        private val exemplarTypes = arrayOf("ExemplarCharacters", "AuxExemplarCharacters", "ExemplarCharactersIndex", "ExemplarCharactersCurrency", "ExemplarCharactersPunctuation")
        private val delimiterTypes = arrayOf("quotationStart", "quotationEnd", "alternateQuotationStart", "alternateQuotationEnd")
        private val cldrVersion = AtomicReference<VersionInfo?>(null)
        fun getInstance(locale: ULocale?): LocaleData {
            val result = LocaleData()
            result.bundle = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as ICUResourceBundle
            try { result.langBundle = UResourceBundle.getBundleInstance(ICUData.ICU_LANG_BASE_NAME, locale) as ICUResourceBundle }
            catch (failure: MissingResourceException) { result.langBundleException = failure }
            return result
        }
        fun getInstance(): LocaleData = getInstance(ULocale.getDefault(ULocale.Category.FORMAT))
        fun getExemplarSet(locale: ULocale?, options: Int): UnicodeSet? = getInstance(locale).getExemplarSet(options, ES_STANDARD)
        fun getExemplarSet(locale: ULocale?, options: Int, extype: Int): UnicodeSet? = getInstance(locale).getExemplarSet(options, extype)
        private fun measurementTypeBundle(locale: ULocale?, type: String): UResourceBundle? {
            val region = ULocale.getRegionForSupplementalData(locale, true)
            return try {
                val measurement = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "supplementalData")["measurementData"]
                try { measurement[region][type] } catch (_: MissingResourceException) { measurement["001"][type] }
            } catch (_: MissingResourceException) { null }
        }
        fun getMeasurementSystem(locale: ULocale?): MeasurementSystem? = when (measurementTypeBundle(locale, "MeasurementSystem")!!.getInt()) {
            0 -> MeasurementSystem.SI
            1 -> MeasurementSystem.US
            2 -> MeasurementSystem.UK
            else -> null
        }
        fun getPaperSize(locale: ULocale?): PaperSize {
            val sizes = measurementTypeBundle(locale, "PaperSize")!!.getIntVector()
            return PaperSize.create(checkedArrayElement(sizes, 0), checkedArrayElement(sizes, 1))
        }
        fun getCLDRVersion(): VersionInfo {
            if (cldrVersion.load() == null) {
                val supplemental = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "supplementalData")
                cldrVersion.store(VersionInfo.getInstance(supplemental["cldrVersion"].getString()))
            }
            return cldrVersion.load()!!
        }
    }
}
