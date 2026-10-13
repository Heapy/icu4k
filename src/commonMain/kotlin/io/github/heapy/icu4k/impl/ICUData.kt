// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2004-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

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

    private val resourceNames = setOf("uts46.nrm", "nfkc_scf.nrm", "nfkc_cf.nrm", "nfkc.nrm", "nfc.nrm", "uemoji.icu", "pnames.icu", "ubidi.icu", "ucase.icu", "ulayout.icu", "uprops.icu", "brkitr/char.brk", "brkitr/line.brk", "brkitr/line_cj.brk", "brkitr/line_loose.brk", "brkitr/line_loose_cj.brk", "brkitr/line_loose_phrase_cj.brk", "brkitr/line_normal.brk", "brkitr/line_normal_cj.brk", "brkitr/line_normal_phrase_cj.brk", "brkitr/line_phrase_cj.brk", "brkitr/sent.brk", "brkitr/sent_el.brk", "brkitr/title.brk", "brkitr/word.brk", "brkitr/word_POSIX.brk", "brkitr/burmesedict.dict", "brkitr/cjdict.dict", "brkitr/khmerdict.dict", "brkitr/laodict.dict", "brkitr/thaidict.dict")
    private fun itemPath(name: String): String = name.removePrefix("/").removePrefix("$ICU_BASE_NAME/")
    fun exists(resourceName: String): Boolean = itemPath(resourceName) in resourceNames

    /** Returns a private copy, so callers cannot mutate cached resource data. */
    fun getBytes(resourceName: String): ByteArray? = when (itemPath(resourceName)) {
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
