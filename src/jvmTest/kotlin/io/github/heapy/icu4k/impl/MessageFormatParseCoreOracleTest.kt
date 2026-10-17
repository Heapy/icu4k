package io.github.heapy.icu4k.impl

import com.ibm.icu.text.MessageFormat as Source
import com.ibm.icu.text.MessagePattern as SourcePattern
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class MessageFormatParseCoreOracleTest {
    private class Owner(override val messagePattern: MessagePattern) : MessageFormatParseCore.Owner {
        override var cachedParsers: MutableMap<Int,MessageFormatParseCore.Parser?>?=null
    }
    private fun pair(pattern: String,mode: MessagePattern.ApostropheMode = MessagePattern.ApostropheMode.DOUBLE_OPTIONAL): Pair<Source,Owner> {
        val a=Source("",SourceLocale.US)
        a.applyPattern(pattern,SourcePattern.ApostropheMode.valueOf(mode.name))
        val b=Owner(MessagePattern(mode).parse(pattern))
        val formats=a.formats
        var index=0
        for(format in formats) {
            index=MessageFormatPatternCore.nextTopLevelArgStart(b.messagePattern,index)
            if(format!=null) {
                val copy=format.clone() as java.text.Format
                if(b.cachedParsers==null) b.cachedParsers=HashMap()
                b.cachedParsers!![index]=MessageFormatParseCore.Parser { text,pos ->
                    val p=java.text.ParsePosition(pos!!.index).also { it.errorIndex=pos.errorIndex }
                    try { copy.parseObject(text,p) } finally { pos.index=p.index;pos.errorIndex=p.errorIndex }
                }
            }
        }
        return a to b
    }
    private fun normal(value: Any?): Any? = when(value) {
        is Array<*> -> value.map(::normal)
        is Map<*,*> -> value.mapValues { normal(it.value) }
        is Number -> value.javaClass.simpleName+":"+value.toString()
        is java.util.Date -> "Date:"+value.time
        else -> value
    }
    private fun outcome(block: ()->Any?): Any? = try { normal(block()) } catch(e: Throwable) {
        if(e is VirtualMachineError) throw e
        val t=if(e is java.lang.reflect.InvocationTargetException) e.targetException else e
        if(t is VirtualMachineError) throw t
        when(t) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            is java.text.ParseException -> "ParseException:${t.message}:${t.errorOffset}"
            else -> t.javaClass.simpleName+":"+t.message
        }
    }
    private fun compare(a: Source,b: Owner,text: String?,start: Int) {
        val core=MessageFormatParseCore(b)
        for(method in 0..2) {
            val ap=java.text.ParsePosition(start).also { it.errorIndex=9 }
            val bp=ParsePosition(start).also { it.errorIndex=9 }
            val left=outcome { when(method) { 0 -> a.parse(text,ap);1 -> a.parseToMap(text,ap);else -> a.parseObject(text,ap) } }
            val right=outcome { when(method) { 0 -> core.parse(text,bp);1 -> core.parseToMap(text,bp);else -> core.parseObject(text,bp) } }
            assertEquals(left,right,"${b.messagePattern.getPatternString()}/$text/$start/$method")
            assertEquals(listOf(ap.index,ap.errorIndex),listOf(bp.index,bp.errorIndex))
        }
        assertEquals(outcome { a.parse(text) },outcome { core.parse(text) },"throw/${b.messagePattern.getPatternString()}/$text")
        assertEquals(outcome { a.parseToMap(text) },outcome { core.parseToMap(text) })
    }
    @Test fun literalsNamesNumbersQuotesRepeatedAndAdjacentArguments() {
        val patterns=listOf("","literal","a''b","'{0}'","'{'{0}'}'","{0}","{2}","{0}{1}","{0}/{1}","{0}/{0}","A {2} / {0}.","{name}","{name}/{name}","{0}-{name}","'{x}'{name}''tail", "a'{0}","{0}a''b{1}")
        val texts=listOf(null,"","literal","a'b","{0}","{name}","left/right","A left / right.","A/{name}","xxliteral","a'{0}","lefta'bright")
        for(mode in MessagePattern.ApostropheMode.entries) for(pattern in patterns) {
            val(a,b)=pair(pattern,mode)
            for(text in texts) for(start in listOf(-1,0,1,3,100)) compare(a,b,text,start)
            assertEquals(outcome { a.parse(null,null as java.text.ParsePosition?) },outcome { MessageFormatParseCore(b).parse(null,null as ParsePosition?) })
        }
    }
    @Test fun choicesNestedLiteralsLongestMatchAndUnsupportedPluralSelect() {
        val patterns=listOf("{0,choice,0#no|1#one|2<many {1}}!", "{n,choice,-1#same|0#same|1#same longer|2#}","{0,choice,0#ab''cd|1#other}","{0,choice,0#'{'x'}'|1#quoted}","{0,plural,one{one} other{# many}}", "{n,select,a{A} other{O}}","{0,selectordinal,one{one} other{other}}","{0,choice,0#{nested}|1#{2,number}}")
        for(mode in MessagePattern.ApostropheMode.entries) for(pattern in patterns) {
            val(a,b)=pair(pattern,mode)
            for(text in listOf(null,"","no!","one!","many {1}!","many X!","same longer","ab'cd","other","{nested}","{2,number}","A")) for(start in listOf(-1,0,1,100)) compare(a,b,text,start)
            a.setFormat(0,null)
            b.cachedParsers=hashMapOf(MessageFormatPatternCore.nextTopLevelArgStart(b.messagePattern,0) to null)
            compare(a,b,"raw!",0)
        }
    }
    @Test fun realStockFormatDelegationAndCommonNumberParsers() {
        for(pattern in listOf("{0,number}","x{0,number,integer}/{1,number,percent}","{n,number,currency}","{0,date,yyyy-MM-dd}/{1,time,HH:mm}","{0,spellout}","{0,ordinal}","{0,duration}")) {
            val(a,b)=pair(pattern)
            for(text in listOf("1,234.5","x12/34%","$12.00","1970-01-01/12:34","twelve","12th","1:23", "wrong")) for(start in listOf(0,1)) compare(a,b,text,start)
        }
        val(a,b)=pair("{0}/{1}")
        a.setFormat(0,com.ibm.icu.text.NumberFormat.getInstance(SourceLocale("ar")))
        val nf=NumberFormat.getInstance(ULocale("ar"))!!
        b.cachedParsers=hashMapOf(1 to MessageFormatParseCore.Parser { text,pos -> nf.parse(text,pos) })
        compare(a,b,"١٢/remaining",0)
    }
    @Test fun parserNullResultsProgressAndErrorIndexReuse() {
        val(a,b)=pair("{0}{1}")
        val seenA=ArrayList<Int>();val seenB=ArrayList<Int>()
        a.setFormat(0,object:java.text.Format() {
            override fun format(obj:Any?,out:StringBuffer?,pos:java.text.FieldPosition?):StringBuffer = out!!
            override fun parseObject(source:String?,pos:java.text.ParsePosition?):Any? {seenA.add(pos!!.errorIndex);pos.index++;pos.errorIndex=42;return null}
        })
        a.setFormat(1,object:java.text.Format() {
            override fun format(obj:Any?,out:StringBuffer?,pos:java.text.FieldPosition?):StringBuffer = out!!
            override fun parseObject(source:String?,pos:java.text.ParsePosition?):Any? {seenA.add(pos!!.errorIndex);pos.index++;return "second"}
        })
        val first=MessageFormatPatternCore.nextTopLevelArgStart(b.messagePattern,0)
        val second=MessageFormatPatternCore.nextTopLevelArgStart(b.messagePattern,first)
        b.cachedParsers=hashMapOf(first to MessageFormatParseCore.Parser { _,pos -> seenB.add(pos!!.errorIndex);pos.index++;pos.errorIndex=42;null },second to MessageFormatParseCore.Parser { _,pos -> seenB.add(pos!!.errorIndex);pos.index++;"second" })
        compare(a,b,"xy",0)
        assertEquals(seenA,seenB);assertTrue(42 in seenB)
    }

    @Test fun choiceAndPluralTraversalHelpers() {
        val choose=Source::class.java.getDeclaredMethod("findChoiceSubMessage",SourcePattern::class.java,Int::class.javaPrimitiveType,Double::class.javaPrimitiveType).apply { isAccessible=true }
        for(pattern in listOf("{0,choice,-∞#low|0#zero|1<more|∞#end}","{0,choice,0#a|0#b|1#c}","{n,choice,0#{x}|1#{y,number}}")) {
            val a=SourcePattern(pattern);val b=MessagePattern(pattern)
            for(number in listOf(Double.NaN,Double.NEGATIVE_INFINITY,-1.0,-0.0,0.0,1.0,1.00001,Double.POSITIVE_INFINITY)) {
                assertEquals(choose.invoke(null,a,3,number),MessageFormatPatternCore.findChoiceSubMessage(b,3,number),"$pattern/$number")
            }
        }
        val other=Source::class.java.getDeclaredMethod("findOtherSubMessage",Int::class.javaPrimitiveType).apply { isAccessible=true }
        val first=Source::class.java.getDeclaredMethod("findFirstPluralNumberArg",Int::class.javaPrimitiveType,String::class.java).apply { isAccessible=true }
        for(pattern in listOf("{n,plural,one{one} other{#}}","{n,plural,offset:1 =0{zero} one{{n,number}} other{{n} things}}","{0,select,a{{1,plural,other{#}}} other{{0,number}}}","{n,plural,one{'#'} other{nothing}}")) {
            val(a,b)=pair(pattern)
            val start=other.invoke(a,3) as Int
            assertEquals(start,MessageFormatPatternCore.findOtherSubMessage(b.messagePattern,3))
            for(name in listOf("","n","0","1","missing")) assertEquals(first.invoke(a,start,name),MessageFormatPatternCore.findFirstPluralNumberArg(b.messagePattern,start,name),"$pattern/$name")
        }
    }
}
