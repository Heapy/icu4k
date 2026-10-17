// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2016, Google, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.calendar


/** DateTimePatternGenerator's field classification and quote-aware parser. */
internal object DateTimePatternSyntaxCore {
    const val ERA=0; const val YEAR=1; const val QUARTER=2; const val MONTH=3
    const val WEEK_OF_YEAR=4; const val WEEK_OF_MONTH=5; const val WEEKDAY=6; const val DAY=7
    const val DAY_OF_YEAR=8; const val DAY_OF_WEEK_IN_MONTH=9; const val DAYPERIOD=10; const val HOUR=11
    const val MINUTE=12; const val SECOND=13; const val FRACTIONAL_SECOND=14; const val ZONE=15; const val TYPE_LIMIT=16
    const val DATE_MASK=(1 shl DAYPERIOD)-1; const val TIME_MASK=(1 shl TYPE_LIMIT)-1-DATE_MASK
    const val DELTA=0x10; const val NUMERIC=0x100; const val NONE=0
    const val NARROW=-0x101; const val SHORTER=-0x102; const val SHORT=-0x103; const val LONG=-0x104
    const val EXTRA_FIELD=0x10000; const val MISSING_FIELD=0x1000
    val CANONICAL_ITEMS=arrayOf("G","y","Q","M","w","W","E","d","D","F","a","H","m","s","S","v")
    val FIELD_NAME=arrayOf("Era","Year","Quarter","Month","Week_in_Year","Week_in_Month","Weekday","Day","Day_Of_Year","Day_of_Week_in_Month","Dayperiod","Hour","Minute","Second","Fractional_Second","Zone")
    fun getCanonicalIndex(value: String?,strict: Boolean): Int {
        val text=value!!
        if(text.isEmpty()) return -1
        val ch=text[0].code
        if(text.any { it.code!=ch }) return -1
        var best=-1
        for(i in types.indices) {
            val row=types[i]
            if(row[0]!=ch) continue
            best=i
            if(row[3]>text.length || row.last()<text.length) continue
            return i
        }
        return if(strict) -1 else best
    }
    fun getCanonicalChar(field: Int,reference: Char): Char {
        if(reference=='h' || reference=='K') return 'h'
        for(row in types) if(row[1]==field) return row[0].toChar()
        throw IllegalArgumentException("Could not find field $field")
    }
    fun getName(value: String?): String {
        val index=getCanonicalIndex(value,true)
        if(index !in types.indices) throw IndexOutOfBoundsException()
        val row=types[index]
        return FIELD_NAME[row[1]]+if(row[2]<0) ":S" else ":N"
    }
    fun showMask(mask: Int): String=buildString {
        for(i in 0 until TYPE_LIMIT) if(mask and (1 shl i)!=0) {
            if(isNotEmpty()) append(" | ")
            append(FIELD_NAME[i]).append(' ')
        }
    }
    val types=arrayOf(
        intArrayOf('G'.code, ERA, SHORT, 1, 3),
        intArrayOf('G'.code, ERA, LONG, 4),
        intArrayOf('G'.code, ERA, NARROW, 5),
        intArrayOf('y'.code, YEAR, NUMERIC, 1, 20),
        intArrayOf('Y'.code, YEAR, NUMERIC + DELTA, 1, 20),
        intArrayOf('u'.code, YEAR, NUMERIC + 2*DELTA, 1, 20),
        intArrayOf('r'.code, YEAR, NUMERIC + 3*DELTA, 1, 20),
        intArrayOf('U'.code, YEAR, SHORT, 1, 3),
        intArrayOf('U'.code, YEAR, LONG, 4),
        intArrayOf('U'.code, YEAR, NARROW, 5),
        intArrayOf('Q'.code, QUARTER, NUMERIC, 1, 2),
        intArrayOf('Q'.code, QUARTER, SHORT, 3),
        intArrayOf('Q'.code, QUARTER, LONG, 4),
        intArrayOf('Q'.code, QUARTER, NARROW, 5),
        intArrayOf('q'.code, QUARTER, NUMERIC + DELTA, 1, 2),
        intArrayOf('q'.code, QUARTER, SHORT - DELTA, 3),
        intArrayOf('q'.code, QUARTER, LONG - DELTA, 4),
        intArrayOf('q'.code, QUARTER, NARROW - DELTA, 5),
        intArrayOf('M'.code, MONTH, NUMERIC, 1, 2),
        intArrayOf('M'.code, MONTH, SHORT, 3),
        intArrayOf('M'.code, MONTH, LONG, 4),
        intArrayOf('M'.code, MONTH, NARROW, 5),
        intArrayOf('L'.code, MONTH, NUMERIC + DELTA, 1, 2),
        intArrayOf('L'.code, MONTH, SHORT - DELTA, 3),
        intArrayOf('L'.code, MONTH, LONG - DELTA, 4),
        intArrayOf('L'.code, MONTH, NARROW - DELTA, 5),
        intArrayOf('l'.code, MONTH, NUMERIC + DELTA, 1, 1),
        intArrayOf('w'.code, WEEK_OF_YEAR, NUMERIC, 1, 2),
        intArrayOf('W'.code, WEEK_OF_MONTH, NUMERIC, 1),
        intArrayOf('E'.code, WEEKDAY, SHORT, 1, 3),
        intArrayOf('E'.code, WEEKDAY, LONG, 4),
        intArrayOf('E'.code, WEEKDAY, NARROW, 5),
        intArrayOf('E'.code, WEEKDAY, SHORTER, 6),
        intArrayOf('c'.code, WEEKDAY, NUMERIC + 2*DELTA, 1, 2),
        intArrayOf('c'.code, WEEKDAY, SHORT - 2*DELTA, 3),
        intArrayOf('c'.code, WEEKDAY, LONG - 2*DELTA, 4),
        intArrayOf('c'.code, WEEKDAY, NARROW - 2*DELTA, 5),
        intArrayOf('c'.code, WEEKDAY, SHORTER - 2*DELTA, 6),
        intArrayOf('e'.code, WEEKDAY, NUMERIC + DELTA, 1, 2),
        intArrayOf('e'.code, WEEKDAY, SHORT - DELTA, 3),
        intArrayOf('e'.code, WEEKDAY, LONG - DELTA, 4),
        intArrayOf('e'.code, WEEKDAY, NARROW - DELTA, 5),
        intArrayOf('e'.code, WEEKDAY, SHORTER - DELTA, 6),
        intArrayOf('d'.code, DAY, NUMERIC, 1, 2),
        intArrayOf('g'.code, DAY, NUMERIC + DELTA, 1, 20),
        intArrayOf('D'.code, DAY_OF_YEAR, NUMERIC, 1, 3),
        intArrayOf('F'.code, DAY_OF_WEEK_IN_MONTH, NUMERIC, 1),
        intArrayOf('a'.code, DAYPERIOD, SHORT, 1, 3),
        intArrayOf('a'.code, DAYPERIOD, LONG, 4),
        intArrayOf('a'.code, DAYPERIOD, NARROW, 5),
        intArrayOf('b'.code, DAYPERIOD, SHORT - DELTA, 1, 3),
        intArrayOf('b'.code, DAYPERIOD, LONG - DELTA, 4),
        intArrayOf('b'.code, DAYPERIOD, NARROW - DELTA, 5),
        intArrayOf('B'.code, DAYPERIOD, SHORT - 3*DELTA, 1, 3),
        intArrayOf('B'.code, DAYPERIOD, LONG - 3*DELTA, 4),
        intArrayOf('B'.code, DAYPERIOD, NARROW - 3*DELTA, 5),
        intArrayOf('H'.code, HOUR, NUMERIC + 10*DELTA, 1, 2),
        intArrayOf('k'.code, HOUR, NUMERIC + 11*DELTA, 1, 2),
        intArrayOf('h'.code, HOUR, NUMERIC, 1, 2),
        intArrayOf('K'.code, HOUR, NUMERIC + DELTA, 1, 2),
        intArrayOf('m'.code, MINUTE, NUMERIC, 1, 2),
        intArrayOf('s'.code, SECOND, NUMERIC, 1, 2),
        intArrayOf('A'.code, SECOND, NUMERIC + DELTA, 1, 1000),
        intArrayOf('S'.code, FRACTIONAL_SECOND, NUMERIC, 1, 1000),
        intArrayOf('v'.code, ZONE, SHORT - 2*DELTA, 1),
        intArrayOf('v'.code, ZONE, LONG - 2*DELTA, 4),
        intArrayOf('z'.code, ZONE, SHORT, 1, 3),
        intArrayOf('z'.code, ZONE, LONG, 4),
        intArrayOf('Z'.code, ZONE, NARROW - DELTA, 1, 3),
        intArrayOf('Z'.code, ZONE, LONG - DELTA, 4),
        intArrayOf('Z'.code, ZONE, SHORT - DELTA, 5),
        intArrayOf('O'.code, ZONE, SHORT - DELTA, 1),
        intArrayOf('O'.code, ZONE, LONG - DELTA, 4),
        intArrayOf('V'.code, ZONE, SHORT - DELTA, 1),
        intArrayOf('V'.code, ZONE, LONG - DELTA, 2),
        intArrayOf('V'.code, ZONE, LONG-1 - DELTA, 3),
        intArrayOf('V'.code, ZONE, LONG-2 - DELTA, 4),
        intArrayOf('X'.code, ZONE, NARROW - DELTA, 1),
        intArrayOf('X'.code, ZONE, SHORT - DELTA, 2),
        intArrayOf('X'.code, ZONE, LONG - DELTA, 4),
        intArrayOf('x'.code, ZONE, NARROW - DELTA, 1),
        intArrayOf('x'.code, ZONE, SHORT - DELTA, 2),
        intArrayOf('x'.code, ZONE, LONG - DELTA, 4),
    )
}
