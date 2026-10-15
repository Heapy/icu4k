// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2004-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.data.CurrencyNumericCodesResourceData
import io.github.heapy.icu4k.impl.data.CurrencyDeResourceData
import io.github.heapy.icu4k.impl.data.CurrencyZhResourceData
import io.github.heapy.icu4k.impl.data.CurrencyRuResourceData
import io.github.heapy.icu4k.impl.data.CurrencyHiResourceData
import io.github.heapy.icu4k.impl.data.CurrencyItResourceData
import io.github.heapy.icu4k.impl.data.CurrencyPtResourceData
import io.github.heapy.icu4k.impl.data.CurrencyKoResourceData
import io.github.heapy.icu4k.impl.data.CurrencyNlResourceData
import io.github.heapy.icu4k.impl.data.UnitRootResourceData
import io.github.heapy.icu4k.impl.data.UnitPoolResourceData
import io.github.heapy.icu4k.impl.data.UnitEnResourceData
import io.github.heapy.icu4k.impl.data.UnitLocaleIndexResourceData
import io.github.heapy.icu4k.impl.data.RegionRootResourceData
import io.github.heapy.icu4k.impl.data.RegionPoolResourceData
import io.github.heapy.icu4k.impl.data.RegionEnResourceData
import io.github.heapy.icu4k.impl.data.BreakEnResourceData
import io.github.heapy.icu4k.impl.data.BreakFrResourceData
import io.github.heapy.icu4k.impl.data.BreakDeResourceData
import io.github.heapy.icu4k.impl.data.BreakEsResourceData
import io.github.heapy.icu4k.impl.data.BreakElResourceData
import io.github.heapy.icu4k.impl.data.BreakKoResourceData
import io.github.heapy.icu4k.impl.data.BreakZhResourceData
import io.github.heapy.icu4k.impl.data.BreakZhHantResourceData
import io.github.heapy.icu4k.impl.data.BreakEnUsResourceData
import io.github.heapy.icu4k.impl.data.BreakEnUsPosixResourceData
import io.github.heapy.icu4k.impl.data.RegionFrResourceData
import io.github.heapy.icu4k.impl.data.RegionArResourceData
import io.github.heapy.icu4k.impl.data.RegionJaResourceData
import io.github.heapy.icu4k.impl.data.PluralsResourceData
import io.github.heapy.icu4k.impl.data.PluralRangesResourceData
import io.github.heapy.icu4k.impl.data.UnitsResourceData
import io.github.heapy.icu4k.impl.data.CurrencyRootResourceData
import io.github.heapy.icu4k.impl.data.CurrencyPoolResourceData
import io.github.heapy.icu4k.impl.data.CurrencyEnResourceData
import io.github.heapy.icu4k.impl.data.CurrencyFrResourceData
import io.github.heapy.icu4k.impl.data.CurrencyArResourceData
import io.github.heapy.icu4k.impl.data.CurrencyJaResourceData
import io.github.heapy.icu4k.impl.data.CurrencyEsResourceData
import io.github.heapy.icu4k.impl.data.CurrencyHeResourceData
import io.github.heapy.icu4k.impl.data.CurrencySupplementalResourceData
import io.github.heapy.icu4k.impl.data.NumberingSystemsResourceData
import io.github.heapy.icu4k.impl.data.CoreEsResourceData
import io.github.heapy.icu4k.impl.data.CoreHeResourceData
import io.github.heapy.icu4k.impl.data.CoreEnResourceData
import io.github.heapy.icu4k.impl.data.CoreFrResourceData
import io.github.heapy.icu4k.impl.data.CoreArResourceData
import io.github.heapy.icu4k.impl.data.CoreJaResourceData
import io.github.heapy.icu4k.impl.data.LanguageRootResourceData
import io.github.heapy.icu4k.impl.data.LanguagePoolResourceData
import io.github.heapy.icu4k.impl.data.LanguageEnResourceData
import io.github.heapy.icu4k.impl.data.LanguageFrResourceData
import io.github.heapy.icu4k.impl.data.LanguageArResourceData
import io.github.heapy.icu4k.impl.data.LanguageJaResourceData
import io.github.heapy.icu4k.impl.data.SupplementalResourceData
import io.github.heapy.icu4k.impl.data.LanguageInfoResourceData
import io.github.heapy.icu4k.impl.data.GrammaticalFeaturesResourceData
import io.github.heapy.icu4k.impl.data.GermanUnitResourceData
import io.github.heapy.icu4k.impl.data.FrenchUnitResourceData
import io.github.heapy.icu4k.impl.data.GermanResourceData
import io.github.heapy.icu4k.impl.data.RussianResourceData
import io.github.heapy.icu4k.impl.data.ArabicUnitResourceData
import io.github.heapy.icu4k.impl.data.LocaleMetadataResourceData
import io.github.heapy.icu4k.impl.data.RootResourceData
import io.github.heapy.icu4k.impl.data.PoolResourceData
import io.github.heapy.icu4k.impl.data.RootLocaleIndexData
import io.github.heapy.icu4k.impl.data.BreakLocaleIndexData
import io.github.heapy.icu4k.impl.data.TimezoneTypeResourceData
import io.github.heapy.icu4k.impl.data.KeyTypeResourceData
import io.github.heapy.icu4k.impl.data.BurmeseLstmModelData

import io.github.heapy.icu4k.impl.data.ThaiLstmModelData

import io.github.heapy.icu4k.impl.data.BreakJapaneseMlResourceData

import io.github.heapy.icu4k.impl.data.BreakJapaneseResourceData
import io.github.heapy.icu4k.impl.data.BreakRootResourceData

import io.github.heapy.icu4k.impl.data.UnamesData
import io.github.heapy.icu4k.impl.data.Uts46Data

import io.github.heapy.icu4k.impl.data.NfkcScfData

import io.github.heapy.icu4k.impl.data.NfkcCfData

import io.github.heapy.icu4k.impl.data.NfkcData

import io.github.heapy.icu4k.impl.data.NfcData

import io.github.heapy.icu4k.impl.data.BreakCharData
import io.github.heapy.icu4k.impl.data.BreakLineData
import io.github.heapy.icu4k.impl.data.BreakLineCjData
import io.github.heapy.icu4k.impl.data.BreakLineLooseData
import io.github.heapy.icu4k.impl.data.BreakLineLooseCjData
import io.github.heapy.icu4k.impl.data.BreakLineLoosePhraseCjData
import io.github.heapy.icu4k.impl.data.BreakLineNormalData
import io.github.heapy.icu4k.impl.data.BreakLineNormalCjData
import io.github.heapy.icu4k.impl.data.BreakLineNormalPhraseCjData
import io.github.heapy.icu4k.impl.data.BreakLinePhraseCjData
import io.github.heapy.icu4k.impl.data.BreakSentData
import io.github.heapy.icu4k.impl.data.BreakSentElData
import io.github.heapy.icu4k.impl.data.BreakTitleData
import io.github.heapy.icu4k.impl.data.BreakWordData
import io.github.heapy.icu4k.impl.data.BreakWordPosixData
import io.github.heapy.icu4k.impl.data.DictionaryBurmesedictData
import io.github.heapy.icu4k.impl.data.DictionaryCjdictData
import io.github.heapy.icu4k.impl.data.DictionaryKhmerdictData
import io.github.heapy.icu4k.impl.data.DictionaryLaodictData
import io.github.heapy.icu4k.impl.data.DictionaryThaidictData
import io.github.heapy.icu4k.impl.data.UcaseData
import io.github.heapy.icu4k.impl.data.UbidiData
import io.github.heapy.icu4k.impl.data.UlayoutData
import io.github.heapy.icu4k.impl.data.UpropsData
import io.github.heapy.icu4k.impl.data.PnamesData
import io.github.heapy.icu4k.impl.data.UemojiData

/** Version-pinned ICU resources available identically on JVM, JS and Native. */
object ICUData {
    internal const val PACKAGE_NAME = "icudt78b"
    const val ICU_BUNDLE = "data/icudata"
    const val ICU_BASE_NAME = "com/ibm/icu/impl/$ICU_BUNDLE"
    const val ICU_COLLATION_BASE_NAME = "$ICU_BASE_NAME/coll"
    const val ICU_BRKITR_NAME = "brkitr"
    const val ICU_BRKITR_BASE_NAME = "$ICU_BASE_NAME/$ICU_BRKITR_NAME"
    const val ICU_RBNF_BASE_NAME = "$ICU_BASE_NAME/rbnf"
    const val ICU_TRANSLIT_BASE_NAME = "$ICU_BASE_NAME/translit"
    const val ICU_LANG_BASE_NAME = "$ICU_BASE_NAME/lang"
    const val ICU_CURR_BASE_NAME = "$ICU_BASE_NAME/curr"
    const val ICU_REGION_BASE_NAME = "$ICU_BASE_NAME/region"
    const val ICU_ZONE_BASE_NAME = "$ICU_BASE_NAME/zone"
    const val ICU_UNIT_BASE_NAME = "$ICU_BASE_NAME/unit"

    private val resourceNames = setOf("unit/ar.res", "ru.res", "de.res", "unit/fr.res", "unit/de.res", "grammaticalFeatures.res", "region/fr.res", "region/ar.res", "region/ja.res", "brkitr/en_US.res", "brkitr/en_US_POSIX.res", "brkitr/el.res", "brkitr/ko.res", "brkitr/zh.res", "brkitr/zh_Hant.res", "brkitr/en.res", "brkitr/fr.res", "brkitr/de.res", "brkitr/es.res", "region/root.res", "region/pool.res", "region/en.res", "unit/root.res", "unit/pool.res", "unit/en.res", "unit/res_index.res", "curr/de.res", "curr/zh.res", "curr/ru.res", "curr/hi.res", "curr/it.res", "curr/pt.res", "curr/ko.res", "curr/nl.res", "currencyNumericCodes.res", "units.res", "curr/he.res", "curr/es.res", "curr/ja.res", "curr/ar.res", "curr/fr.res", "curr/en.res", "curr/pool.res", "curr/root.res", "curr/supplementalData.res", "numberingSystems.res", "es.res", "he.res", "lang/ja.res", "lang/ar.res", "lang/fr.res", "lang/en.res", "lang/pool.res", "lang/root.res", "ja.res", "ar.res", "fr.res", "en.res", "supplementalData.res", "langInfo.res", "metadata.res", "root.res", "pool.res", "res_index.res", "brkitr/res_index.res", "timezoneTypes.res", "keyTypeData.res", "brkitr/Burmese_graphclust_model5_heavy.res", "brkitr/Thai_graphclust_model4_heavy.res", "brkitr/jaml.res", "brkitr/ja.res", "brkitr/root.res", "unames.icu", "uts46.nrm", "nfkc_scf.nrm", "nfkc_cf.nrm", "nfkc.nrm", "nfc.nrm", "uemoji.icu", "pnames.icu", "ubidi.icu", "ucase.icu", "ulayout.icu", "uprops.icu", "brkitr/char.brk", "brkitr/line.brk", "brkitr/line_cj.brk", "brkitr/line_loose.brk", "brkitr/line_loose_cj.brk", "brkitr/line_loose_phrase_cj.brk", "brkitr/line_normal.brk", "brkitr/line_normal_cj.brk", "brkitr/line_normal_phrase_cj.brk", "brkitr/line_phrase_cj.brk", "brkitr/sent.brk", "brkitr/sent_el.brk", "brkitr/title.brk", "brkitr/word.brk", "brkitr/word_POSIX.brk", "brkitr/burmesedict.dict", "brkitr/cjdict.dict", "brkitr/khmerdict.dict", "brkitr/laodict.dict", "brkitr/thaidict.dict")
    internal fun visitResources(directory: String, visitor: (String) -> Unit) {
        val prefix = directory.removePrefix("/")
        if (!prefix.startsWith("$ICU_BASE_NAME/")) return
        val folder = prefix.removePrefix("$ICU_BASE_NAME/")
        for (name in resourceNames) if (name.startsWith(folder)) {
            val relative = name.substring(folder.length)
            if ('/' !in relative) visitor(relative)
        }
    }
    private fun itemPath(name: String): String = name.removePrefix("/").removePrefix("$ICU_BASE_NAME/")
    fun exists(resourceName: String): Boolean = itemPath(resourceName) in resourceNames

    /** Returns a private copy, so callers cannot mutate cached resource data. */
    fun getBytes(resourceName: String): ByteArray? = when (itemPath(resourceName)) {
        "currencyNumericCodes.res" -> CurrencyNumericCodesResourceData.copy()
        "curr/de.res" -> CurrencyDeResourceData.copy()
        "curr/zh.res" -> CurrencyZhResourceData.copy()
        "curr/ru.res" -> CurrencyRuResourceData.copy()
        "curr/hi.res" -> CurrencyHiResourceData.copy()
        "curr/it.res" -> CurrencyItResourceData.copy()
        "curr/pt.res" -> CurrencyPtResourceData.copy()
        "curr/ko.res" -> CurrencyKoResourceData.copy()
        "curr/nl.res" -> CurrencyNlResourceData.copy()
        "unit/root.res" -> UnitRootResourceData.copy()
        "unit/pool.res" -> UnitPoolResourceData.copy()
        "unit/en.res" -> UnitEnResourceData.copy()
        "unit/res_index.res" -> UnitLocaleIndexResourceData.copy()
        "region/root.res" -> RegionRootResourceData.copy()
        "region/pool.res" -> RegionPoolResourceData.copy()
        "region/en.res" -> RegionEnResourceData.copy()
        "brkitr/en.res" -> BreakEnResourceData.copy()
        "brkitr/fr.res" -> BreakFrResourceData.copy()
        "brkitr/de.res" -> BreakDeResourceData.copy()
        "brkitr/es.res" -> BreakEsResourceData.copy()
        "brkitr/el.res" -> BreakElResourceData.copy()
        "brkitr/ko.res" -> BreakKoResourceData.copy()
        "brkitr/zh.res" -> BreakZhResourceData.copy()
        "brkitr/zh_Hant.res" -> BreakZhHantResourceData.copy()
        "brkitr/en_US.res" -> BreakEnUsResourceData.copy()
        "brkitr/en_US_POSIX.res" -> BreakEnUsPosixResourceData.copy()
        "region/fr.res" -> RegionFrResourceData.copy()
        "region/ar.res" -> RegionArResourceData.copy()
        "region/ja.res" -> RegionJaResourceData.copy()
        "units.res" -> UnitsResourceData.copy()
        "plurals.res" -> PluralsResourceData.copy()
        "pluralRanges.res" -> PluralRangesResourceData.copy()
        "curr/root.res" -> CurrencyRootResourceData.copy()
        "curr/pool.res" -> CurrencyPoolResourceData.copy()
        "curr/en.res" -> CurrencyEnResourceData.copy()
        "curr/fr.res" -> CurrencyFrResourceData.copy()
        "curr/ar.res" -> CurrencyArResourceData.copy()
        "curr/ja.res" -> CurrencyJaResourceData.copy()
        "curr/es.res" -> CurrencyEsResourceData.copy()
        "curr/he.res" -> CurrencyHeResourceData.copy()
        "curr/supplementalData.res" -> CurrencySupplementalResourceData.copy()
        "numberingSystems.res" -> NumberingSystemsResourceData.copy()
        "es.res" -> CoreEsResourceData.copy()
        "he.res" -> CoreHeResourceData.copy()
        "en.res" -> CoreEnResourceData.copy()
        "fr.res" -> CoreFrResourceData.copy()
        "ar.res" -> CoreArResourceData.copy()
        "ja.res" -> CoreJaResourceData.copy()
        "lang/root.res" -> LanguageRootResourceData.copy()
        "lang/pool.res" -> LanguagePoolResourceData.copy()
        "lang/en.res" -> LanguageEnResourceData.copy()
        "lang/fr.res" -> LanguageFrResourceData.copy()
        "lang/ar.res" -> LanguageArResourceData.copy()
        "lang/ja.res" -> LanguageJaResourceData.copy()
        "supplementalData.res" -> SupplementalResourceData.copy()
        "langInfo.res" -> LanguageInfoResourceData.copy()
        "grammaticalFeatures.res" -> GrammaticalFeaturesResourceData.copy()
        "unit/de.res" -> GermanUnitResourceData.copy()
        "unit/fr.res" -> FrenchUnitResourceData.copy()
        "de.res" -> GermanResourceData.copy()
        "ru.res" -> RussianResourceData.copy()
        "unit/ar.res" -> ArabicUnitResourceData.copy()
        "metadata.res" -> LocaleMetadataResourceData.copy()
        "root.res" -> RootResourceData.copy()
        "pool.res" -> PoolResourceData.copy()
        "res_index.res" -> RootLocaleIndexData.copy()
        "brkitr/res_index.res" -> BreakLocaleIndexData.copy()
        "timezoneTypes.res" -> TimezoneTypeResourceData.copy()
        "keyTypeData.res" -> KeyTypeResourceData.copy()
        "brkitr/ja.res" -> BreakJapaneseResourceData.copy()
        "brkitr/root.res" -> BreakRootResourceData.copy()
        "brkitr/jaml.res" -> BreakJapaneseMlResourceData.copy()
        "brkitr/Thai_graphclust_model4_heavy.res" -> ThaiLstmModelData.copy()
        "brkitr/Burmese_graphclust_model5_heavy.res" -> BurmeseLstmModelData.copy()
        "unames.icu" -> UnamesData.copy()
        "nfc.nrm" -> NfcData.copy()
        "nfkc.nrm" -> NfkcData.copy()
        "nfkc_cf.nrm" -> NfkcCfData.copy()
        "nfkc_scf.nrm" -> NfkcScfData.copy()
        "uts46.nrm" -> Uts46Data.copy()
        "uemoji.icu" -> UemojiData.copy()
        "pnames.icu" -> PnamesData.copy()
        "ucase.icu" -> UcaseData.copy()
        "ubidi.icu" -> UbidiData.copy()
        "uprops.icu" -> UpropsData.copy()
        "ulayout.icu" -> UlayoutData.copy()
        "brkitr/char.brk" -> BreakCharData.copy()
        "brkitr/line.brk" -> BreakLineData.copy()
        "brkitr/line_cj.brk" -> BreakLineCjData.copy()
        "brkitr/line_loose.brk" -> BreakLineLooseData.copy()
        "brkitr/line_loose_cj.brk" -> BreakLineLooseCjData.copy()
        "brkitr/line_loose_phrase_cj.brk" -> BreakLineLoosePhraseCjData.copy()
        "brkitr/line_normal.brk" -> BreakLineNormalData.copy()
        "brkitr/line_normal_cj.brk" -> BreakLineNormalCjData.copy()
        "brkitr/line_normal_phrase_cj.brk" -> BreakLineNormalPhraseCjData.copy()
        "brkitr/line_phrase_cj.brk" -> BreakLinePhraseCjData.copy()
        "brkitr/sent.brk" -> BreakSentData.copy()
        "brkitr/sent_el.brk" -> BreakSentElData.copy()
        "brkitr/title.brk" -> BreakTitleData.copy()
        "brkitr/word.brk" -> BreakWordData.copy()
        "brkitr/word_POSIX.brk" -> BreakWordPosixData.copy()
        "brkitr/burmesedict.dict" -> DictionaryBurmesedictData.copy()
        "brkitr/cjdict.dict" -> DictionaryCjdictData.copy()
        "brkitr/khmerdict.dict" -> DictionaryKhmerdictData.copy()
        "brkitr/laodict.dict" -> DictionaryLaodictData.copy()
        "brkitr/thaidict.dict" -> DictionaryThaidictData.copy()
        else -> null
    }
}
