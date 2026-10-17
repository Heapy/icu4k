// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.Calendar

/** The source survey-tool interval algorithm, including its pattern-index/level comparison. */
internal class SimpleDateFormatIntervalCore(private val owner: Owner) {
    interface Owner {
        val pattern: String?
        val useFastFormat: Boolean
        fun compiledPatternItems(): Array<Any>
        fun capitalizationContext(): DisplayContext?
        fun format(cal: Calendar?, output: StringBuilder?, pos: FormatFieldPosition?): StringBuilder
        fun subFormat(buf: StringBuilder?, ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?)
        fun subFormat(ch: Char, count: Int, begin: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?): String?
    }
    fun format(from: Calendar?, to: Calendar?, output: StringBuilder?, pos: FormatFieldPosition?): StringBuilder {
        if (!from!!.isEquivalentTo(to)) throw IllegalArgumentException("can not format on two different calendars")
        val items = owner.compiledPatternItems()
        var begin = -1
        var end = -1
        try {
            for (i in items.indices) if (different(from,to,items[i])) { begin = i; break }
            if (begin == -1) return owner.format(from,output,pos)
            for (i in items.lastIndex downTo begin) if (different(from,to,items[i])) { end = i; break }
        } catch (e: IllegalArgumentException) { throw IllegalArgumentException(exceptionString(e)) }
        fun full(): StringBuilder {
            owner.format(from,output,pos)
            output!!.append(" – ")
            owner.format(to,output,pos)
            return output
        }
        if (begin == 0 && end == items.lastIndex) return full()
        var highest = 1000
        for (i in begin..end) {
            val item = items[i]
            if (item is String) continue
            item as SimpleDateFormatPatternCore.PatternItem
            val index = SimpleDateFormatPatternCore.index(item.type)
            if (index == -1) illegal(item.type)
            // This is the pattern index, not the calendar-field level, in ICU4J.
            if (index < highest) highest = index
        }
        try {
            for (i in 0 until begin) if (lower(items[i],highest)) { begin = i; break }
            for (i in items.lastIndex downTo end + 1) if (lower(items[i],highest)) { end = i; break }
        } catch (e: IllegalArgumentException) { throw IllegalArgumentException(exceptionString(e)) }
        if (begin == 0 && end == items.lastIndex) return full()
        pos!!.setBeginIndex(0); pos.setEndIndex(0)
        val context = owner.capitalizationContext()
        fun append(item: Any, index: Int, cal: Calendar?) {
            if (item is String) output!!.append(item)
            else {
                item as SimpleDateFormatPatternCore.PatternItem
                if (owner.useFastFormat) owner.subFormat(output,item.type,item.length,output!!.length,index,context,pos,item.type,cal)
                else output!!.append(owner.subFormat(item.type,item.length,output.length,index,context,pos,item.type,cal))
            }
        }
        for (i in 0..end) append(items[i],i,from)
        output!!.append(" – ")
        for (i in begin until items.size) append(items[i],i,to)
        return output
    }
    private fun different(from: Calendar, to: Calendar?, item: Any): Boolean {
        if (item is String) return false
        item as SimpleDateFormatPatternCore.PatternItem
        val index = SimpleDateFormatPatternCore.index(item.type)
        if (index == -1) illegal(item.type)
        val field = SimpleDateFormatPatternCore.calendarField(index)
        return field >= 0 && from.get(field) != to!!.get(field)
    }
    private fun lower(item: Any, level: Int): Boolean {
        if (item is String) return false
        item as SimpleDateFormatPatternCore.PatternItem
        val itemLevel = SimpleDateFormatPatternCore.level(item.type)
        if (itemLevel == -1) illegal(item.type)
        return itemLevel >= level
    }
    private fun illegal(ch: Char): Nothing = throw IllegalArgumentException("Illegal pattern character '$ch' in \"${owner.pattern}\"")
    private fun exceptionString(error: IllegalArgumentException): String =
        if (error::class == IllegalArgumentException::class) "java.lang.IllegalArgumentException" + (error.message?.let { ": $it" } ?: "") else error.toString()
}
