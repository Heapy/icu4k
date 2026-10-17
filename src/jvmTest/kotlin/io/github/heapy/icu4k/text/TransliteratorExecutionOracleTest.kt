// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*
import java.lang.reflect.InvocationTargetException
import com.ibm.icu.text.Transliterator as Source
import com.ibm.icu.text.Replaceable as SourceText
import com.ibm.icu.text.ReplaceableString as SourceString
import com.ibm.icu.text.UnicodeSet as SourceSet

class TransliteratorExecutionOracleTest {
    private class PairEngine(rule: String,filter: String?) {
        val source=Source.createFromRules("test",rule,Source.FORWARD)
        val data=TransliteratorParser(TransliterationFactory { _,_ -> null }).apply{parse(rule,0)}.dataVector.single()
        val state=object:TransliteratorExecutionCore.Input {
            override val id="test"
            override var filter:UnicodeSet?=null
            override fun handleTransliterate(text:Replaceable,pos:TransliterationPosition,incremental:Boolean)=data.ruleSet.handleTransliterate(text,pos,incremental)
        }
        val port=TransliteratorExecutionCore(state)
        init{source.setFilter(filter?.let(::SourceSet));port.setFilter(filter?.let(::UnicodeSet))}
    }
    private fun error(action:()->Any?):String=try{val v=action();if(v==null||v==Unit)"OK"else "OK:$v"}catch(e:Throwable){"ERR:"+if(e is IndexOutOfBoundsException)"bounds"else e::class.java.simpleName+":"+e.message}
    private fun assertState(st:SourceString,pt:Replaceable,sp:Source.Position,pp:TransliterationPosition,message:String="") {
        assertEquals(st.toString(),pt.toString(),message)
        assertContentEquals(intArrayOf(sp.contextStart,sp.contextLimit,sp.start,sp.limit),intArrayOf(pp.contextStart,pp.contextLimit,pp.start,pp.limit),message)
    }
    @Test fun incrementalInsertionFinishAndFilteredBatch() {
        val rules=listOf("ab > X;", "a > AA; b > ;", "(ab) > '<'$"+"1'>';", "x{ab}y > Q;", "[ab]+ > Z;", "a > b|c;", ". > X;", "😀 > QQ;")
        val inputs=listOf("","abc","xaby","aabbab","😀ab",charArrayOf('a','\ud800','b','\udc00').concatToString())
        for(rule in rules)for(filter in listOf(null,"[a]","[ab]","[a-z😀]","[]"))for(value in inputs) {
            val e=PairEngine(rule,filter)
            for(start in -1..value.length)for(limit in maxOf(0,start)..value.length+1) {
                val st=SourceString(value);val pt=ReplaceableString(value)
                assertEquals(error{e.source.transliterate(st,start,limit)},error{e.port.transliterate(pt,start,limit)},"$rule/$filter/$value/$start/$limit")
                assertEquals(st.toString(),pt.toString())
            }
            val st=SourceString();val pt=ReplaceableString()
            val sp=Source.Position();val pp=TransliterationPosition()
            for(c in value) {
                assertEquals(error{e.source.transliterate(st,sp,c.toString())},error{e.port.transliterate(pt,pp,c.toString())},"$rule/$filter/$value/$c")
                assertState(st,pt,sp,pp)
            }
            assertEquals(error{e.source.finishTransliteration(st,sp)},error{e.port.finishTransliteration(pt,pp)})
            assertState(st,pt,sp,pp)
        }
    }
    @Test fun allPositionBoundsAndRawFilteredEntry() {
        for(filter in listOf(null,"[ab]","[]"))for(value in listOf("","abc","😀ab")) {
            val e=PairEngine("ab > XX;",filter)
            for(cs in -1..value.length)for(cl in cs..value.length+1)for(start in maxOf(0,cs)..maxOf(0,cl))for(limit in start..maxOf(start,cl))for(inc in listOf(false,true)) {
                val st=SourceString(value);val pt=ReplaceableString(value)
                val sp=Source.Position(cs,cl,start,limit);val pp=TransliterationPosition(cs,cl,start,limit)
                assertEquals(error{sp.validate(value.length)},error{pp.validate(value.length)})
                assertEquals(sp.toString(),pp.toString());assertEquals(sp.hashCode(),pp.hashCode())
                assertEquals(error{if(inc)e.source.transliterate(st,sp,null as String?)else e.source.finishTransliteration(st,sp)},error{if(inc)e.port.transliterate(pt,pp,null)else e.port.finishTransliteration(pt,pp)})
                assertState(st,pt,sp,pp)
            }
            for(start in 0..value.length)for(limit in start..value.length)for(inc in listOf(false,true)) {
                val st=SourceString(value);val pt=ReplaceableString(value)
                val sp=Source.Position(0,value.length,start,limit);val pp=TransliterationPosition(0,value.length,start,limit)
                assertEquals(error{e.source.filteredTransliterate(st,sp,inc)},error{e.port.filteredTransliterate(pt,pp,inc)})
                assertState(st,pt,sp,pp)
            }
        }
    }
    private class Traced(private val text:Replaceable,val trace:MutableList<String>):Replaceable {
        override fun length():Int {trace.add("length");return text.length()}
        override fun charAt(offset:Int):Char {trace.add("charAt:$offset");return text.charAt(offset)}
        override fun char32At(offset:Int):Int {trace.add("char32At:$offset");return text.char32At(offset)}
        override fun getChars(srcStart:Int,srcLimit:Int,dst:CharArray,dstStart:Int){trace.add("getChars:$srcStart,$srcLimit,$dstStart");text.getChars(srcStart,srcLimit,dst,dstStart)}
        override fun replace(start:Int,limit:Int,text:String){trace.add("replace:$start,$limit,$text");this.text.replace(start,limit,text)}
        override fun replace(start:Int,limit:Int,chars:CharArray,charsStart:Int,charsLen:Int){trace.add("replaceChars:$start,$limit,$charsStart,$charsLen");text.replace(start,limit,chars,charsStart,charsLen)}
        override fun copy(start:Int,limit:Int,dest:Int){trace.add("copy:$start,$limit,$dest");text.copy(start,limit,dest)}
        override fun hasMetaData():Boolean {trace.add("hasMetaData");return true}
    }
    @Test fun callbackFailuresRollbackAndMetadataTraces() {
        for(policy in 0..4)for(filter in listOf(null,"[ab]","[]"))for(value in listOf("","ab","a!b","😀ab"))for(entry in 0..2) {
            val sourceTrace=mutableListOf<String>();val portTrace=mutableListOf<String>()
            val source=object:Source("callback",null) {
                override fun handleTransliterate(text:SourceText,pos:Position,incremental:Boolean) {
                    sourceTrace.add("handle:${pos.contextStart},${pos.contextLimit},${pos.start},${pos.limit},$incremental")
                    if(policy==0){pos.start=pos.limit;return}
                    if(pos.start<pos.limit) {
                        text.replace(pos.start,pos.start+1,"QQ");++pos.limit;++pos.contextLimit
                        if(policy==1)pos.start=pos.limit
                        if(policy==2)throw IllegalStateException("callback")
                        if(policy==3)setFilter(null)
                    }
                }
            }
            val state=object:TransliteratorExecutionCore.Input {
                override val id="callback";override var filter:UnicodeSet?=null
                override fun handleTransliterate(text:Replaceable,pos:TransliterationPosition,incremental:Boolean) {
                    portTrace.add("handle:${pos.contextStart},${pos.contextLimit},${pos.start},${pos.limit},$incremental")
                    if(policy==0){pos.start=pos.limit;return}
                    if(pos.start<pos.limit) {
                        text.replace(pos.start,pos.start+1,"QQ");++pos.limit;++pos.contextLimit
                        if(policy==1)pos.start=pos.limit
                        if(policy==2)throw IllegalStateException("callback")
                        if(policy==3)this.filter=null
                    }
                }
            }
            val port=TransliteratorExecutionCore(state)
            source.setFilter(filter?.let(::SourceSet));port.setFilter(filter?.let(::UnicodeSet))
            val st=SourceString(value);val pt=ReplaceableString(value)
            val proxy=java.lang.reflect.Proxy.newProxyInstance(SourceText::class.java.classLoader,arrayOf(SourceText::class.java)) { _,method,args ->
                val a=args?:emptyArray();sourceTrace.add(if(a.isEmpty())method.name else method.name+":"+a.joinToString(","))
                if(method.name=="hasMetaData")true else try{method.invoke(st,*a)}catch(e:InvocationTargetException){throw e.cause!!}
            } as SourceText
            val traced=Traced(pt,portTrace)
            val sp=Source.Position(0,value.length,0,value.length);val pp=TransliterationPosition(0,value.length,0,value.length)
            val a=error{when(entry){0->source.filteredTransliterate(proxy,sp,true);1->source.transliterate(proxy,sp,null as String?);else->source.finishTransliteration(proxy,sp)}}
            val b=error{when(entry){0->port.filteredTransliterate(traced,pp,true);1->port.transliterate(traced,pp,null);else->port.finishTransliteration(traced,pp)}}
            assertEquals(a,b,"$policy/$filter/$value/$entry");assertEquals(sourceTrace,portTrace,"$policy/$filter/$value/$entry");assertState(st,pt,sp,pp)
            assertEquals((source.filter as SourceSet?)?.toPattern(false),state.filter?.toPattern(false))
        }
    }
    @Test fun filterCopyMaterializationAndBaseRules() {
        for(id in listOf("abc","a;B","אב😀",charArrayOf('a','\ud800').concatToString())) {
            val source=object:Source(id,null){override fun handleTransliterate(text:SourceText,pos:Position,incremental:Boolean){pos.start=pos.limit}}
            val state=object:TransliteratorExecutionCore.Input {
                override val id=id;override var filter:UnicodeSet?=null
                override fun handleTransliterate(text:Replaceable,pos:TransliterationPosition,incremental:Boolean){pos.start=pos.limit}
            }
            val port=TransliteratorExecutionCore(state)
            val base=Source::class.java.getDeclaredMethod("baseToRules",Boolean::class.javaPrimitiveType).apply{isAccessible=true}
            for(escape in listOf(false,true))assertEquals(base.invoke(source,escape),port.baseToRules(escape))
            val sf=SourceSet("[ab]");val pf=UnicodeSet("[ab]")
            source.setFilter(sf);port.setFilter(pf);sf.add('c'.code);pf.add('c'.code)
            assertEquals((source.filter as SourceSet).toPattern(false),state.filter!!.toPattern(false));assertTrue(state.filter!!.isFrozen())
            for(external in listOf("[a]","[a-z]","[]")) {
                val se=SourceSet(external);val pe=UnicodeSet(external)
                assertEquals(source.getFilterAsUnicodeSet(se).toPattern(false),port.getFilterAsUnicodeSet(pe).toPattern(false))
            }
            val sc=object:com.ibm.icu.text.UnicodeFilter(){
                override fun contains(c:Int)=c==97
                override fun toPattern(e:Boolean)="[a]"
                override fun matchesIndexValue(v:Int)=v==97
                override fun addMatchSetTo(s:SourceSet){s.add(97)}
            }
            val pc=object:UnicodeFilter(){
                override fun contains(c:Int)=c==97
                override fun toPattern(escapeUnprintable:Boolean)="[a]"
                override fun matchesIndexValue(v:Int)=v==97
                override fun addMatchSetTo(toUnionTo:UnicodeSet){toUnionTo.add(97)}
            }
            source.setFilter(sc);port.setFilter(pc)
            assertEquals((source.filter as SourceSet).toPattern(false),state.filter!!.toPattern(false));assertTrue(state.filter!!.isFrozen())
            source.setFilter(null);port.setFilter(null)
            val external=UnicodeSet("[a]");assertSame(external,port.getFilterAsUnicodeSet(external))
        }
    }

    @Test fun filterCallbackFailureAndReentrancy() {
        for(policy in 0..3) {
            val source=object:Source("filter",null){override fun handleTransliterate(text:SourceText,pos:Position,incremental:Boolean){pos.start=pos.limit}}
            val state=object:TransliteratorExecutionCore.Input {
                override val id="filter";override var filter:UnicodeSet?=null
                override fun handleTransliterate(text:Replaceable,pos:TransliterationPosition,incremental:Boolean){pos.start=pos.limit}
            }
            val port=TransliteratorExecutionCore(state)
            val sf=object:com.ibm.icu.text.UnicodeFilter(){
                override fun contains(c:Int)=false
                override fun toPattern(e:Boolean)="[]"
                override fun matchesIndexValue(v:Int)=false
                override fun addMatchSetTo(s:SourceSet){s.add(97);when(policy){0->throw IllegalStateException("filter");1->source.setFilter(null);2->source.setFilter(SourceSet("[b]"));else->s.freeze()}}
            }
            val pf=object:UnicodeFilter(){
                override fun contains(c:Int)=false
                override fun toPattern(escapeUnprintable:Boolean)="[]"
                override fun matchesIndexValue(v:Int)=false
                override fun addMatchSetTo(toUnionTo:UnicodeSet){toUnionTo.add(97);when(policy){0->throw IllegalStateException("filter");1->port.setFilter(null);2->port.setFilter(UnicodeSet("[b]"));else->toUnionTo.freeze()}}
            }
            fun result(action:()->Unit)=try{action();"OK"}catch(e:Throwable){e::class.java.simpleName}
            assertEquals(result{source.setFilter(sf)},result{port.setFilter(pf)})
            val a=source.filter as SourceSet?;val b=state.filter
            assertEquals(a?.toPattern(false),b?.toPattern(false));assertEquals(a?.isFrozen,b?.isFrozen())
        }
    }

}
