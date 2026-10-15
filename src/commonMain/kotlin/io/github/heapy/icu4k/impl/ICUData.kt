// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2004-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.data.RbnfAfResourceData
import io.github.heapy.icu4k.impl.data.RbnfAkResourceData
import io.github.heapy.icu4k.impl.data.RbnfAmResourceData
import io.github.heapy.icu4k.impl.data.RbnfArResourceData
import io.github.heapy.icu4k.impl.data.RbnfArSAResourceData
import io.github.heapy.icu4k.impl.data.RbnfArsResourceData
import io.github.heapy.icu4k.impl.data.RbnfAzResourceData
import io.github.heapy.icu4k.impl.data.RbnfBeResourceData
import io.github.heapy.icu4k.impl.data.RbnfBgResourceData
import io.github.heapy.icu4k.impl.data.RbnfBsResourceData
import io.github.heapy.icu4k.impl.data.RbnfCaResourceData
import io.github.heapy.icu4k.impl.data.RbnfCcpResourceData
import io.github.heapy.icu4k.impl.data.RbnfChrResourceData
import io.github.heapy.icu4k.impl.data.RbnfCsResourceData
import io.github.heapy.icu4k.impl.data.RbnfCyResourceData
import io.github.heapy.icu4k.impl.data.RbnfDaResourceData
import io.github.heapy.icu4k.impl.data.RbnfDeResourceData
import io.github.heapy.icu4k.impl.data.RbnfDeCHResourceData
import io.github.heapy.icu4k.impl.data.RbnfEeResourceData
import io.github.heapy.icu4k.impl.data.RbnfElResourceData
import io.github.heapy.icu4k.impl.data.RbnfEnResourceData
import io.github.heapy.icu4k.impl.data.RbnfEn001ResourceData
import io.github.heapy.icu4k.impl.data.RbnfEnINResourceData
import io.github.heapy.icu4k.impl.data.RbnfEoResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsResourceData
import io.github.heapy.icu4k.impl.data.RbnfEs419ResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsDOResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsGTResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsHNResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsMXResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsNIResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsPAResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsPRResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsSVResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsUSResourceData
import io.github.heapy.icu4k.impl.data.RbnfEtResourceData
import io.github.heapy.icu4k.impl.data.RbnfFaResourceData
import io.github.heapy.icu4k.impl.data.RbnfFaAFResourceData
import io.github.heapy.icu4k.impl.data.RbnfFfResourceData
import io.github.heapy.icu4k.impl.data.RbnfFiResourceData
import io.github.heapy.icu4k.impl.data.RbnfFilResourceData
import io.github.heapy.icu4k.impl.data.RbnfFoResourceData
import io.github.heapy.icu4k.impl.data.RbnfFrResourceData
import io.github.heapy.icu4k.impl.data.RbnfFrBEResourceData
import io.github.heapy.icu4k.impl.data.RbnfFrCHResourceData
import io.github.heapy.icu4k.impl.data.RbnfGaResourceData
import io.github.heapy.icu4k.impl.data.RbnfGuResourceData
import io.github.heapy.icu4k.impl.data.RbnfHeResourceData
import io.github.heapy.icu4k.impl.data.RbnfHiResourceData
import io.github.heapy.icu4k.impl.data.RbnfHrResourceData
import io.github.heapy.icu4k.impl.data.RbnfHuResourceData
import io.github.heapy.icu4k.impl.data.RbnfHyResourceData
import io.github.heapy.icu4k.impl.data.RbnfIdResourceData
import io.github.heapy.icu4k.impl.data.RbnfInResourceData
import io.github.heapy.icu4k.impl.data.RbnfIsResourceData
import io.github.heapy.icu4k.impl.data.RbnfItResourceData
import io.github.heapy.icu4k.impl.data.RbnfIwResourceData
import io.github.heapy.icu4k.impl.data.RbnfJaResourceData
import io.github.heapy.icu4k.impl.data.RbnfKaResourceData
import io.github.heapy.icu4k.impl.data.RbnfKkResourceData
import io.github.heapy.icu4k.impl.data.RbnfKlResourceData
import io.github.heapy.icu4k.impl.data.RbnfKmResourceData
import io.github.heapy.icu4k.impl.data.RbnfKoResourceData
import io.github.heapy.icu4k.impl.data.RbnfKyResourceData
import io.github.heapy.icu4k.impl.data.RbnfLbResourceData
import io.github.heapy.icu4k.impl.data.RbnfLoResourceData
import io.github.heapy.icu4k.impl.data.RbnfLrcResourceData
import io.github.heapy.icu4k.impl.data.RbnfLtResourceData
import io.github.heapy.icu4k.impl.data.RbnfLvResourceData
import io.github.heapy.icu4k.impl.data.RbnfMkResourceData
import io.github.heapy.icu4k.impl.data.RbnfMsResourceData
import io.github.heapy.icu4k.impl.data.RbnfMtResourceData
import io.github.heapy.icu4k.impl.data.RbnfMyResourceData
import io.github.heapy.icu4k.impl.data.RbnfNbResourceData
import io.github.heapy.icu4k.impl.data.RbnfNeResourceData
import io.github.heapy.icu4k.impl.data.RbnfNlResourceData
import io.github.heapy.icu4k.impl.data.RbnfNnResourceData
import io.github.heapy.icu4k.impl.data.RbnfNoResourceData
import io.github.heapy.icu4k.impl.data.RbnfPlResourceData
import io.github.heapy.icu4k.impl.data.RbnfPtResourceData
import io.github.heapy.icu4k.impl.data.RbnfPtPTResourceData
import io.github.heapy.icu4k.impl.data.RbnfQuResourceData
import io.github.heapy.icu4k.impl.data.RbnfResIndexResourceData
import io.github.heapy.icu4k.impl.data.RbnfRoResourceData
import io.github.heapy.icu4k.impl.data.RbnfRootResourceData
import io.github.heapy.icu4k.impl.data.RbnfRuResourceData
import io.github.heapy.icu4k.impl.data.RbnfSeResourceData
import io.github.heapy.icu4k.impl.data.RbnfShResourceData
import io.github.heapy.icu4k.impl.data.RbnfSkResourceData
import io.github.heapy.icu4k.impl.data.RbnfSlResourceData
import io.github.heapy.icu4k.impl.data.RbnfSqResourceData
import io.github.heapy.icu4k.impl.data.RbnfSrResourceData
import io.github.heapy.icu4k.impl.data.RbnfSrLatnResourceData
import io.github.heapy.icu4k.impl.data.RbnfSuResourceData
import io.github.heapy.icu4k.impl.data.RbnfSvResourceData
import io.github.heapy.icu4k.impl.data.RbnfSwResourceData
import io.github.heapy.icu4k.impl.data.RbnfTaResourceData
import io.github.heapy.icu4k.impl.data.RbnfThResourceData
import io.github.heapy.icu4k.impl.data.RbnfTrResourceData
import io.github.heapy.icu4k.impl.data.RbnfUkResourceData
import io.github.heapy.icu4k.impl.data.RbnfVecResourceData
import io.github.heapy.icu4k.impl.data.RbnfViResourceData
import io.github.heapy.icu4k.impl.data.RbnfYueResourceData
import io.github.heapy.icu4k.impl.data.RbnfYueHansResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhHKResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhHantResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhHantHKResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhHantMOResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhHantTWResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhMOResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhTWResourceData
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

    private val resourceNames = setOf("plurals.res", "pluralRanges.res", "unit/ar.res", "ru.res", "de.res", "unit/fr.res", "unit/de.res", "grammaticalFeatures.res", "region/fr.res", "region/ar.res", "region/ja.res", "brkitr/en_US.res", "brkitr/en_US_POSIX.res", "brkitr/el.res", "brkitr/ko.res", "brkitr/zh.res", "brkitr/zh_Hant.res", "brkitr/en.res", "brkitr/fr.res", "brkitr/de.res", "brkitr/es.res", "region/root.res", "region/pool.res", "region/en.res", "unit/root.res", "unit/pool.res", "unit/en.res", "unit/res_index.res", "curr/de.res", "curr/zh.res", "curr/ru.res", "curr/hi.res", "curr/it.res", "curr/pt.res", "curr/ko.res", "curr/nl.res", "currencyNumericCodes.res", "units.res", "curr/he.res", "curr/es.res", "curr/ja.res", "curr/ar.res", "curr/fr.res", "curr/en.res", "curr/pool.res", "curr/root.res", "curr/supplementalData.res", "numberingSystems.res", "es.res", "he.res", "lang/ja.res", "lang/ar.res", "lang/fr.res", "lang/en.res", "lang/pool.res", "lang/root.res", "ja.res", "ar.res", "fr.res", "en.res", "supplementalData.res", "langInfo.res", "metadata.res", "root.res", "pool.res", "res_index.res", "brkitr/res_index.res", "timezoneTypes.res", "keyTypeData.res", "brkitr/Burmese_graphclust_model5_heavy.res", "brkitr/Thai_graphclust_model4_heavy.res", "brkitr/jaml.res", "brkitr/ja.res", "brkitr/root.res", "unames.icu", "uts46.nrm", "nfkc_scf.nrm", "nfkc_cf.nrm", "nfkc.nrm", "nfc.nrm", "uemoji.icu", "pnames.icu", "ubidi.icu", "ucase.icu", "ulayout.icu", "uprops.icu", "brkitr/char.brk", "brkitr/line.brk", "brkitr/line_cj.brk", "brkitr/line_loose.brk", "brkitr/line_loose_cj.brk", "brkitr/line_loose_phrase_cj.brk", "brkitr/line_normal.brk", "brkitr/line_normal_cj.brk", "brkitr/line_normal_phrase_cj.brk", "brkitr/line_phrase_cj.brk", "brkitr/sent.brk", "brkitr/sent_el.brk", "brkitr/title.brk", "brkitr/word.brk", "brkitr/word_POSIX.brk", "brkitr/burmesedict.dict", "brkitr/cjdict.dict", "brkitr/khmerdict.dict", "brkitr/laodict.dict", "brkitr/thaidict.dict",
        "rbnf/af.res",
        "rbnf/ak.res",
        "rbnf/am.res",
        "rbnf/ar.res",
        "rbnf/ar_SA.res",
        "rbnf/ars.res",
        "rbnf/az.res",
        "rbnf/be.res",
        "rbnf/bg.res",
        "rbnf/bs.res",
        "rbnf/ca.res",
        "rbnf/ccp.res",
        "rbnf/chr.res",
        "rbnf/cs.res",
        "rbnf/cy.res",
        "rbnf/da.res",
        "rbnf/de.res",
        "rbnf/de_CH.res",
        "rbnf/ee.res",
        "rbnf/el.res",
        "rbnf/en.res",
        "rbnf/en_001.res",
        "rbnf/en_IN.res",
        "rbnf/eo.res",
        "rbnf/es.res",
        "rbnf/es_419.res",
        "rbnf/es_DO.res",
        "rbnf/es_GT.res",
        "rbnf/es_HN.res",
        "rbnf/es_MX.res",
        "rbnf/es_NI.res",
        "rbnf/es_PA.res",
        "rbnf/es_PR.res",
        "rbnf/es_SV.res",
        "rbnf/es_US.res",
        "rbnf/et.res",
        "rbnf/fa.res",
        "rbnf/fa_AF.res",
        "rbnf/ff.res",
        "rbnf/fi.res",
        "rbnf/fil.res",
        "rbnf/fo.res",
        "rbnf/fr.res",
        "rbnf/fr_BE.res",
        "rbnf/fr_CH.res",
        "rbnf/ga.res",
        "rbnf/gu.res",
        "rbnf/he.res",
        "rbnf/hi.res",
        "rbnf/hr.res",
        "rbnf/hu.res",
        "rbnf/hy.res",
        "rbnf/id.res",
        "rbnf/in.res",
        "rbnf/is.res",
        "rbnf/it.res",
        "rbnf/iw.res",
        "rbnf/ja.res",
        "rbnf/ka.res",
        "rbnf/kk.res",
        "rbnf/kl.res",
        "rbnf/km.res",
        "rbnf/ko.res",
        "rbnf/ky.res",
        "rbnf/lb.res",
        "rbnf/lo.res",
        "rbnf/lrc.res",
        "rbnf/lt.res",
        "rbnf/lv.res",
        "rbnf/mk.res",
        "rbnf/ms.res",
        "rbnf/mt.res",
        "rbnf/my.res",
        "rbnf/nb.res",
        "rbnf/ne.res",
        "rbnf/nl.res",
        "rbnf/nn.res",
        "rbnf/no.res",
        "rbnf/pl.res",
        "rbnf/pt.res",
        "rbnf/pt_PT.res",
        "rbnf/qu.res",
        "rbnf/res_index.res",
        "rbnf/ro.res",
        "rbnf/root.res",
        "rbnf/ru.res",
        "rbnf/se.res",
        "rbnf/sh.res",
        "rbnf/sk.res",
        "rbnf/sl.res",
        "rbnf/sq.res",
        "rbnf/sr.res",
        "rbnf/sr_Latn.res",
        "rbnf/su.res",
        "rbnf/sv.res",
        "rbnf/sw.res",
        "rbnf/ta.res",
        "rbnf/th.res",
        "rbnf/tr.res",
        "rbnf/uk.res",
        "rbnf/vec.res",
        "rbnf/vi.res",
        "rbnf/yue.res",
        "rbnf/yue_Hans.res",
        "rbnf/zh.res",
        "rbnf/zh_HK.res",
        "rbnf/zh_Hant.res",
        "rbnf/zh_Hant_HK.res",
        "rbnf/zh_Hant_MO.res",
        "rbnf/zh_Hant_TW.res",
        "rbnf/zh_MO.res",
        "rbnf/zh_TW.res",
    )
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
        "rbnf/af.res" -> RbnfAfResourceData.copy()
        "rbnf/ak.res" -> RbnfAkResourceData.copy()
        "rbnf/am.res" -> RbnfAmResourceData.copy()
        "rbnf/ar.res" -> RbnfArResourceData.copy()
        "rbnf/ar_SA.res" -> RbnfArSAResourceData.copy()
        "rbnf/ars.res" -> RbnfArsResourceData.copy()
        "rbnf/az.res" -> RbnfAzResourceData.copy()
        "rbnf/be.res" -> RbnfBeResourceData.copy()
        "rbnf/bg.res" -> RbnfBgResourceData.copy()
        "rbnf/bs.res" -> RbnfBsResourceData.copy()
        "rbnf/ca.res" -> RbnfCaResourceData.copy()
        "rbnf/ccp.res" -> RbnfCcpResourceData.copy()
        "rbnf/chr.res" -> RbnfChrResourceData.copy()
        "rbnf/cs.res" -> RbnfCsResourceData.copy()
        "rbnf/cy.res" -> RbnfCyResourceData.copy()
        "rbnf/da.res" -> RbnfDaResourceData.copy()
        "rbnf/de.res" -> RbnfDeResourceData.copy()
        "rbnf/de_CH.res" -> RbnfDeCHResourceData.copy()
        "rbnf/ee.res" -> RbnfEeResourceData.copy()
        "rbnf/el.res" -> RbnfElResourceData.copy()
        "rbnf/en.res" -> RbnfEnResourceData.copy()
        "rbnf/en_001.res" -> RbnfEn001ResourceData.copy()
        "rbnf/en_IN.res" -> RbnfEnINResourceData.copy()
        "rbnf/eo.res" -> RbnfEoResourceData.copy()
        "rbnf/es.res" -> RbnfEsResourceData.copy()
        "rbnf/es_419.res" -> RbnfEs419ResourceData.copy()
        "rbnf/es_DO.res" -> RbnfEsDOResourceData.copy()
        "rbnf/es_GT.res" -> RbnfEsGTResourceData.copy()
        "rbnf/es_HN.res" -> RbnfEsHNResourceData.copy()
        "rbnf/es_MX.res" -> RbnfEsMXResourceData.copy()
        "rbnf/es_NI.res" -> RbnfEsNIResourceData.copy()
        "rbnf/es_PA.res" -> RbnfEsPAResourceData.copy()
        "rbnf/es_PR.res" -> RbnfEsPRResourceData.copy()
        "rbnf/es_SV.res" -> RbnfEsSVResourceData.copy()
        "rbnf/es_US.res" -> RbnfEsUSResourceData.copy()
        "rbnf/et.res" -> RbnfEtResourceData.copy()
        "rbnf/fa.res" -> RbnfFaResourceData.copy()
        "rbnf/fa_AF.res" -> RbnfFaAFResourceData.copy()
        "rbnf/ff.res" -> RbnfFfResourceData.copy()
        "rbnf/fi.res" -> RbnfFiResourceData.copy()
        "rbnf/fil.res" -> RbnfFilResourceData.copy()
        "rbnf/fo.res" -> RbnfFoResourceData.copy()
        "rbnf/fr.res" -> RbnfFrResourceData.copy()
        "rbnf/fr_BE.res" -> RbnfFrBEResourceData.copy()
        "rbnf/fr_CH.res" -> RbnfFrCHResourceData.copy()
        "rbnf/ga.res" -> RbnfGaResourceData.copy()
        "rbnf/gu.res" -> RbnfGuResourceData.copy()
        "rbnf/he.res" -> RbnfHeResourceData.copy()
        "rbnf/hi.res" -> RbnfHiResourceData.copy()
        "rbnf/hr.res" -> RbnfHrResourceData.copy()
        "rbnf/hu.res" -> RbnfHuResourceData.copy()
        "rbnf/hy.res" -> RbnfHyResourceData.copy()
        "rbnf/id.res" -> RbnfIdResourceData.copy()
        "rbnf/in.res" -> RbnfInResourceData.copy()
        "rbnf/is.res" -> RbnfIsResourceData.copy()
        "rbnf/it.res" -> RbnfItResourceData.copy()
        "rbnf/iw.res" -> RbnfIwResourceData.copy()
        "rbnf/ja.res" -> RbnfJaResourceData.copy()
        "rbnf/ka.res" -> RbnfKaResourceData.copy()
        "rbnf/kk.res" -> RbnfKkResourceData.copy()
        "rbnf/kl.res" -> RbnfKlResourceData.copy()
        "rbnf/km.res" -> RbnfKmResourceData.copy()
        "rbnf/ko.res" -> RbnfKoResourceData.copy()
        "rbnf/ky.res" -> RbnfKyResourceData.copy()
        "rbnf/lb.res" -> RbnfLbResourceData.copy()
        "rbnf/lo.res" -> RbnfLoResourceData.copy()
        "rbnf/lrc.res" -> RbnfLrcResourceData.copy()
        "rbnf/lt.res" -> RbnfLtResourceData.copy()
        "rbnf/lv.res" -> RbnfLvResourceData.copy()
        "rbnf/mk.res" -> RbnfMkResourceData.copy()
        "rbnf/ms.res" -> RbnfMsResourceData.copy()
        "rbnf/mt.res" -> RbnfMtResourceData.copy()
        "rbnf/my.res" -> RbnfMyResourceData.copy()
        "rbnf/nb.res" -> RbnfNbResourceData.copy()
        "rbnf/ne.res" -> RbnfNeResourceData.copy()
        "rbnf/nl.res" -> RbnfNlResourceData.copy()
        "rbnf/nn.res" -> RbnfNnResourceData.copy()
        "rbnf/no.res" -> RbnfNoResourceData.copy()
        "rbnf/pl.res" -> RbnfPlResourceData.copy()
        "rbnf/pt.res" -> RbnfPtResourceData.copy()
        "rbnf/pt_PT.res" -> RbnfPtPTResourceData.copy()
        "rbnf/qu.res" -> RbnfQuResourceData.copy()
        "rbnf/res_index.res" -> RbnfResIndexResourceData.copy()
        "rbnf/ro.res" -> RbnfRoResourceData.copy()
        "rbnf/root.res" -> RbnfRootResourceData.copy()
        "rbnf/ru.res" -> RbnfRuResourceData.copy()
        "rbnf/se.res" -> RbnfSeResourceData.copy()
        "rbnf/sh.res" -> RbnfShResourceData.copy()
        "rbnf/sk.res" -> RbnfSkResourceData.copy()
        "rbnf/sl.res" -> RbnfSlResourceData.copy()
        "rbnf/sq.res" -> RbnfSqResourceData.copy()
        "rbnf/sr.res" -> RbnfSrResourceData.copy()
        "rbnf/sr_Latn.res" -> RbnfSrLatnResourceData.copy()
        "rbnf/su.res" -> RbnfSuResourceData.copy()
        "rbnf/sv.res" -> RbnfSvResourceData.copy()
        "rbnf/sw.res" -> RbnfSwResourceData.copy()
        "rbnf/ta.res" -> RbnfTaResourceData.copy()
        "rbnf/th.res" -> RbnfThResourceData.copy()
        "rbnf/tr.res" -> RbnfTrResourceData.copy()
        "rbnf/uk.res" -> RbnfUkResourceData.copy()
        "rbnf/vec.res" -> RbnfVecResourceData.copy()
        "rbnf/vi.res" -> RbnfViResourceData.copy()
        "rbnf/yue.res" -> RbnfYueResourceData.copy()
        "rbnf/yue_Hans.res" -> RbnfYueHansResourceData.copy()
        "rbnf/zh.res" -> RbnfZhResourceData.copy()
        "rbnf/zh_HK.res" -> RbnfZhHKResourceData.copy()
        "rbnf/zh_Hant.res" -> RbnfZhHantResourceData.copy()
        "rbnf/zh_Hant_HK.res" -> RbnfZhHantHKResourceData.copy()
        "rbnf/zh_Hant_MO.res" -> RbnfZhHantMOResourceData.copy()
        "rbnf/zh_Hant_TW.res" -> RbnfZhHantTWResourceData.copy()
        "rbnf/zh_MO.res" -> RbnfZhMOResourceData.copy()
        "rbnf/zh_TW.res" -> RbnfZhTWResourceData.copy()
        else -> null
    }
}
