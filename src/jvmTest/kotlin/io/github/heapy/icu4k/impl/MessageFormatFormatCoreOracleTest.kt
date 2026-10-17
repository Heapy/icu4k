@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import com.ibm.icu.text.MessageFormat as Source
import com.ibm.icu.text.MessagePattern as SourcePattern
import com.ibm.icu.text.DecimalFormat as SourceDecimal
import com.ibm.icu.text.NumberFormat as SourceNumber
import com.ibm.icu.text.PluralRules as SourceRules
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class MessageFormatFormatCoreOracleTest {
    private class Adapter(val delegate: java.text.Format) : MessageFormatFormatCore.Formatter {
        override val complex get()=delegate is java.text.ChoiceFormat || delegate is com.ibm.icu.text.PluralFormat || delegate is com.ibm.icu.text.SelectFormat
        override val decimal get()=delegate is SourceDecimal
        override fun format(value:Any?):String=delegate.format(value)
        override fun iterator(value:Any?):FormatAttributedCharacterIterator=delegate.formatToCharacterIterator(value)
        override fun fixedDecimal(value:Double):PluralRules.IFixedDecimal? {
            val fixed=(delegate as SourceDecimal).getFixedDecimal(value) ?: return null
            return object:PluralRules.IFixedDecimal {
                override fun getPluralOperand(operand:PluralRules.Operand?):Double=fixed.getPluralOperand(operand?.let {SourceRules.Operand.valueOf(it.name)})
                override fun isNaN()=fixed.isNaN
                override fun isInfinite()=fixed.isInfinite
                override fun isHasIntegerValue()=fixed.isHasIntegerValue
            }
        }
    }
    private class Owner(val source:Source,val id:String,val mode:MessagePattern.ApostropheMode?,val commonNumbers:Boolean) : MessageFormatFormatCore.Owner {
        override val assertionsEnabled=Source::class.java.desiredAssertionStatus()
        override val locale=ULocale(id)
        private val patternField=Source::class.java.getDeclaredField("msgPattern").apply {isAccessible=true}
        val sourcePattern=patternField.get(source) as SourcePattern
        override val messagePattern=MessagePattern(MessagePattern.ApostropheMode.valueOf(sourcePattern.apostropheMode.name)).parse(sourcePattern.patternString)
        override val argumentField:FormatField=Source.Field.ARGUMENT
        override val cachedFormatters=HashMap<Int,MessageFormatFormatCore.Formatter?>()
        val core=MessageFormatFormatCore(this)
        init {
            val field=Source::class.java.getDeclaredField("cachedFormatters").apply {isAccessible=true}
            @Suppress("UNCHECKED_CAST")
            val formats=field.get(source) as Map<Int,java.text.Format?>?
            formats?.forEach { (key,format) -> cachedFormatters[key]=format?.let { adapt(it.clone() as java.text.Format) } }
        }
        private fun adapt(format:java.text.Format):MessageFormatFormatCore.Formatter {
            if(commonNumbers && format is SourceDecimal) {
                val port=DecimalFormat(format.toPattern(),DecimalFormatSymbols(locale))
                format.currency?.let { port.setCurrency(io.github.heapy.icu4k.util.Currency.getInstance(it.currencyCode)) }
                return MessageFormatFormatCore.CommonFormatter(port)
            }
            return Adapter(format)
        }
        private var numberFormatter:MessageFormatFormatCore.Formatter?=null
        private var dateFormatter:MessageFormatFormatCore.Formatter?=null
        override fun stockNumberFormatter():MessageFormatFormatCore.Formatter {
            if(numberFormatter==null) numberFormatter=if(commonNumbers) MessageFormatFormatCore.CommonFormatter(NumberFormat.getInstance(locale)) else Adapter(SourceNumber.getInstance(SourceLocale(id)))
            return numberFormatter!!
        }
        override fun stockDateFormatter():MessageFormatFormatCore.Formatter {
            val method=Source::class.java.getDeclaredMethod("getStockDateFormatter").apply {isAccessible=true}
            if(dateFormatter==null) dateFormatter=Adapter((method.invoke(source) as java.text.Format).clone() as java.text.Format)
            return dateFormatter!!
        }
        override fun isDateLike(value:Any):Boolean=value is java.util.Date || value is com.ibm.icu.util.Calendar || value is java.util.Calendar || value is java.time.temporal.Temporal || value is java.time.DayOfWeek || value is java.time.Month
        override fun child(pattern:String,mode:MessagePattern.ApostropheMode?):MessageFormatFormatCore {
            val child=Source("",SourceLocale(id))
            if(mode!=null) child.applyPattern(pattern,SourcePattern.ApostropheMode.valueOf(mode.name)) else child.applyPattern(pattern)
            return Owner(child,id,mode,commonNumbers).core
        }
    }
    private fun pair(pattern:String,id:String="en_US",mode:MessagePattern.ApostropheMode=MessagePattern.ApostropheMode.DOUBLE_OPTIONAL,common:Boolean=false):Pair<Source,Owner> {
        val source=Source("",SourceLocale(id));source.applyPattern(pattern,SourcePattern.ApostropheMode.valueOf(mode.name))
        return source to Owner(source,id,mode,common)
    }
    private fun outcome(block:()->Any?):Any?=try {block()} catch(e:Throwable) {
        if(e is VirtualMachineError) throw e
        when(e) {is NullPointerException -> "null";is ClassCastException -> "cast";is IndexOutOfBoundsException -> "bounds";else -> e.javaClass.simpleName+":"+e.message}
    }
    private fun attr(value:Any?):Any?=if(value is java.text.AttributedCharacterIterator.Attribute) value.toString().substringAfter('(') else value
    private fun snapshot(iterator:java.text.AttributedCharacterIterator):Any {
        val text=StringBuilder();val attrs=ArrayList<Map<String,Any?>>()
        var i=iterator.beginIndex
        while(i<iterator.endIndex) {
            text.append(iterator.setIndex(i));attrs.add(iterator.attributes.entries.associate { it.key.toString().substringAfter('(') to attr(it.value) });i++
        }
        return listOf(text.toString(),attrs)
    }
    private fun compare(a:Source,b:Owner,args:Any?) {
        val dest=MessageFormatFormatCore.Destination(StringBuilder("p:"))
        val ap=java.text.FieldPosition(Source.Field.ARGUMENT).also {it.beginIndex=7;it.endIndex=9}
        val bp=FormatFieldPosition(Source.Field.ARGUMENT).also {it.setBeginIndex(7);it.setEndIndex(9)}
        val output=StringBuffer("p:")
        val left=outcome {a.format(args,output,ap).toString()}
        val right=outcome {b.core.format(args,dest,bp);dest.output.toString()}
        assertEquals(left,right,"${b.messagePattern.getPatternString()}/$args/${b.id}")
        assertEquals(output.toString(),dest.output.toString());assertEquals(listOf(ap.beginIndex,ap.endIndex),listOf(bp.getBeginIndex(),bp.getEndIndex()))
        assertEquals(outcome {snapshot(a.formatToCharacterIterator(args))},outcome {snapshot(b.core.iterator(args))},"attrs/${b.messagePattern.getPatternString()}/${(args as? Array<*>)?.map { it?.javaClass?.name }}/$args/${b.id}")
    }
    @Test fun recursiveGrammarApostrophesMissingAndWrongArguments() {
        val patterns=listOf("literal","a''b {0}","'{0}' {1}","{0}/{1}/{2}","{name}:{missing}","{0,choice,0#none|1#one|1<many {1}}","{n,select,a{{value}} other{other}}","{n,plural,offset:1 =0{none} one{# one {who}} other{# many {n}}}","{0,selectordinal,one{#st} two{#nd} few{#rd} other{#th}}","{n,plural,one{one} other{{n,number,0.0} (#)}}","{n,plural,one{one} other{{who,select,a{A #} other{B}} #}}","{0,choice,0#'{'quoted'}'|1#'{1}'}")
        val arguments=listOf(null,emptyArray<Any?>(),arrayOf<Any?>(0,"X"),arrayOf<Any?>(1,"X"),arrayOf<Any?>(2,"X"),arrayOf<Any?>(22,null),mapOf("n" to 0,"who" to "a","value" to "V"),mapOf("n" to 2,"who" to "a","value" to "V"),mapOf("n" to 5,"who" to null),mapOf("name" to "N"),mapOf("n" to "a","value" to "V"),mapOf("n" to "wrong"))
        for(mode in MessagePattern.ApostropheMode.entries) for(pattern in patterns) {
            val(a,b)=pair(pattern,mode=mode)
            for(args in arguments) compare(a,b,args)
        }
    }
    @Test fun realCommonNumberFormattingPluralPrecisionAndOffsetsAcrossLocales() {
        val patterns=listOf("{n,plural,one{one} two{two} few{few} many{many} other{other #}}","{n,plural,offset:1 =0{zero #} =1{one #} one{one {n,number,0.0} #} other{other {n,number,0.0} #}}","{n,plural,one{one} other{other {n,number,0.00}}}","{n,plural,one{one} other{other {n,number,integer}}}","{n,selectordinal,one{one #} two{two #} few{few #} other{other #}}")
        for(id in listOf("en_US","ru","pl","ar","fr","sl","lv")) for(pattern in patterns) {
            val(a,b)=pair(pattern,id,common=true)
            for(n in listOf<Number>(-2,-1,0,1,2,3,5,11,21,101,1.1,1.01,2.5,Double.NaN,Double.POSITIVE_INFINITY,java.math.BigDecimal("1.00"),java.math.BigInteger("9007199254740993"))) compare(a,b,mapOf("n" to n))
        }
    }
    @Test fun stockDatesTemporalEnumsAndExplicitFormats() {
        val patterns=listOf("{0}","{0,date,yyyy-MM-dd}/{1,time,HH:mm}","{0,number,currency} {1,number,percent}","{0,spellout} {1,ordinal}")
        val arguments=listOf(arrayOf<Any?>(java.util.Date(0),java.util.Date(123456789)),arrayOf<Any?>(12.5,0.5),arrayOf<Any?>(java.time.LocalDate.of(2024,2,29),java.time.LocalTime.of(12,34)),arrayOf<Any?>(java.time.DayOfWeek.MONDAY,java.time.Month.JANUARY),arrayOf<Any?>(java.util.GregorianCalendar(),com.ibm.icu.util.GregorianCalendar()))
        for(pattern in patterns) {val(a,b)=pair(pattern);for(args in arguments) compare(a,b,args)}
    }
    @Test fun customComplexFormatsAndGenericAttributes() {
        val formats=listOf<java.text.Format>(java.text.ChoiceFormat("0#none|1#one {1}"),com.ibm.icu.text.PluralFormat("one{one} other{# {1}}"),com.ibm.icu.text.SelectFormat("a{A {1}} other{O}"))
        for(mode in MessagePattern.ApostropheMode.entries) for(format in formats) {
            val(a,_)=pair("{0}",mode=mode);a.setFormat(0,format)
            val b=Owner(a,"en_US",mode,false)
            for(args in listOf(arrayOf<Any?>(0,"X"),arrayOf<Any?>(1,"X"),arrayOf<Any?>(2,"X"),arrayOf<Any?>("a","X"))) compare(a,b,args)
        }
        val custom=object:java.text.Format() {
            override fun format(obj:Any?,out:StringBuffer?,pos:java.text.FieldPosition?):StringBuffer=out!!.append("custom")
            override fun parseObject(source:String?,pos:java.text.ParsePosition?):Any?=null
            override fun formatToCharacterIterator(obj:Any?):java.text.AttributedCharacterIterator {
                val text=java.text.AttributedString("prefixCUSTOMsuffix")
                text.addAttribute(java.awt.font.TextAttribute.WEIGHT,java.awt.font.TextAttribute.WEIGHT_BOLD,6,12)
                return text.getIterator(null,6,12)
            }
        }
        val(a,_)=pair("A{0}B");a.setFormat(0,custom)
        compare(a,Owner(a,"en_US",null,false),arrayOf<Any?>(1))
    }
    private class CountingChoice(pattern:String):java.text.ChoiceFormat(pattern) {
        var calls=0
        var iteratorCalls=0
        override fun format(value:Double,output:StringBuffer?,pos:java.text.FieldPosition?):StringBuffer {
            calls++;return super.format(value,output,pos)
        }
        override fun formatToCharacterIterator(value:Any?):java.text.AttributedCharacterIterator {
            iteratorCalls++;return super.formatToCharacterIterator(value)
        }
    }
    @Test fun complexFormatsRepeatFormattingForAttributesAndEmptyArgumentsConsumePosition() {
        for(pattern in listOf("0#plain|1#also plain","0#plain|1#nested {1}")) for(value in listOf(0,1)) {
            val(a,_)=pair("{0}")
            val format=CountingChoice(pattern);a.setFormat(0,format)
            val b=Owner(a,"en_US",null,false)
            val other=(b.cachedFormatters[1] as Adapter).delegate as CountingChoice
            compare(a,b,arrayOf<Any?>(value,"X"))
            assertEquals(format.calls,other.calls);assertEquals(format.iteratorCalls,other.iteratorCalls)
        }
        val(a,_)=pair("{0}/{1}")
        a.setFormat(0,object:java.text.Format() {
            override fun format(value:Any?,out:StringBuffer?,pos:java.text.FieldPosition?):StringBuffer=out!!
            override fun parseObject(text:String?,pos:java.text.ParsePosition?):Any?=null
        })
        compare(a,Owner(a,"en_US",null,false),arrayOf<Any?>(0,"X"))
    }
}
