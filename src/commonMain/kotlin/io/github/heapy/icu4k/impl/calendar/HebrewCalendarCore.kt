// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.CalendarCache

/** HebrewCalendar's lunisolar arithmetic, discontinuous month operations and Temporal policy. */
internal class HebrewCalendarCore(private val owner: Owner) {
    interface Owner {
        fun get(field: Int): Int
        fun set(field: Int,value: Int)
        fun pinField(field: Int)
        fun baseAdd(field: Int,amount: Int)
        fun baseRoll(field: Int,amount: Int)
        fun handleGetYearLength(year: Int): Int
        fun handleGetExtendedYear(): Int
        fun internalGetMonth(): Int
        fun baseInternalGetMonth(): Int
        fun baseValidateField(field: Int)
        fun resolveMonthField(): Int
        fun internalGet(field: Int): Int
        fun internalGet(field: Int,defaultValue: Int): Int
        fun newerField(defaultField: Int,alternateField: Int): Int
        fun internalSet(field: Int,value: Int)
    }
    fun add(field: Int,amount: Int) {
        if(field!=2 && field!=23) { owner.baseAdd(field,amount); return }
        var month=owner.get(2); var year=owner.get(1)
        var across: Boolean
        if(amount>0) {
            across=month<5; month+=amount
            while(true) {
                if(across && month>=5 && !isLeapYear(year)) month++
                if(month<=12) break
                month-=13; year++; across=true
            }
        } else {
            across=month>5; month+=amount
            while(true) {
                if(across && month<=5 && !isLeapYear(year)) month--
                if(month>=0) break
                month+=13; year--; across=true
            }
        }
        owner.set(2,month); owner.set(1,year); owner.pinField(5)
    }
    fun roll(field: Int,amount: Int) {
        if(field!=2 && field!=23) { owner.baseRoll(field,amount); return }
        val month=owner.get(2); val year=owner.get(1)
        val leap=isLeapYear(year)
        var next=month+amount%monthsInYear(year)
        if(!leap) {
            if(amount>0 && month<5 && next>=5) next++
            else if(amount<0 && month>5 && next<=5) next--
        }
        owner.set(2,(next+13)%13); owner.pinField(5)
    }
    private fun yearType(year: Int): Int {
        var length=owner.handleGetYearLength(year)
        if(length>380) length-=30
        return when(length) {
            353 -> 0; 354 -> 1; 355 -> 2
            else -> throw IllegalArgumentException("Illegal year length $length in year $year")
        }
    }
    fun handleGetLimit(field: Int,type: Int): Int=at(LIMITS,field,type)
    fun handleGetMonthLength(extendedYear: Int,month: Int): Int {
        val normalized=normalize(extendedYear,month)
        val m=normalized[1]
        return at(MONTH_LENGTH,m,if(m==1 || m==2) yearType(normalized[0]) else 0)
    }
    fun handleGetYearLength(year: Int): Int=(startOfYear(year+1)-startOfYear(year)).toInt()
    fun validateField(field: Int) {
        if((field==2 || field==23) && !isLeapYear(owner.handleGetExtendedYear()) && owner.internalGetMonth()==5)
            throw IllegalArgumentException("MONTH cannot be ADAR_1(5) except leap years")
        owner.baseValidateField(field)
    }
    fun handleComputeFields(julianDay: Int) {
        val d=(julianDay-347997).toLong()
        val months=CalendarDateCore.floorDivide(d*25920L,765433L)
        var year=(CalendarDateCore.floorDivide(19*months+234,235L)+1).toInt()
        var dayOfYear=(d-startOfYear(year)).toInt()
        while(dayOfYear<1) { year--; dayOfYear=(d-startOfYear(year)).toInt() }
        val type=yearType(year); val leap=isLeapYear(year)
        val starts=if(leap) LEAP_MONTH_START else MONTH_START
        var month=0
        while(dayOfYear>at(starts,month,type)) month++
        month--
        val day=dayOfYear-at(starts,month,type)
        owner.internalSet(0,0); owner.internalSet(1,year); owner.internalSet(19,year)
        var ordinal=month
        if(!leap && ordinal>5) ordinal--
        owner.internalSet(23,ordinal); owner.internalSet(2,month); owner.internalSet(5,day); owner.internalSet(6,dayOfYear)
    }
    fun handleGetExtendedYear(): Int=if(owner.newerField(19,1)==19) owner.internalGet(19,1) else owner.internalGet(1,1)
    @Suppress("UNUSED_PARAMETER")
    fun handleComputeMonthStart(extendedYear: Int,month: Int,useMonth: Boolean): Int {
        val normalized=normalize(extendedYear,month); val year=normalized[0]; val m=normalized[1]
        var day=startOfYear(year)
        if(m!=0) day+=at(if(isLeapYear(year)) LEAP_MONTH_START else MONTH_START,m,yearType(year))
        return (day+347997).toInt()
    }
    fun getType(): String="hebrew"
    fun getRelatedYearDifference(): Int=-3760
    fun inTemporalLeapYear(): Boolean=isLeapYear(owner.get(19))
    fun getTemporalMonthCode(): String {
        val month=owner.get(2)
        if(month !in MONTH_CODES.indices) throw IndexOutOfBoundsException()
        return MONTH_CODES[month]
    }
    fun setTemporalMonthCode(code: String?) {
        val value=code!!
        if(value.length==3 || value.length==4) for(month in MONTH_CODES.indices) if(value==MONTH_CODES[month]) { owner.set(2,month); return }
        throw IllegalArgumentException("Incorrect temporal Month code: $value")
    }
    fun internalGetMonth(): Int {
        if(owner.resolveMonthField()==23) {
            val ordinal=owner.internalGet(23)
            val year=owner.handleGetExtendedYear()
            return ordinal+if(!isLeapYear(year) && ordinal>5) 1 else 0
        }
        return owner.baseInternalGetMonth()
    }
    companion object {
        private var cache=CalendarCache()
        fun isLeapYear(year: Int): Boolean { val x=(year*12+17)%19; return x>=if(x<0) -7 else 12 }
        private fun monthsInYear(year: Int): Int=if(isLeapYear(year)) 13 else 12
        private fun normalize(extendedYear: Int,month: Int): IntArray {
            var year=extendedYear; var m=month
            if(m<= -235 || m>=235) { year+=(m/235)*19; m%=235 }
            while(m<0) { --year; m+=monthsInYear(year) }
            while(m>12) { m-=monthsInYear(year); year++ }
            return intArrayOf(year,m)
        }
        fun startOfYear(year: Int): Long {
            var day=cache.get(year.toLong())
            if(day==CalendarCache.EMPTY) {
                val months=CalendarDateCore.floorDivide(235L*year-234,19L).toInt()
                var fraction=months*13753L+12084
                day=(months*29).toLong()+fraction/25920
                fraction%=25920
                var weekday=(day%7).toInt()
                if(weekday==2 || weekday==4 || weekday==6) { day++; weekday=(day%7).toInt() }
                else if(weekday==1 && fraction>16404 && !isLeapYear(year)) day+=2
                else if(weekday==0 && fraction>23269 && isLeapYear(year-1)) day++
                cache.put(year.toLong(),day)
            }
            return day
        }
        private fun at(table: Array<IntArray>,row: Int,column: Int): Int {
            if(row !in table.indices || column !in table[row].indices) throw IndexOutOfBoundsException()
            return table[row][column]
        }
        private val MONTH_CODES=arrayOf("M01","M02","M03","M04","M05","M05L","M06","M07","M08","M09","M10","M11","M12")
        private val LIMITS=arrayOf(
            intArrayOf(0,0,0,0),
            intArrayOf(-5000000,-5000000,5000000,5000000),
            intArrayOf(0,0,12,12),
            intArrayOf(1,1,51,56),
            intArrayOf(),
            intArrayOf(1,1,29,30),
            intArrayOf(1,1,353,385),
            intArrayOf(),
            intArrayOf(-1,-1,5,5),
            intArrayOf(),
            intArrayOf(),
            intArrayOf(),
            intArrayOf(),
            intArrayOf(),
            intArrayOf(),
            intArrayOf(),
            intArrayOf(),
            intArrayOf(-5000000,-5000000,5000000,5000000),
            intArrayOf(),
            intArrayOf(-5000000,-5000000,5000000,5000000),
            intArrayOf(),
            intArrayOf(),
            intArrayOf(),
            intArrayOf(0,0,11,12),
        )
        private val MONTH_LENGTH=arrayOf(
            intArrayOf(30,30,30),
            intArrayOf(29,29,30),
            intArrayOf(29,30,30),
            intArrayOf(29,29,29),
            intArrayOf(30,30,30),
            intArrayOf(30,30,30),
            intArrayOf(29,29,29),
            intArrayOf(30,30,30),
            intArrayOf(29,29,29),
            intArrayOf(30,30,30),
            intArrayOf(29,29,29),
            intArrayOf(30,30,30),
            intArrayOf(29,29,29),
        )
        private val MONTH_START=arrayOf(
            intArrayOf(0,0,0),
            intArrayOf(30,30,30),
            intArrayOf(59,59,60),
            intArrayOf(88,89,90),
            intArrayOf(117,118,119),
            intArrayOf(147,148,149),
            intArrayOf(147,148,149),
            intArrayOf(176,177,178),
            intArrayOf(206,207,208),
            intArrayOf(235,236,237),
            intArrayOf(265,266,267),
            intArrayOf(294,295,296),
            intArrayOf(324,325,326),
            intArrayOf(353,354,355),
        )
        private val LEAP_MONTH_START=arrayOf(
            intArrayOf(0,0,0),
            intArrayOf(30,30,30),
            intArrayOf(59,59,60),
            intArrayOf(88,89,90),
            intArrayOf(117,118,119),
            intArrayOf(147,148,149),
            intArrayOf(177,178,179),
            intArrayOf(206,207,208),
            intArrayOf(236,237,238),
            intArrayOf(265,266,267),
            intArrayOf(295,296,297),
            intArrayOf(324,325,326),
            intArrayOf(354,355,356),
            intArrayOf(383,384,385),
        )
    }
}
