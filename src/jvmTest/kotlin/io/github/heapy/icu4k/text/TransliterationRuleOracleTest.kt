// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*
import java.lang.reflect.InvocationTargetException
import com.ibm.icu.text.Replaceable as SourceReplaceable
import com.ibm.icu.text.ReplaceableString as SourceText
import com.ibm.icu.text.Transliterator.Position as SourcePosition
import com.ibm.icu.text.UnicodeSet as SourceSet

class TransliterationRuleOracleTest {
    private val dc = Class.forName("com.ibm.icu.text.RuleBasedTransliterator\$Data")
    private val rc = Class.forName("com.ibm.icu.text.TransliterationRule")
    private val rsc = Class.forName("com.ibm.icu.text.TransliterationRuleSet")
    private val matcherArrayClass = java.lang.reflect.Array.newInstance(com.ibm.icu.text.UnicodeMatcher::class.java, 0).javaClass
    private fun construct(type: Class<*>, types: Array<Class<*>>, vararg args: Any?): Any = try {
        type.getDeclaredConstructor(*types).apply { isAccessible = true }.newInstance(*args)
    } catch(e: InvocationTargetException) { throw e.cause!! }
    private fun call(obj: Any, name: String, types: Array<Class<*>> = emptyArray(), vararg args: Any?): Any? = try {
        obj.javaClass.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(obj, *args)
    } catch(e: InvocationTargetException) { throw e.cause!! }
    private val intType = Int::class.javaPrimitiveType!!
    private val boolType = Boolean::class.javaPrimitiveType!!
    private val ctor = arrayOf(String::class.java, intType, intType, String::class.java, intType, intType, matcherArrayClass, boolType, boolType, dc)
    private val dataPair = run {
        val source = construct(dc, emptyArray())
        dc.getDeclaredField("variablesBase").apply { isAccessible = true }.setChar(source, '\ue000')
        dc.getDeclaredField("variables").apply { isAccessible = true }.set(source, arrayOf(SourceSet("[a-z😀]")))
        source to TransliterationData().apply { variablesBase = '\ue000'; variables = arrayOf(UnicodeSet("[a-z😀]")) }
    }
    private val sourceData get() = dataPair.first
    private val portData get() = dataPair.second
    private data class Spec(val input: String, val ante: Int, val post: Int, val output: String,
        val cursor: Int = -1, val offset: Int = 0, val start: Boolean = false, val end: Boolean = false)
    private fun source(s: Spec) = construct(rc, ctor, s.input,s.ante,s.post,s.output,s.cursor,s.offset,null,s.start,s.end,sourceData)
    private fun port(s: Spec) = TransliterationRule(s.input,s.ante,s.post,s.output,s.cursor,s.offset,null,s.start,s.end,portData)
    private fun outcome(action: () -> Any?): String = try { val result = action(); if (result == null || result == Unit) "OK" else "OK:" + result } catch(e: Throwable) {
        "ERR:" + (if (e is IndexOutOfBoundsException) "bounds" else e::class.java.simpleName) + (if (e is IllegalArgumentException && e !is IndexOutOfBoundsException) ":" + e.message else "")
    }
    private val specs = listOf(
        Spec("a",-1,-1,"xyz"), Spec("ab",0,1,"q"), Spec("ab",1,-1,""), Spec("abc",1,2,"XY",0),
        Spec("abc",1,2,"q",1,-2), Spec("abc",1,2,"😀",1,3), Spec("",-1,-1,"z"),
        Spec("ab",2,2,"!",-1), Spec("a",0,0,"q"), Spec("😀a",2,-1,"!"), Spec("a😀b",1,3,"?"),
        Spec("\ue000a",0,1,"Q"), Spec("x\ue000y",1,2,"A"), Spec("\ue000",-1,-1,"a b'\\"),
        Spec(charArrayOf('\ud800','a').concatToString(),0,-1,"b"), Spec("a",3,-1,"x"), Spec("ab",1,0,"x"), Spec("a",0,1,"x",2)
    )
    @Test fun contextualMatchReplacementAnchorsAndPartialInput() {
        val texts = listOf("", "a", "ab", "abc", "xabcq", "xxabc", "😀a", "a😀b", "x😀y", "zaaz")
        for (base in specs) for (anchor in 0..3) {
            val spec = base.copy(start = anchor and 1 != 0, end = anchor and 2 != 0)
            var source: Any? = null; var port: TransliterationRule? = null
            assertEquals(outcome { source = source(spec) }, outcome { port = port(spec) }, spec.toString())
            if (source == null || port == null) continue
            for (escape in listOf(false,true)) assertEquals(call(source!!,"toRule",arrayOf(boolType),escape),port!!.toRule(escape))
            assertEquals(call(source!!,"getAnteContextLength"),port!!.getAnteContextLength())
            assertEquals(call(source!!,"getIndexValue"),port!!.getIndexValue())
            for (value in texts) for (start in 0..value.length) for (limit in start..value.length) for (incremental in listOf(false,true)) {
                val st=SourceText(value); val pt=ReplaceableString(value)
                val sp=SourcePosition(0,value.length,start,limit); val pp=TransliterationPosition(0,value.length,start,limit)
                assertEquals(outcome { call(source!!,"matchAndReplace",arrayOf(SourceReplaceable::class.java,SourcePosition::class.java,boolType),st,sp,incremental) },outcome { port!!.matchAndReplace(pt,pp,incremental) },"$spec/$value/$start/$limit/$incremental")
                assertEquals(st.toString(),pt.toString()); assertContentEquals(intArrayOf(sp.contextStart,sp.contextLimit,sp.start,sp.limit),intArrayOf(pp.contextStart,pp.contextLimit,pp.start,pp.limit))
            }
            for (filter in listOf("[]","[a-z]","[a]","[\\u0000-\\U0010ffff]")) {
                val ss=SourceSet(); val st=SourceSet(); val ps=UnicodeSet(); val pt=UnicodeSet()
                call(source!!,"addSourceTargetSet",Array(4){SourceSet::class.java},SourceSet(filter),ss,st,SourceSet())
                port!!.addSourceTargetSet(UnicodeSet(filter),ps,pt,UnicodeSet())
                assertEquals(ss.toPattern(false),ps.toPattern(false)); assertEquals(st.toPattern(false),pt.toPattern(false))
            }
        }
    }
    @Test fun maskingAndFrozenRuleSelection() {
        val valid=specs.filter { it.ante <= it.input.length && (it.post < 0 || it.post >= maxOf(0,it.ante)) && it.cursor <= it.output.length }
        val sr=valid.map(::source); val pr=valid.map(::port)
        for (i in sr.indices) for (j in sr.indices) assertEquals(call(sr[i],"masks",arrayOf(rc),sr[j]),pr[i].masks(pr[j]))
        val groups=listOf(listOf(Spec("a",-1,-1,"A"),Spec("b",-1,-1,"BB")),listOf(Spec("a",-1,-1,"A"),Spec("ab",-1,-1,"B")),listOf(Spec("\ue000",-1,-1,"x"),Spec("z",-1,-1,"y")),listOf(Spec("abc",1,2,"!"),Spec("ab",0,1,"Q")))
        for (group in groups) {
            val source=construct(rsc,emptyArray()); val port=TransliterationRuleSet()
            for(spec in group) { call(source,"addRule",arrayOf(rc),source(spec));port.addRule(port(spec)) }
            assertEquals(call(source,"getMaximumContextLength"),port.getMaximumContextLength())
            val a=outcome{call(source,"freeze")};val b=outcome{port.freeze()};assertEquals(a,b)
            for(escape in listOf(false,true))assertEquals(call(source,"toRules",arrayOf(boolType),escape),port.toRules(escape))
            // Source installs the dispatch tables even when masking validation throws.
            for(value in listOf("abc","z😀a","xabcy","ab"))for(incremental in listOf(false,true)) {
                val st=SourceText(value);val pt=ReplaceableString(value)
                val sp=SourcePosition(0,value.length,0,value.length);val pp=TransliterationPosition(0,value.length,0,value.length)
                repeat(20) {
                    if(sp.start<sp.limit) {
                        assertEquals(call(source,"transliterate",arrayOf(SourceReplaceable::class.java,SourcePosition::class.java,boolType),st,sp,incremental),port.transliterate(pt,pp,incremental))
                        assertEquals(st.toString(),pt.toString());assertContentEquals(intArrayOf(sp.start,sp.limit,sp.contextLimit),intArrayOf(pp.start,pp.limit,pp.contextLimit))
                    }
                }
            }
        }
    }
}
