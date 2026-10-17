// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.number.NumberFormatter
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.ULocale

/** MessageFormat's complete explicit-style dispatch; date constructors remain owned by the facade. */
internal class MessageFormatStyleCore(private val owner: Owner) {
    interface Owner {
        val locale: ULocale?
        fun dateStyle(date: Boolean, style: Int, locale: ULocale?): Any?
        fun dateSkeleton(skeleton: String, locale: ULocale?): Any?
        fun datePattern(pattern: String, locale: ULocale?): Any?
    }
    fun dateTimeFormatForPatternOrSkeleton(style: String?): Any? {
        val text = style!!
        val index = PatternProps.skipWhiteSpace(text,0)
        return if (text.regionMatches(index,"::",0,2)) owner.dateSkeleton(text.substring(index + 2),owner.locale)
            else owner.datePattern(text,owner.locale)
    }
    fun create(type: String?, style: String?): Any? {
        val typeIndex = findKeyword(type,TYPES)
        return when (typeIndex) {
        0 -> when (findKeyword(style,NUMBER_MODIFIERS)) {
            0 -> NumberFormat.getInstance(owner.locale)
            1 -> NumberFormat.getCurrencyInstance(owner.locale)
            2 -> NumberFormat.getPercentInstance(owner.locale)
            3 -> NumberFormat.getIntegerInstance(owner.locale)
            else -> {
                val index = PatternProps.skipWhiteSpace(style!!,0)
                if (style.regionMatches(index,"::",0,2)) NumberFormatter.forSkeleton(style.substring(index + 2)).locale(owner.locale).toFormat()
                else DecimalFormat(style,DecimalFormatSymbols(owner.locale))
            }
        }
        1, 2 -> {
            val date = typeIndex == 1
            when (findKeyword(style,DATE_MODIFIERS)) {
                0, 2 -> owner.dateStyle(date,DateFormat.DEFAULT,owner.locale)
                1 -> owner.dateStyle(date,DateFormat.SHORT,owner.locale)
                3 -> owner.dateStyle(date,DateFormat.LONG,owner.locale)
                4 -> owner.dateStyle(date,DateFormat.FULL,owner.locale)
                else -> dateTimeFormatForPatternOrSkeleton(style)
            }
        }
        3, 4, 5 -> {
            val kind = when (typeIndex) { 3 -> RuleBasedNumberFormat.SPELLOUT; 4 -> RuleBasedNumberFormat.ORDINAL; else -> RuleBasedNumberFormat.DURATION }
            val format = RuleBasedNumberFormat(owner.locale,kind)
            // Java String.trim(), not Unicode/PatternProps trimming, for a rule-set name.
            val rules = style!!.trim { it <= ' ' }
            if (rules.isNotEmpty()) try { format.setDefaultRuleSet(rules) } catch (_: Exception) { }
            format
        }
        else -> throw IllegalArgumentException("Unknown format type \"$type\"")
    }
    }
    companion object {
        private val TYPES = arrayOf("number","date","time","spellout","ordinal","duration")
        private val NUMBER_MODIFIERS = arrayOf("","currency","percent","integer")
        private val DATE_MODIFIERS = arrayOf("","short","medium","long","full")
        fun findKeyword(value: String?, choices: Array<String>): Int {
            val key = PatternProps.trimWhiteSpace(value!!).toString().lowercase()
            return choices.indexOf(key)
        }
    }
}
