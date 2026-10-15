// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.impl.Trie2_32
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import io.github.heapy.icu4k.util.VersionInfo

/** Data/settings container, mutable only while built or deserialized. */
internal class CollationTailoring(baseSettings: SharedObject.Reference<CollationSettings>?) {
    var data: CollationData?=null
    var settings: SharedObject.Reference<CollationSettings>?
    private var rules: String?=null
    private var rulesResource: UResourceBundle?=null
    var actualLocale: ULocale?=ULocale.ROOT
    var version=0
    var ownedData: CollationData?=null
    var trie: Trie2_32?=null
    var unsafeBackwardSet: UnicodeSet?=null
    var maxExpansions: MutableMap<Int,Int>?=null
    internal val maxExpansionsLock = createAvailabilityLock()
    init {
        settings=if (baseSettings != null) {
            checkAssertion { baseSettings.readOnly()!!.reorderCodes!!.isEmpty() }
            checkAssertion { baseSettings.readOnly()!!.reorderTable == null }
            checkAssertion { baseSettings.readOnly()!!.minHighNoReorder == 0L }
            baseSettings.clone()
        } else SharedObject.Reference(CollationSettings())
    }
    fun ensureOwnedData() { if (ownedData == null) ownedData=CollationData(Norm2AllModes.getNFCInstance().impl); data=ownedData }
    fun setRules(r: String?) { checkAssertion { rules == null && rulesResource == null }; rules=r }
    fun setRulesResource(resource: UResourceBundle?) { checkAssertion { rules == null && rulesResource == null }; rulesResource=resource }
    fun getRules(): String { if (rules != null) return rules!!; if (rulesResource != null) return rulesResource!!.getString(); return "" }
    fun setVersion(baseVersion: Int,rulesVersion: Int) {
        val r=(rulesVersion shr 16) and 0xff00;val s=(rulesVersion shr 16) and 0xff;val t=(rulesVersion shr 8) and 0xff;val q=rulesVersion and 0xff
        version=(VersionInfo.UCOL_BUILDER_VERSION.major shl 24) or (baseVersion and 0xffc000) or ((r+(r shr 6)) and 0x3f00) or (((s shl 3)+(s shr 5)+t+(q shl 4)+(q shr 4)) and 0xff)
    }
    fun getUCAVersion(): Int = ((version shr 12) and 0xff0) or ((version shr 14) and 3)
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
    companion object {
        private val assertionsEnabled=collationIteratorClassAssertionsEnabled(CollationTailoring::class)
        fun makeBaseVersion(ucaVersion: VersionInfo?): VersionInfo = VersionInfo.getInstance(VersionInfo.UCOL_BUILDER_VERSION.major,(ucaVersion!!.major shl 3)+ucaVersion.minor,ucaVersion.milli shl 6,0)
    }
}
