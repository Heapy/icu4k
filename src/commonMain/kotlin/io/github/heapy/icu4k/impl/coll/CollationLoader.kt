// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.createAvailabilityLock
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.concurrent.Volatile

/** Locale/type fallback and binary tailoring loading from the collation resource tree. */
internal object CollationLoader {
    @Volatile private var rootRules: String?=null
    private val lock=createAvailabilityLock()
    private fun loadRootRules() {
        if(rootRules!=null) return
        lock.withLock {
            if(rootRules==null) rootRules=UResourceBundle.getBundleInstance(ICUData.ICU_COLLATION_BASE_NAME,ULocale.ROOT).getString("UCARules")
        }
    }
    fun getRootRules(): String { loadRootRules();return rootRules!! }
    private object ASCII {
        fun toLowerCase(s: String?): String {
            for(i in 0 until s!!.length) if(s[i] in 'A'..'Z') {
                return buildString(s.length) {
                    append(s,0,i);append((s[i].code+32).toChar())
                    for(j in i+1 until s.length) { val c=s[j];append(if(c in 'A'..'Z') (c.code+32).toChar() else c) }
                }
            }
            return s
        }
    }
    fun loadRules(locale: ULocale?,collationType: String?): String {
        val bundle=UResourceBundle.getBundleInstance(ICUData.ICU_COLLATION_BASE_NAME,locale)
        return (bundle as ICUResourceBundle).getWithFallback("collations/"+ASCII.toLowerCase(collationType)).getString("Sequence")
    }
    private fun findWithFallback(table: UResourceBundle?,entryName: String): UResourceBundle? = (table as ICUResourceBundle).findWithFallback(entryName)
    fun loadTailoring(locale: ULocale?,outValidLocale: Output<ULocale>?): CollationTailoring {
        val root=CollationRoot.getRoot();val localeName=locale!!.getName()
        if(localeName.isEmpty() || localeName=="root") { outValidLocale!!.value=ULocale.ROOT;return root }
        val bundle: UResourceBundle
        try { bundle=ICUResourceBundle.getBundleInstance(ICUData.ICU_COLLATION_BASE_NAME,locale,ICUResourceBundle.OpenType.LOCALE_ROOT) }
        catch(e: MissingResourceException) { outValidLocale!!.value=ULocale.ROOT;return root }
        var validLocale=bundle.getULocale();val validName=validLocale.getName()
        if(validName.isEmpty() || validName=="root") validLocale=ULocale.ROOT
        outValidLocale!!.value=validLocale
        val collations: UResourceBundle
        try { collations=bundle.get("collations") }
        catch(e: MissingResourceException) { return root }
        var type=locale.getKeywordValue("collation");var defaultType="standard"
        var defT=(collations as ICUResourceBundle).findStringWithFallback("default")
        if(defT!=null) defaultType=defT
        type=if(type==null || type=="default") defaultType else ASCII.toLowerCase(type)
        var data=findWithFallback(collations,type)
        if(data==null && type.length>6 && type.startsWith("search")) { type="search";data=findWithFallback(collations,type) }
        if(data==null && type!=defaultType) { type=defaultType;data=findWithFallback(collations,type) }
        if(data==null && type!="standard") { type="standard";data=findWithFallback(collations,type) }
        if(data==null) return root
        var actualLocale=data.getULocale();val actualName=actualLocale.getName()
        if(actualName.isEmpty() || actualName=="root") { actualLocale=ULocale.ROOT;if(type=="standard") return root }
        val t=CollationTailoring(root.settings);t.actualLocale=actualLocale
        val binary=data.get("%%CollationBin");val inBytes=binary.getBinary()
        try { CollationDataReader.read(root,inBytes,t) }
        catch(e: ICUUncheckedIOException) { throw ICUUncheckedIOException("Failed to load collation tailoring data for locale:$actualLocale type:$type",e) }
        try { t.setRulesResource(data.get("Sequence")) } catch(e: MissingResourceException) { }
        if(type!=defaultType) outValidLocale.value=validLocale.setKeywordValue("collation",type)
        if(actualLocale!=validLocale) {
            val actualBundle=UResourceBundle.getBundleInstance(ICUData.ICU_COLLATION_BASE_NAME,actualLocale)
            defT=(actualBundle as ICUResourceBundle).findStringWithFallback("collations/default")
            if(defT!=null) defaultType=defT
        }
        if(type!=defaultType) t.actualLocale=t.actualLocale!!.setKeywordValue("collation",type)
        return t
    }
}
