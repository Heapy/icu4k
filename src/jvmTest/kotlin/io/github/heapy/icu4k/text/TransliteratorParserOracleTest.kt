// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import java.lang.reflect.InvocationTargetException
import kotlin.test.*
import com.ibm.icu.text.Transliterator as SourceTransliterator
import com.ibm.icu.text.Replaceable as SourceReplaceable
import com.ibm.icu.text.UnicodeMatcher as SourceMatcher
import com.ibm.icu.text.UnicodeSet as SourceSet

class TransliteratorParserOracleTest {
    private val parserClass=Class.forName("com.ibm.icu.text.TransliteratorParser")
    private val idClass=Class.forName("com.ibm.icu.text.TransliteratorIDParser")
    private val dc=Class.forName("com.ibm.icu.text.RuleBasedTransliterator\$Data")
    private val rbc=Class.forName("com.ibm.icu.text.RuleBasedTransliterator")
    private val replacerClass=Class.forName("com.ibm.icu.text.UnicodeReplacer")
    private val intType=Int::class.javaPrimitiveType!!
    private val boolType=Boolean::class.javaPrimitiveType!!
    private fun call(obj: Any,name: String,types: Array<Class<*>> = emptyArray(),vararg args: Any?): Any? = invoke(obj.javaClass,obj,name,types,*args)
    private fun invoke(type: Class<*>,obj: Any?,name: String,types: Array<Class<*>>,vararg args: Any?): Any? = try {
        type.getDeclaredMethod(name,*types).apply{isAccessible=true}.invoke(obj,*args)
    }catch(e: InvocationTargetException){throw e.cause!!}
    private fun field(obj: Any,name: String): Any? = obj.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(obj)
    private fun sourceParser()=parserClass.getDeclaredConstructor().apply{isAccessible=true}.newInstance()
    private fun error(action: () -> Unit): List<String> = try {action();emptyList()}catch(e: Throwable) {
        val result=mutableListOf<String>();var current: Throwable?=e
        while(current!=null && result.size<50) {
            val t=current
            result.add(if(t is IndexOutOfBoundsException)"bounds" else t::class.java.simpleName+":"+t.message)
            current=t.cause
        }
        result
    }
    private fun initializeInverses() {
        SourceTransliterator.getInstance("Any-Null")
        @Suppress("UNCHECKED_CAST")
        val source=idClass.getDeclaredField("SPECIAL_INVERSES").apply{isAccessible=true}.get(null) as Map<com.ibm.icu.util.CaseInsensitiveString,String>
        for((key,value)in source)TransliteratorIDParser.registerSpecialInverse(key.string,value,false)
    }
    private val factory=TransliterationFactory { basic,canon ->
        if(!basic.equals("Any-PortUnit",true))null else object: TransliterationFunction {
            private var filter: UnicodeSet?=null
            override fun getID()=canon ?: basic
            override fun setFilter(filter: UnicodeSet){this.filter=filter}
            override fun getTargetSet()=UnicodeSet("[X-Z]")
            override fun transliterate(text: Replaceable,start: Int,limit: Int): Int {
                var i=start
                while(i<limit){val c=text.charAt(i);if(c in 'a'..'c' && (filter==null || filter!!.contains(c.code)))text.replace(i,i+1,('X'.code+c.code-'a'.code).toChar().toString());++i}
                return limit
            }
        }
    }
    private fun registerFunction() {
        SourceTransliterator.registerFactory("Any-PortUnit") { _ -> object: SourceTransliterator("Any-PortUnit",null) {
            override fun handleTransliterate(text: SourceReplaceable,pos: Position,incremental: Boolean) {
                while(pos.start<pos.limit){val c=text.charAt(pos.start);if(c in 'a'..'c')text.replace(pos.start,pos.start+1,('X'.code+c.code-'a'.code).toChar().toString());++pos.start}
            }
            override fun getTargetSet()=SourceSet("[X-Z]")
        } }
    }
    private fun serialized(action: () -> Any?): String = try { "OK:"+action() }catch(e: Throwable){"ERR:"+e::class.java.simpleName}
    private fun snapshot(source: Any,port: TransliteratorParser) {
        assertEquals((field(source,"compoundFilter") as SourceSet?)?.toPattern(false),port.compoundFilter?.toPattern(false))
        assertEquals(field(source,"idBlockVector"),port.idBlockVector)
        val data=field(source,"dataVector") as List<*>
        assertEquals(data.size,port.dataVector.size)
        for(i in data.indices) {
            val sd=data[i]!!;val pd=port.dataVector[i]
            assertEquals(field(sd,"variablesBase"),pd.variablesBase)
            @Suppress("UNCHECKED_CAST")val sn=field(sd,"variableNames") as Map<String,CharArray>
            assertEquals(sn.keys,pd.variableNames.keys)
            for(key in sn.keys)assertContentEquals(sn[key],pd.variableNames[key])
            val sv=field(sd,"variables") as Array<*>?
            assertEquals(sv?.size,pd.variables?.size)
            if(sv!=null)for(j in sv.indices) {
                val a=sv[j];val b=pd.variables!![j]
                assertEquals(a?.javaClass?.simpleName,b?.javaClass?.simpleName)
                for(escape in listOf(false,true)) {
                    if(a is SourceMatcher)assertEquals(serialized{a.toPattern(escape)},serialized{(b as UnicodeMatcher).toPattern(escape)})
                    if(a!=null && replacerClass.isInstance(a))assertEquals(serialized{invoke(replacerClass,a,"toReplacerPattern",arrayOf(boolType),escape)},serialized{(b as UnicodeReplacer).toReplacerPattern(escape)})
                }
            }
            for(escape in listOf(false,true))assertEquals(serialized{call(field(sd,"ruleSet")!!,"toRules",arrayOf(boolType),escape)},serialized{pd.ruleSet.toRules(escape)})
        }
    }
    @Test fun syntaxStateErrorsAndReusableParser() {
        initializeInverses();registerFunction()
        val rules=listOf("", "# comment", ";;;", "a > b;", "a > bc", "a > bc; d > e;", "a ← b; b ↔ c;", "a <> b;", "x{ab}y > z;", "^a$ > z;", "a > |@@b;", "a > b@@|;", "'a b' > 'x''y';", "\\u0061 > \\U0001F600;", "[a-z] > X;", "[:Lu:] > x;", "$"+"v=[a-c]; $"+"v > x;", "$"+"v='ab'; $"+"v+ > x;", "(a(b)) > $"+"2$"+"1;", "(.) > $"+"1;", "a* > x;", "[ab]?c+ > X;", "::Any-PortUnit; a>b; ::Null; c>d;", "::[a-z]; a>b;", "a>b; ::([a-z]);", "a > &PortUnit(ab);", "a > &[ab]PortUnit(abc);", "use variable range 0xE000 0xE100; [a]>b;", "use maximum backup 2;", "use nfd rules;", "use nfc rules;", "use unknown;", "a > b; a > c;", "$"+"bad > x;", "$"+"v=a; $"+"v=b;", "a > $"+"1;", "a > [b];", "[a > b;", "a > 'bad;", "a > \\q;", "a > \\;", "* > x;", "(a > b;", "a > &Missing(a);", "::   ", "::[a]; ::[b]; a>b;", "a>>b;", "a > a|@b;", "a { b { c > x;", "a > ^b;", "a > b$;", "a > b; "+"! > !;".repeat(35), ". > x;", "a > ", "a >", "a", "\\uF000 > x;", "use variable range 0xFFFF 0xFFFF; a>b;")
        val source=sourceParser();val port=TransliteratorParser(factory)
        for(dir in 0..1)for(rule in rules) {
            val a=error{call(source,"parse",arrayOf(String::class.java,intType),rule,dir)}
            val b=error{port.parse(rule,dir)}
            assertEquals(a,b,"dir=$dir rule=$rule")
            assertEquals(error{snapshot(source,port)},emptyList(),"snapshot dir=$dir rule=$rule")
        }
        SourceTransliterator.unregister("Any-PortUnit")
    }
    @Test fun parsedRulesExecuteLikeSourceEngine() {
        initializeInverses();registerFunction()
        val rules=listOf("a > B; b > CC;", "x{ab}y > z;", "^a > z; b$ > y;", "(a)(b) > $"+"2$"+"1;", "[ab]+ > Q;", "'ab'?c > Z;", "$"+"v=[a-c]; ($"+"v+) > '<'$"+"1'>';", "a > &PortUnit(abc);", "a > &[ab]PortUnit(abc);", "a > ∆PortUnit(abc);", ". > X;")
        for(rule in rules) {
            val source=sourceParser();val port=TransliteratorParser(factory)
            call(source,"parse",arrayOf(String::class.java,intType),rule,0);port.parse(rule,0)
            val data=(field(source,"dataVector") as List<*>)[0]!!
            val engine=rbc.getDeclaredConstructor(String::class.java,dc,com.ibm.icu.text.UnicodeFilter::class.java).apply{isAccessible=true}.newInstance("test",data,null)
            for(value in listOf("", "abc", "xaby", "aabbcc", "😀abc", "ab", "xabc"))for(inc in listOf(false,true)) {
                val st=com.ibm.icu.text.ReplaceableString(value);val pt=ReplaceableString(value)
                val sp=SourceTransliterator.Position(0,value.length,0,value.length);val pp=TransliterationPosition(0,value.length,0,value.length)
                assertEquals(error{call(engine,"handleTransliterate",arrayOf(SourceReplaceable::class.java,SourceTransliterator.Position::class.java,boolType),st,sp,inc)},error{port.dataVector[0].ruleSet.handleTransliterate(pt,pp,inc)},"$rule/$value/$inc")
                assertEquals(st.toString(),pt.toString(),"$rule/$value/$inc");assertContentEquals(intArrayOf(sp.start,sp.limit,sp.contextLimit),intArrayOf(pp.start,pp.limit,pp.contextLimit))
            }
        }
        SourceTransliterator.unregister("Any-PortUnit")
    }
    @Test fun idParsingCanonicalizationAndFilters() {
        initializeInverses()
        val ids=listOf("", "Latin-Greek", "Greek", "Any-Lower", "Lower", "[a-z] Lower", "Latin/UNGEGN-Greek", "Latin-Greek/UNGEGN", "(Greek-Latin)", "Latin-Greek()", "Lower(Upper)", "[a]Latin-Greek([b]Greek-Latin)", "[a];Lower;Upper;([b]);", "::", "-Greek", "/foo-Latin", "a--b", "Lower;Upper", "[bad", "Latin-", "[]", "( )", "Any-Null;", "Greek; ;", "😀", "𐐀-Greek", "[a] (", "Lower/foo(Upper/bar)")
        for(id in ids)for(dir in 0..1) {
            val sp=intArrayOf(0);val pp=intArrayOf(0)
            var source: Any?=null;var port: TransliteratorIDParser.SingleID?=null
            assertEquals(error{source=invoke(idClass,null,"parseSingleID",arrayOf(String::class.java,IntArray::class.java,intType),id,sp,dir)},error{port=TransliteratorIDParser.parseSingleID(id,pp,dir)},"$id/$dir")
            assertContentEquals(sp,pp)
            assertEquals(source?.let{listOf(field(it,"canonID"),field(it,"basicID"),field(it,"filter"))},port?.let{listOf(it.canonID,it.basicID,it.filter)})
            val sc=StringBuilder("old");val pc=StringBuilder("old")
            val sl=mutableListOf<Any>();val pl=mutableListOf<TransliteratorIDParser.SingleID>()
            val sf=arrayOfNulls<SourceSet>(1);val pf=arrayOfNulls<UnicodeSet>(1)
            var a: Any?=null;var b: Any?=null
            assertEquals(error{a=invoke(idClass,null,"parseCompoundID",arrayOf(String::class.java,intType,StringBuilder::class.java,java.util.List::class.java,sf.javaClass),id,dir,sc,sl,sf)},error{b=TransliteratorIDParser.parseCompoundID(id,dir,pc,pl,pf)})
            assertEquals(a,b);assertEquals(sc.toString(),pc.toString());assertEquals(sf[0]?.toPattern(false),pf[0]?.toPattern(false))
            assertEquals(sl.map{listOf(field(it,"canonID"),field(it,"basicID"),field(it,"filter"))},pl.map{listOf(it.canonID,it.basicID,it.filter)})
            assertEquals((invoke(idClass,null,"IDtoSTV",arrayOf(String::class.java),id) as Array<*>).toList(),TransliteratorIDParser.IDtoSTV(id).toList())
        }
    }
}
