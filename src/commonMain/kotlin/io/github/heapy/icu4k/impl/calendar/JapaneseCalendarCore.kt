// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2014, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

/** JapaneseCalendar era-specific overrides of the Gregorian framework. */
internal class JapaneseCalendarCore(private val rules: EraRulesCore,private val currentEra: Int,private val owner: Owner) {
    interface Owner {
        fun newerField(defaultField: Int,alternateField: Int): Int
        fun internalGet(field: Int): Int
        fun internalGet(field: Int,defaultValue: Int): Int
        fun internalSet(field: Int,value: Int)
        fun gregorianComputeFields(julianDay: Int)
        fun baseDefaultMonth(year: Int): Int
        fun baseDefaultDay(year: Int,month: Int): Int
        fun baseGetLimit(field: Int,type: Int): Int
        fun baseGetActualMaximum(field: Int): Int
        fun get(field: Int): Int
        fun handleGetLimit(field: Int,type: Int): Int
    }
    fun handleGetExtendedYear(): Int {
        if(owner.newerField(19,1)==19 && owner.newerField(19,0)==19) return owner.internalGet(19,1970)
        return owner.internalGet(1,1)+rules.getStartYear(owner.internalGet(0,currentEra))-1
    }
    fun getDefaultMonthInYear(year: Int): Int {
        val date=rules.getStartDate(owner.internalGet(0,currentEra),null)
        return if(year==date[0]) date[1]-1 else owner.baseDefaultMonth(year)
    }
    fun getDefaultDayInMonth(year: Int,month: Int): Int {
        val date=rules.getStartDate(owner.internalGet(0,currentEra),null)
        return if(year==date[0] && month==date[1]-1) date[2] else owner.baseDefaultDay(year,month)
    }
    fun handleComputeFields(julianDay: Int) {
        owner.gregorianComputeFields(julianDay)
        val year=owner.internalGet(19)
        val era=rules.getEraCode(year,owner.internalGet(2)+1,owner.internalGet(5))
        owner.internalSet(0,era)
        owner.internalSet(1,year-rules.getStartYear(era)+1)
    }
    fun handleGetLimit(field: Int,type: Int): Int {
        if(field==0) return if(type==0 || type==1) 0 else rules.getMaxEraCode()
        if(field==1) when(type) {
            0,1,2 -> return 1
            3 -> return owner.baseGetLimit(field,3)-rules.getStartYear(currentEra)
        }
        return owner.baseGetLimit(field,type)
    }
    fun getActualMaximum(field: Int): Int {
        if(field!=1) return owner.baseGetActualMaximum(field)
        val era=owner.get(0)
        if(era==rules.getMaxEraCode()) return owner.handleGetLimit(1,3)
        val next=rules.getStartDate(era+1,null)
        var year=next[0]-rules.getStartYear(era)+1
        if(next[1]==1 && next[2]==1) year--
        return year
    }
    fun getType(): String="japanese"
    fun haveDefaultCentury(): Boolean=false
    fun isEra0CountingBackward(): Boolean=false
    companion object {
        interface Configuration {
            fun systemProperty(name: String): String?
            fun environmentVariable(name: String): String?
        }
        fun enableTentativeEra(host: Configuration): Boolean {
            val property=host.systemProperty("ICU_ENABLE_TENTATIVE_ERA")
            val value=property ?: host.environmentVariable("ICU_ENABLE_TENTATIVE_ERA")
            if(value!=null) return value.equals("true",ignoreCase=true)
            return host.systemProperty("jdk.calendar.japanese.supplemental.era")!=null
        }
    }
}
