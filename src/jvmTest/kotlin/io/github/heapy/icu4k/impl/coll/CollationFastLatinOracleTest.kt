// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.coll.CollationFastLatin as Source
import com.ibm.icu.impl.coll.CollationSettings as SourceSettings
import com.ibm.icu.impl.coll.CollationData as SourceData

class CollationFastLatinOracleTest {
    private val sourceMethods=Source::class.java.declaredMethods.onEach { it.isAccessible=true }.associateBy { it.name }
    private val targetMethods=CollationFastLatin::class.java.declaredMethods.onEach { it.isAccessible=true }.associateBy { it.name }
    private fun call(target: Boolean,name: String,vararg args: Any?): Any? = try { (if(target) targetMethods else sourceMethods).getValue(name).invoke(if(target) CollationFastLatin else null,*args) } catch(e: InvocationTargetException) { throw e.targetException }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) { when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message } }
    private fun ss(s: CollationSettings): SourceSettings=SourceSettings::class.java.getDeclaredConstructor().also { it.isAccessible=true }.newInstance().also { a ->
        for(name in listOf("options","variableTop","reorderTable","reorderCodes","reorderRanges","minHighNoReorder")) SourceSettings::class.java.getDeclaredField(name).also { it.isAccessible=true }.set(a,CollationSettings::class.java.getDeclaredField(name).also { it.isAccessible=true }.get(s))
    }
    @Test fun constantsIndexesAndEverySingleMiniCE() {
        for(field in Source::class.java.declaredFields.filter { it.type==Int::class.javaPrimitiveType && it.name.uppercase()==it.name }) {
            field.isAccessible=true; val target=CollationFastLatin::class.java.getDeclaredField(field.name).also { it.isAccessible=true }; assertEquals(field.getInt(null),target.getInt(null),field.name)
        }
        for(c in 0..0xffff) assertEquals(call(false,"getCharIndex",c.toChar()),CollationFastLatin.getCharIndex(c.toChar()))
        for(ce in 0..0xffff) {
            assertEquals(call(false,"getSecondariesFromOneShortCE",ce),call(true,"getSecondariesFromOneShortCE",ce))
            for(top in listOf(0xbff,0xc08,0xff8)) for(name in listOf("getPrimaries","getSecondaries","getQuaternaries")) assertEquals(outcome { call(false,name,top,ce) },outcome { call(true,name,top,ce) },"$name/$top/$ce")
            for(flag in listOf(false,true)) for(name in listOf("getCases","getTertiaries")) assertEquals(outcome { call(false,name,0xc08,flag,ce) },outcome { call(true,name,0xc08,flag,ce) },"$name/$flag/$ce")
        }
        val random=Random(7834)
        for(pair in listOf(Int.MIN_VALUE,Int.MAX_VALUE,0xffff0000.toInt(),0x10001000,0x0c000c00,0x04000400)+List(3000) { random.nextInt() }) for(top in listOf(0xbff,0xc08,0xff8)) {
            for(name in listOf("getPrimaries","getSecondaries","getQuaternaries")) assertEquals(outcome { call(false,name,top,pair) },outcome { call(true,name,top,pair) },"$name/$pair")
            for(flag in listOf(false,true)) for(name in listOf("getCases","getTertiaries")) assertEquals(outcome { call(false,name,top,flag,pair) },outcome { call(true,name,top,flag,pair) },"$name/$pair")
        }
    }
    @Test fun realTablesOptionsReorderingAndUTF16Comparison() {
        val bridge=CollationDataOracleTest(); val dataField=com.ibm.icu.text.RuleBasedCollator::class.java.getDeclaredField("data").also { it.isAccessible=true }
        val sources=listOf(com.ibm.icu.impl.coll.CollationRoot.getData())+listOf("de@collation=phonebook","es@collation=traditional","cs","en_US_POSIX").map { dataField.get(com.ibm.icu.text.Collator.getInstance(com.ibm.icu.util.ULocale(it))) as SourceData }
        val random=Random(78034); val pieces=listOf("a","A","á","æ","œ","ß","ch","ll","é","e\u0301","-"," ","1","2","\u2000","\u203f","☃","😀","\ufffe","\uffff","\u0000")
        val pairs=listOf("a" to "A","æ" to "ae","ch" to "d","é" to "e\u0301","a-b" to "ab","12" to "2","" to " ","\ufffe" to "\uffff")+List(80) { val prefix=buildString { repeat(random.nextInt(0,7)) { append(pieces.random(random)) } }; prefix+pieces.random(random) to prefix+pieces.random(random) }
        for(sd in sources) {
            val td=bridge.importSource(sd)
            for(strength in listOf(0,1,2,3,15)) for(flags in listOf(0,4,2,0x200,0x300,0x400,0x700,0x800,0x804)) for(order in listOf(intArrayOf(),intArrayOf(14,25),intArrayOf(4100,25))) {
                val t=CollationSettings().apply { options=(strength shl 12) or flags or 0x10; setReordering(td,order) }; val s=ss(t)
                val sp=CharArray(384) { 0x1234.toChar() }; val tp=sp.copyOf()
                val so=Source.getOptions(sd,s,sp); val to=CollationFastLatin.getOptions(td,t,tp); assertEquals(so,to); assertContentEquals(sp,tp)
                if(so >= 0) for((a,b) in pairs) for(start in listOf(0,minOf(a.length,b.length))) assertEquals(outcome { Source.compareUTF16(sd.fastLatinTable,sp,so,a,b,start) },outcome { CollationFastLatin.compareUTF16(td.fastLatinTable,tp,to,a,b,start) },"$strength/$flags/${order.toList()}/$a/$b/$start")
            }
        }
    }
    @Test fun contractionsExpansionsCallbackOrderAndLazyFailures() {
        val d=fastLatinFixture(); val table=d.fastLatinTable!!; val primaries=CharArray(384); val settings=CollationSettings(); val options=CollationFastLatin.getOptions(d,settings,primaries)
        for(text in listOf("c","ch","ca","c☃","c\ufffe","c\uffff","c\u2000","e","\u0000","☃","abc")) for(index in listOf(-1,0,text.length,text.length+1)) {
            val a=Recording(text); val b=Recording(text); val c=Recording("ab"); val e=Recording("ab")
            assertEquals(outcome { Source.compareUTF16(table,primaries,options,a,c,index) },outcome { CollationFastLatin.compareUTF16(table,primaries,options,b,e,index) }); assertEquals(a.calls,b.calls); assertEquals(c.calls,e.calls)
        }
        for(ce in listOf(0,1,0x400,0x805,0xc00,0x10a8)) for(text in listOf("","h","a","☃","\ufffe","\uffff","\u2000")) assertEquals(outcome { call(false,"nextPair",table,'c'.code,ce,text,0) },outcome { call(true,"nextPair",table,'c'.code,ce,text,0) })
        val sd=CollationDataOracleTest().exportTarget(d)
        for(header in listOf<CharArray?>(null,charArrayOf(),charArrayOf(0x0105.toChar()),d.fastLatinTableHeader)) for(size in listOf(0,383,384,385)) {
            sd.javaClass.getDeclaredField("fastLatinTableHeader").also { it.isAccessible=true }.set(sd,header); d.fastLatinTableHeader=header
            val a=CharArray(size) { 'x' };val b=a.copyOf(); assertEquals(outcome { Source.getOptions(sd,ss(settings),a) },outcome { CollationFastLatin.getOptions(d,settings,b) }); assertContentEquals(a,b)
        }
    }
    private class Recording(private val text: String): CharSequence {
        val calls=mutableListOf<String>()
        override val length: Int get() { calls.add("length"); return text.length }
        override fun get(index: Int): Char { calls.add("get:$index"); return text[index] }
        override fun subSequence(startIndex: Int,endIndex: Int): CharSequence=text.subSequence(startIndex,endIndex)
    }
}
