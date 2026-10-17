// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2016, Google, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.impl.SimpleCache

/** CLDR list-gender rules, including locale fallback and ICU's degenerate list cases. */
@Deprecated("This API is ICU internal only.")
open class GenderInfo(private val style: ListGenderStyle?) {
    enum class Gender { MALE, FEMALE, OTHER }
    enum class ListGenderStyle {
        NEUTRAL, MIXED_NEUTRAL, MALE_TAINTS;
        companion object {
            fun fromName(name: String?): ListGenderStyle = when(name) {
                "neutral"->NEUTRAL
                "mixedNeutral"->MIXED_NEUTRAL
                "maleTaints"->MALE_TAINTS
                else->throw IllegalArgumentException("Unknown gender style name: $name")
            }
        }
    }
    open fun getListGender(vararg genders: Gender?): Gender? = getListGender(genders.asList())
    open fun getListGender(genders: List<Gender?>): Gender? {
        if(genders.size==0)return Gender.OTHER
        if(genders.size==1)return genders[0]
        return when(style!!) {
            ListGenderStyle.NEUTRAL->Gender.OTHER
            ListGenderStyle.MIXED_NEUTRAL->{
                var female=false;var male=false
                for(gender in genders)when(gender!!) {
                    Gender.FEMALE->{if(male)return Gender.OTHER;female=true}
                    Gender.MALE->{if(female)return Gender.OTHER;male=true}
                    Gender.OTHER->return Gender.OTHER
                }
                if(male)Gender.MALE else Gender.FEMALE
            }
            ListGenderStyle.MALE_TAINTS->{
                for(gender in genders)if(gender!=Gender.FEMALE)return Gender.MALE
                Gender.FEMALE
            }
        }
    }
    companion object {
        private val neutral=GenderInfo(ListGenderStyle.NEUTRAL)
        private val cache=SimpleCache<ULocale,GenderInfo>()
        fun getInstance(locale: ULocale): GenderInfo {
            cache.get(locale)?.let{return it}
            val bundle=UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"genderList",ICUResourceBundleReader.embeddedLoader,true)["genderList"]
            val loaded=try {GenderInfo(ListGenderStyle.fromName(bundle.getString(locale.toString())))}catch(_:MissingResourceException){null}
            val result=loaded?:locale.getFallback()?.let{getInstance(it)}?:neutral
            cache.put(locale,result)
            return result
        }
    }
}
