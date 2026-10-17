// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2006-2016, Google, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.UResource
import io.github.heapy.icu4k.impl.PatternTokenizer
import io.github.heapy.icu4k.impl.calendar.*
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.getCanonicalIndex
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.types
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.CANONICAL_ITEMS
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.DATE_MASK
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.TIME_MASK
import io.github.heapy.icu4k.util.*

/** Locale-sensitive date/time skeleton matching and customizable pattern generation. */
open class DateTimePatternGenerator protected constructor() : ICUShallowCloneableBase(), Freezable<DateTimePatternGenerator> {
    private var core: Bridge = Bridge(this)
    enum class DisplayWidth { WIDE, ABBREVIATED, NARROW }
    class PatternInfo {
        @kotlin.jvm.JvmField var status: Int = 0
        @kotlin.jvm.JvmField var conflictingPattern: String? = null
        companion object { const val OK = 0; const val BASE_CONFLICT = 1; const val CONFLICT = 2 }
    }
    open fun getDefaultHourFormatChar(): Char = core.rawHour()
    open fun setDefaultHourFormatChar(value: Char) = core.setDefaultHourFormatChar(value)
    open fun getDefaultHourCycle(): DateFormat.HourCycle = DateFormat.HourCycle.valueOf(core.getDefaultHourCycle().name)
    open fun getBestPattern(skeleton: String?): String = core.getBestPattern(skeleton)
    open fun getBestPattern(skeleton: String?, options: Int): String = core.getBestPattern(skeleton, options)
    open fun addPattern(pattern: String?, override: Boolean, info: PatternInfo?): DateTimePatternGenerator = addPatternWithSkeleton(pattern, null, override, info)
    open fun addPatternWithSkeleton(pattern: String?, skeleton: String?, override: Boolean, info: PatternInfo?): DateTimePatternGenerator {
        core.rawAdd(pattern, skeleton, override, info)
        return this
    }
    open fun getSkeleton(pattern: String?): String = core.getSkeleton(pattern)
    open fun getSkeletonAllowingDuplicates(pattern: String?): String = core.getSkeletonAllowingDuplicates(pattern)
    open fun getCanonicalSkeletonAllowingDuplicates(pattern: String?): String = core.getCanonicalSkeletonAllowingDuplicates(pattern)
    open fun getBaseSkeleton(pattern: String?): String = core.getBaseSkeleton(pattern)
    open fun getSkeletons(result: MutableMap<String, String?>?): MutableMap<String, String?> = core.getSkeletons(result)
    open fun getBaseSkeletons(result: MutableSet<String>?): MutableSet<String> = core.getBaseSkeletons(result)
    open fun replaceFieldTypes(pattern: String?, skeleton: String?): String = replaceFieldTypes(pattern, skeleton, MATCH_NO_OPTIONS)
    open fun replaceFieldTypes(pattern: String?, skeleton: String?, options: Int): String = core.replaceFieldTypes(pattern, skeleton, options)
    open fun setDateTimeFormat(value: String?) = core.setDateTimeFormat(value)
    open fun setDateTimeFormat(style: Int, value: String?) = core.rawSetDateTime(style, value)
    open fun getDateTimeFormat(): String? = core.getDateTimeFormat()
    open fun getDateTimeFormat(style: Int): String? = core.rawGetDateTime(style)
    open fun setDecimal(value: String?) = core.setDecimal(value)
    open fun getDecimal(): String? = core.getDecimal()
    open fun getRedundants(output: MutableCollection<String?>?): MutableCollection<String?> = core.getRedundants(output)
    open fun setAppendItemFormat(field: Int, value: String?) = core.rawSetAppend(field, value)
    open fun getAppendItemFormat(field: Int): String? = core.rawGetAppend(field)
    open fun setAppendItemName(field: Int, value: String?) = core.setAppendItemName(field, value)
    open fun getAppendItemName(field: Int): String? = core.getAppendItemName(field)
    open fun getFieldDisplayName(field: Int, width: DisplayWidth?): String? = core.rawFieldName(field, width?.let { DateTimePatternGeneratorCore.DisplayWidth.entries[it.ordinal] })
    open fun skeletonsAreSimilar(id: String?, skeleton: String?): Boolean = core.skeletonsAreSimilar(id, skeleton)
    open fun getFields(pattern: String?): String = core.getFields(pattern)
    override fun isFrozen(): Boolean = core.rawFrozen()
    override fun freeze(): DateTimePatternGenerator { core.rawFreeze(); return this }
    override fun cloneAsThawed(): DateTimePatternGenerator {
        val result = clone()
        core.clearFrozen() // ICU4J also clears the original cached instance's flag.
        return result
    }
    public override fun clone(): DateTimePatternGenerator {
        val copy = cloneHost() as? DateTimePatternGenerator ?: cloneNative()
        copy.core = Bridge(copy).also { core.copyStateTo(it) }
        return copy
    }
    protected open fun cloneNative(): DateTimePatternGenerator = DateTimePatternGenerator()

    /** Route only the source's virtual self-calls through the public object. */
    private class Bridge(val owner: DateTimePatternGenerator) : DateTimePatternGeneratorCore(dateTimePatternGeneratorAssertionsEnabled(), owner) {
        override fun isFrozen(): Boolean = owner.isFrozen()
        fun rawFrozen(): Boolean = super.isFrozen()
        override fun freeze(): DateTimePatternGeneratorCore = owner.freeze().core
        fun rawFreeze() { super.freeze() }
        override fun cloneAsThawed(): DateTimePatternGeneratorCore = owner.cloneAsThawed().core
        override fun getDefaultHourFormatChar(): Char = owner.getDefaultHourFormatChar()
        fun rawHour(): Char = super.getDefaultHourFormatChar()
        override fun addPattern(pattern: String?, override: Boolean, info: PatternInfo?): DateTimePatternGeneratorCore = owner.addPattern(pattern, override, info).core
        override fun addPatternWithSkeleton(pattern: String?, skeleton: String?, override: Boolean, info: PatternInfo?): DateTimePatternGeneratorCore = owner.addPatternWithSkeleton(pattern, skeleton, override, info).core
        fun rawAdd(pattern: String?, skeleton: String?, override: Boolean, info: PatternInfo?) { super.addPatternWithSkeleton(pattern, skeleton, override, info) }
        override fun setDateTimeFormat(style: Int, value: String?) = owner.setDateTimeFormat(style, value)
        fun rawSetDateTime(style: Int, value: String?) = super.setDateTimeFormat(style, value)
        override fun getDateTimeFormat(style: Int): String? = owner.getDateTimeFormat(style)
        fun rawGetDateTime(style: Int): String? = super.getDateTimeFormat(style)
        override fun setAppendItemFormat(field: Int, value: String?) = owner.setAppendItemFormat(field, value)
        fun rawSetAppend(field: Int, value: String?) = super.setAppendItemFormat(field, value)
        override fun getAppendItemFormat(field: Int): String? = owner.getAppendItemFormat(field)
        fun rawGetAppend(field: Int): String? = super.getAppendItemFormat(field)
        override fun getFieldDisplayName(field: Int, width: DisplayWidth?): String? = owner.getFieldDisplayName(field, width?.let { DateTimePatternGenerator.DisplayWidth.entries[it.ordinal] })
        fun rawFieldName(field: Int, width: DisplayWidth?): String? = super.getFieldDisplayName(field, width)
    }
    companion object {
        private val factory = DateTimePatternFactoryCore(DateTimePatternInitializationCore(), { locale ->
            val calendar = Calendar.getInstance(locale)
            object : DateTimePatternInitializationCore.CalendarPatterns {
                override fun getDateAtTimePattern(style: Int): String? = Calendar.getDateAtTimePattern(calendar, locale, style)
            }
        }, createGenerator = { DateTimePatternGenerator().core })
        @kotlin.jvm.JvmStatic fun getEmptyInstance(): DateTimePatternGenerator = (factory.getEmptyInstance() as Bridge).owner
        @kotlin.jvm.JvmStatic fun getInstance(): DateTimePatternGenerator = (factory.getInstance() as Bridge).owner
        @kotlin.jvm.JvmStatic fun getInstance(locale: ULocale?): DateTimePatternGenerator = (factory.getInstance(locale) as Bridge).owner
        @kotlin.jvm.JvmStatic fun getFrozenInstance(locale: ULocale?): DateTimePatternGenerator = (factory.getFrozenInstance(locale) as Bridge).owner
        @kotlin.jvm.JvmStatic fun getInstanceNoStdPat(locale: ULocale?): DateTimePatternGenerator = (factory.getInstanceNoStdPat(locale) as Bridge).owner
        @kotlin.jvm.JvmStatic fun getAppendFormatNumber(key: String?): Int = DateTimePatternResourceCore.appendFormatNumber(key)
        @kotlin.jvm.JvmStatic fun getAppendFormatNumber(key: UResource.Key?): Int = DateTimePatternResourceCore.appendFormatNumber(key)
        @kotlin.jvm.JvmStatic fun isSingleField(skeleton: String?): Boolean {
            val text = skeleton!!; val first = dateTimePatternFirstChar(text)
            for (i in 1..<text.length) if (text[i] != first) return false
            return true
        }
        const val ERA=0; const val YEAR=1; const val QUARTER=2; const val MONTH=3
        const val WEEK_OF_YEAR=4; const val WEEK_OF_MONTH=5; const val WEEKDAY=6; const val DAY=7
        const val DAY_OF_YEAR=8; const val DAY_OF_WEEK_IN_MONTH=9; const val DAYPERIOD=10; const val HOUR=11
        const val MINUTE=12; const val SECOND=13; const val FRACTIONAL_SECOND=14; const val ZONE=15; const val TYPE_LIMIT=16
        const val MATCH_NO_OPTIONS=0
        const val MATCH_HOUR_FIELD_LENGTH=1 shl HOUR
        const val MATCH_MINUTE_FIELD_LENGTH=1 shl MINUTE
        const val MATCH_SECOND_FIELD_LENGTH=1 shl SECOND
        const val MATCH_ALL_FIELDS_LENGTH=(1 shl TYPE_LIMIT)-1
    }
    open class VariableField(value: String?,strict: Boolean) {
        constructor(value: String?): this(value,false)
        internal val canonicalIndex=getCanonicalIndex(value,strict)
        private val string: String
        init {
            if(canonicalIndex<0) throw IllegalArgumentException("Illegal datetime field:\t$value")
            string=value!!
        }
        open fun getType(): Int=types[canonicalIndex][1]
        open fun isNumeric(): Boolean=types[canonicalIndex][2]>0
        override fun toString(): String=string
        companion object {
            @kotlin.jvm.JvmStatic fun getCanonicalCode(type: Int): String=if(type in CANONICAL_ITEMS.indices) CANONICAL_ITEMS[type] else type.toString()
        }
    }
    open class FormatParser {
        private val tokenizer=PatternTokenizer().setSyntaxCharacters(SYNTAX_CHARS).setExtraQuotingCharacters(QUOTING_CHARS).setUsingQuote(true)
        private val items=ArrayList<Any?>()
        fun set(value: String?): FormatParser=set(value,false)
        @Suppress("UNUSED_PARAMETER")
        open fun set(value: String?,strict: Boolean): FormatParser {
            items.clear()
            val text=value!!
            if(text.isEmpty()) return this
            tokenizer.setPattern(text)
            val buffer=StringBuilder(); val variable=StringBuilder()
            while(true) {
                buffer.setLength(0)
                val status=tokenizer.next(buffer)
                if(status==PatternTokenizer.DONE) break
                if(status==PatternTokenizer.SYNTAX) {
                    if(variable.isNotEmpty() && buffer[0]!=variable[0]) addVariable(variable,false)
                    variable.append(buffer)
                } else { addVariable(variable,false); items.add(buffer.toString()) }
            }
            addVariable(variable,false)
            return this
        }
        private fun addVariable(variable: StringBuilder,strict: Boolean) {
            if(variable.isNotEmpty()) { items.add(VariableField(variable.toString(),strict)); variable.setLength(0) }
        }
        open fun getItems(): MutableList<Any?> = items
        override fun toString(): String=toString(0,items.size)
        open fun toString(start: Int,limit: Int): String=buildString {
            for(i in start until limit) {
                if(i !in items.indices) throw IndexOutOfBoundsException()
                val item=items[i]
                if(item is String) append(tokenizer.quoteLiteral(item)) else append(item!!.toString())
            }
        }
        open fun hasDateAndTimeFields(): Boolean {
            var found=0
            for(item in items) if(item is VariableField) found=found or (1 shl item.getType())
            return found and DATE_MASK!=0 && found and TIME_MASK!=0
        }
        open fun quoteLiteral(value: String?): Any=tokenizer.quoteLiteral(value)
        companion object {
            private val SYNTAX_CHARS=UnicodeSet("[a-zA-Z]").freeze()
            private val QUOTING_CHARS=UnicodeSet("[[[:script=Latn:][:script=Cyrl:]]&[[:L:][:M:]]]").freeze()
        }
    }
}
internal expect fun dateTimePatternGeneratorAssertionsEnabled(): Boolean

internal expect fun dateTimePatternFirstChar(value: String): Char
