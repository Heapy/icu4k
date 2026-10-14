// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2004-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

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

    private val resourceNames = setOf("curr/supplementalData.res", "numberingSystems.res", "es.res", "he.res", "lang/ja.res", "lang/ar.res", "lang/fr.res", "lang/en.res", "lang/pool.res", "lang/root.res", "ja.res", "ar.res", "fr.res", "en.res", "supplementalData.res", "langInfo.res", "metadata.res", "root.res", "pool.res", "res_index.res", "brkitr/res_index.res", "timezoneTypes.res", "keyTypeData.res", "brkitr/Burmese_graphclust_model5_heavy.res", "brkitr/Thai_graphclust_model4_heavy.res", "brkitr/jaml.res", "brkitr/ja.res", "brkitr/root.res", "unames.icu", "uts46.nrm", "nfkc_scf.nrm", "nfkc_cf.nrm", "nfkc.nrm", "nfc.nrm", "uemoji.icu", "pnames.icu", "ubidi.icu", "ucase.icu", "ulayout.icu", "uprops.icu", "brkitr/char.brk", "brkitr/line.brk", "brkitr/line_cj.brk", "brkitr/line_loose.brk", "brkitr/line_loose_cj.brk", "brkitr/line_loose_phrase_cj.brk", "brkitr/line_normal.brk", "brkitr/line_normal_cj.brk", "brkitr/line_normal_phrase_cj.brk", "brkitr/line_phrase_cj.brk", "brkitr/sent.brk", "brkitr/sent_el.brk", "brkitr/title.brk", "brkitr/word.brk", "brkitr/word_POSIX.brk", "brkitr/burmesedict.dict", "brkitr/cjdict.dict", "brkitr/khmerdict.dict", "brkitr/laodict.dict", "brkitr/thaidict.dict")
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
