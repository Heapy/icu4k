// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.text.MessagePattern.ArgType
import io.github.heapy.icu4k.text.MessagePattern.Part
import io.github.heapy.icu4k.util.ULocale

/** ICU MessageFormat's recursive formatting and attributed-output engine. */
internal class MessageFormatFormatCore(private val owner: Owner) {
    interface Formatter {
        val complex: Boolean get() = false
        val decimal: Boolean get() = false
        fun format(value: Any?): String
        fun iterator(value: Any?): FormatAttributedCharacterIterator
        fun fixedDecimal(value: Double): PluralRules.IFixedDecimal? = null
    }
    class CommonFormatter(private val format: UFormatBase) : Formatter {
        override val complex get() = format is PluralFormat || format is SelectFormat
        override val decimal get() = format is DecimalFormat
        override fun format(value: Any?): String = format.format(value)
        override fun iterator(value: Any?): FormatAttributedCharacterIterator = format.formatToCharacterIterator(value)
        override fun fixedDecimal(value: Double): PluralRules.IFixedDecimal? = (format as DecimalFormat).getFixedDecimal(value)
    }
    interface Owner {
        val messagePattern: MessagePattern?
        val locale: ULocale?
        val cachedFormatters: Map<Int, Formatter?>?
        val assertionsEnabled: Boolean get() = false
        val argumentField: FormatField
        fun stockNumberFormatter(): Formatter
        fun stockDateFormatter(): Formatter
        fun isDateLike(value: Any): Boolean
        /** null mode means the constructor's default apostrophe mode. */
        fun child(pattern: String, mode: MessagePattern.ApostropheMode?): MessageFormatFormatCore
    }

    class Destination(val output: StringBuilder) {
        var length = output.length
            private set
        var attributes: MutableList<FormatAttributeRange>? = null
            private set
        fun useAttributes() { attributes = ArrayList() }
        fun append(text: CharSequence) { output.append(text); length += text.length }
        fun append(text: CharSequence, start: Int, limit: Int) { output.appendRange(text,start,limit); length += limit-start }
        fun append(iterator: FormatAttributedCharacterIterator) {
            var start=iterator.getBeginIndex()
            val limit=iterator.getEndIndex()
            val count=limit-start
            if(start<limit) {
                output.append(iterator.first())
                start++
                while(start<limit) { output.append(iterator.next());start++ }
            }
            length+=count
        }
        fun formatAndAppend(formatter: Formatter?, value: Any?, cached: String? = null) {
            if(attributes==null && cached!=null) { append(cached);return }
            if(attributes==null) append(formatter!!.format(value))
            else {
                val iterator=formatter!!.iterator(value)
                val previous=length
                append(iterator)
                iterator.first()
                var start=iterator.getIndex()
                val limit=iterator.getEndIndex()
                val offset=previous-start
                while(start<limit) {
                    val map=iterator.getAttributes()
                    val runLimit=iterator.getRunLimit()
                    if(map.isNotEmpty()) for((key,entry) in map) attributes!!.add(FormatAttributeRange(key,entry,offset+start,offset+runLimit))
                    start=runLimit
                    iterator.setIndex(start)
                }
            }
        }
        fun iterator(): FormatAttributedCharacterIterator = attributedCharacterIterator(output.toString(),attributes ?: emptyList())
    }

    private class PluralContext(val start: Int,val name: String,number: Number,val offset: Double) {
        val number: Number=if(offset==0.0) number else numberFormatDoubleValue(number)-offset
        var numberArgIndex=0
        var formatter: Formatter?=null
        var numberString: String?=null
        var forReplaceNumber=false
        override fun toString(): String = throw AssertionError("PluralSelectorContext being formatted, rather than its number")
    }
    private inner class Provider(val type: PluralRules.PluralType) : PluralFormatCore.PluralSelector {
        private var rules: PluralRules?=null
        override fun select(context: Any?,number: Double): String? {
            if(rules==null) rules=PluralRules.forLocale(owner.locale,type)
            val ctx=context as PluralContext
            val pattern=owner.messagePattern!!
            val other=MessageFormatPatternCore.findOtherSubMessage(pattern,ctx.start)
            ctx.numberArgIndex=MessageFormatPatternCore.findFirstPluralNumberArg(pattern,other,ctx.name)
            if(ctx.numberArgIndex>0 && owner.cachedFormatters!=null) ctx.formatter=owner.cachedFormatters!![ctx.numberArgIndex]
            if(ctx.formatter==null) { ctx.formatter=owner.stockNumberFormatter();ctx.forReplaceNumber=true }
            if(owner.assertionsEnabled && !(numberFormatDoubleValue(ctx.number)==number)) throw AssertionError()
            ctx.numberString=ctx.formatter!!.format(ctx.number)
            return if(ctx.formatter!!.decimal) rules!!.select(ctx.formatter!!.fixedDecimal(number)) else rules!!.select(number)
        }
    }
    private var cardinal: Provider?=null
    private var ordinal: Provider?=null
    fun resetPluralProviders() { cardinal=null;ordinal=null }

    fun format(arguments: Any?,dest: Destination,position: FormatFieldPosition? = null) {
        if(arguments==null || arguments is Map<*,*>) {
            @Suppress("UNCHECKED_CAST")
            format(null,arguments as Map<String,Any?>?,dest,position)
        } else {
            @Suppress("UNCHECKED_CAST")
            format(arguments as Array<Any?>,null,dest,position)
        }
    }
    fun format(args: Array<out Any?>?,map: Map<String,Any?>?,dest: Destination,position: FormatFieldPosition? = null) {
        if(args!=null && owner.messagePattern!!.hasNamedArguments()) throw IllegalArgumentException("This method is not available in MessageFormat objects that use alphanumeric argument names.")
        formatMessage(0,null,args,map,dest,position)
    }
    fun iterator(arguments: Any?): FormatAttributedCharacterIterator {
        if(arguments==null) throw NullPointerException("formatToCharacterIterator must be passed non-null object")
        val dest=Destination(StringBuilder()).also { it.useAttributes() }
        format(arguments,dest)
        return dest.iterator()
    }

    private fun formatMessage(start: Int,plural: PluralContext?,args: Array<out Any?>?,map: Map<String,Any?>?,dest: Destination,position: FormatFieldPosition?) {
        val pattern=owner.messagePattern!!
        val text=pattern.getPatternString()!!
        var previous=pattern.getPart(start).getLimit()
        var i=start+1
        var fp=position
        while(true) {
            var part=pattern.getPart(i)
            val type=part.type
            dest.append(text,previous,part.index)
            if(type==Part.Type.MSG_LIMIT) return
            previous=part.getLimit()
            if(type==Part.Type.REPLACE_NUMBER) {
                val ctx=plural!!
                if(ctx.forReplaceNumber) dest.formatAndAppend(ctx.formatter,ctx.number,ctx.numberString)
                else dest.formatAndAppend(owner.stockNumberFormatter(),ctx.number)
                i++;continue
            }
            if(type!=Part.Type.ARG_START) { i++;continue }
            val limit=pattern.getLimitPartIndex(i)
            val argType=part.getArgType()
            part=pattern.getPart(++i)
            val name=pattern.getSubstring(part)
            val value: Any?
            val id: Any?
            val missing: Boolean
            if(args!=null) {
                val n=part.value
                id=if(dest.attributes!=null) n else null
                if(n>=0 && n<args.size) { value=args[n];missing=false } else { value=null;missing=true }
            } else {
                id=name
                value=map?.get(name)
                missing=if(map==null) true else value==null && !map.containsKey(name)
            }
            i++
            val oldLength=dest.length
            val cached=owner.cachedFormatters
            when {
                missing -> { dest.append("{");dest.append(name);dest.append("}") }
                value==null -> dest.append("null")
                plural!=null && plural.numberArgIndex==i-2 -> {
                    if(plural.offset==0.0) dest.formatAndAppend(plural.formatter,plural.number,plural.numberString)
                    else dest.formatAndAppend(plural.formatter,value)
                }
                cached?.get(i-2)!=null -> {
                    val formatter=cached[i-2]!!
                    if(formatter.complex) {
                        val sub=formatter.format(value)
                        if('{' in sub || ('\'' in sub && !pattern.jdkAposMode())) owner.child(sub,null).formatMessage(0,null,args,map,dest,null)
                        else if(dest.attributes==null) dest.append(sub)
                        else dest.formatAndAppend(formatter,value)
                    } else dest.formatAndAppend(formatter,value)
                }
                argType==ArgType.NONE || cached?.containsKey(i-2)==true -> {
                    val number=value as? Number
                    when { number!=null -> dest.formatAndAppend(owner.stockNumberFormatter(),value)
                        owner.isDateLike(value) -> dest.formatAndAppend(owner.stockDateFormatter(),value)
                        else -> dest.append(value.toString()) }
                }
                argType==ArgType.CHOICE -> {
                    val number=value as? Number ?: throw IllegalArgumentException("'$value' is not a Number")
                    formatComplex(MessageFormatPatternCore.findChoiceSubMessage(pattern,i,numberFormatDoubleValue(number)),null,args,map,dest)
                }
                argType.hasPluralStyle() -> {
                    val number=value as? Number ?: throw IllegalArgumentException("'$value' is not a Number")
                    val provider=if(argType==ArgType.PLURAL) cardinal ?: Provider(PluralRules.PluralType.CARDINAL).also { cardinal=it }
                                 else ordinal ?: Provider(PluralRules.PluralType.ORDINAL).also { ordinal=it }
                    val context=PluralContext(i,name,number,pattern.getPluralOffset(i))
                    val sub=PluralFormatCore.findSubMessage(pattern,i,provider,context,numberFormatDoubleValue(number))
                    formatComplex(sub,context,args,map,dest)
                }
                argType==ArgType.SELECT -> formatComplex(SelectFormat.findSubMessage(pattern,i,value.toString()),null,args,map,dest)
                else -> throw IllegalStateException("unexpected argType $argType")
            }
            if(dest.attributes!=null && oldLength<dest.length) dest.attributes!!.add(FormatAttributeRange(owner.argumentField,id!!,oldLength,dest.length))
            if(fp!=null && owner.argumentField==fp.getFieldAttribute()) {
                fp.setBeginIndex(oldLength);fp.setEndIndex(dest.length);fp=null
            }
            previous=pattern.getPart(limit).getLimit()
            i=limit+1
        }
    }

    private fun formatComplex(start: Int,plural: PluralContext?,args: Array<out Any?>?,map: Map<String,Any?>?,dest: Destination) {
        val pattern=owner.messagePattern!!
        if(!pattern.jdkAposMode()) { formatMessage(start,plural,args,map,dest,null);return }
        val text=pattern.getPatternString()!!
        var builder: StringBuilder?=null
        var previous=pattern.getPart(start).getLimit()
        var i=start
        val sub: String
        while(true) {
            val part=pattern.getPart(++i)
            val type=part.type
            var index=part.index
            if(type==Part.Type.MSG_LIMIT) {
                sub=if(builder==null) text.substring(previous,index) else builder.appendRange(text,previous,index).toString()
                break
            } else if(type==Part.Type.REPLACE_NUMBER || type==Part.Type.SKIP_SYNTAX) {
                if(builder==null) builder=StringBuilder()
                builder.appendRange(text,previous,index)
                if(type==Part.Type.REPLACE_NUMBER) {
                    val ctx=plural!!
                    builder.append(if(ctx.forReplaceNumber) ctx.numberString else owner.stockNumberFormatter().format(ctx.number))
                }
                previous=part.getLimit()
            } else if(type==Part.Type.ARG_START) {
                if(builder==null) builder=StringBuilder()
                builder.appendRange(text,previous,index)
                previous=index
                i=pattern.getLimitPartIndex(i)
                index=pattern.getPart(i).getLimit()
                MessagePattern.appendReducedApostrophes(text,previous,index,builder)
                previous=index
            }
        }
        if('{' in sub) owner.child(sub,MessagePattern.ApostropheMode.DOUBLE_REQUIRED).formatMessage(0,null,args,map,dest,null)
        else dest.append(sub)
    }
}
