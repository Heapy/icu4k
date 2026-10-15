// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.DataBuilderOracle
import com.ibm.icu.impl.coll.CollationData as SourceData
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class CollationDataBuilderOracleTest {
    private val bridge=CollationDataOracleTest()
    private fun field(a: Any,name: String): Any? = a.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(a)
    private fun invoke(a: Any,name: String,vararg args: Any?): Any? {
        val method=a.javaClass.declaredMethods.single { m -> m.name==name && m.parameterCount==args.size && m.parameterTypes.withIndex().all { (i,type) -> args[i]==null || type.isInstance(args[i]) || type==Int::class.javaPrimitiveType && args[i] is Int || type==Long::class.javaPrimitiveType && args[i] is Long || type==Boolean::class.javaPrimitiveType && args[i] is Boolean } }.apply { isAccessible=true }
        return try { method.invoke(a,*args).let { if(method.returnType==Void.TYPE) Unit else it } } catch(e: InvocationTargetException) { throw e.targetException }
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) { when(e) { is NullPointerException -> "null";is IndexOutOfBoundsException -> "bounds";else -> e.javaClass.simpleName+":"+e.message } }
    private fun state(a: Any,b: CollationDataBuilder) {
        for(name in listOf("modified","fastLatinEnabled","contextsEra")) assertEquals(field(a,name),field(b,name),name)
        assertEquals(field(a,"contexts").toString(),b.contexts.toString())
        for(name in listOf("contextChars","unsafeBackwardSet")) {
            val s=field(a,name) as com.ibm.icu.text.UnicodeSet;val t=field(b,name) as io.github.heapy.icu4k.text.UnicodeSet
            assertEquals(s.toPattern(false),t.toPattern(false),name);assertEquals(s.isFrozen,t.isFrozen())
        }
        for(name in listOf("ce32s","ce64s")) {
            val s=field(a,name)!!;val t=field(b,name)!!;assertEquals(invoke(s,"size"),invoke(t,"size"))
            val sa=invoke(s,"getBuffer");val ta=invoke(t,"getBuffer")
            if(sa is IntArray) assertContentEquals(sa,ta as IntArray,name) else assertContentEquals(sa as LongArray,ta as LongArray,name)
        }
        val sc=field(a,"conditionalCE32s") as List<*>;assertEquals(sc.size,b.conditionalCE32s.size)
        for(i in sc.indices) for(name in listOf("context","ce32","defaultCE32","builtCE32","era","next")) assertEquals(field(sc[i]!!,name),field(b.conditionalCE32s[i],name),"cond/$i/$name")
        val st=field(a,"trie") as com.ibm.icu.impl.Trie2Writable?;val tt=b.trie
        assertEquals(st==null,tt==null)
        if(st!=null) {
            assertEquals(field(st,"isCompacted"),field(tt!!,"isCompacted"))
            for(c in listOf(-1,0,48,65,97,98,99,0x301,0x1100,0xac00,0xd800,0x1f600,0x10ffff)) assertEquals(st.get(c),tt!![c],"trie/$c")
        }
        val si=field(a,"collIter") as com.ibm.icu.impl.coll.CollationIterator?;val ti=field(b,"collIter") as CollationIterator?
        assertEquals(si==null,ti==null)
        if(si!=null) {
            assertEquals(si.offset,ti!!.getOffset());assertEquals(si.getCEsLength(),ti.getCEsLength());assertContentEquals(si.getCEs(),ti.getCEs())
            assertEquals(field(si,"s").toString(),field(ti,"s").toString())
        }
        assertEquals(field(a,"fastLatinBuilder")==null,b.fastLatinBuilder==null)
    }
    private fun operation(a: Any,b: CollationDataBuilder,name: String,sourceArgs: Array<out Any?>,targetArgs: Array<out Any?> = sourceArgs) {
        assertEquals(outcome { invoke(a,name,*sourceArgs) },outcome { invoke(b,name,*targetArgs) },name);state(a,b)
    }
    private fun pair(sourceBase: SourceData=com.ibm.icu.impl.coll.CollationRoot.getData()): Pair<Any,CollationDataBuilder> {
        val a=DataBuilderOracle.create();val b=CollationDataBuilder();invoke(a,"initForTailoring",sourceBase);b.initForTailoring(bridge.importSource(sourceBase));state(a,b);return a to b
    }
    private fun getCEs(a: Any,b: CollationDataBuilder,prefix: String,s: String,start: Int=0) {
        val aa=LongArray(40) { 123 };val bb=aa.copyOf()
        operation(a,b,"getCEs",arrayOf(prefix,s,aa,start),arrayOf(prefix,s,bb,start));assertContentEquals(aa,bb)
    }
    private fun build(a: Any,b: CollationDataBuilder) {
        val data=CollationData(b.nfcImpl);val sd=bridge.exportTarget(CollationData(b.nfcImpl))
        assertEquals(outcome { invoke(a,"build",sd) },outcome { b.build(data) });state(a,b)
        val expected=bridge.importSource(sd)
        assertContentEquals(expected.trie?.serialize(),data.trie?.serialize());assertContentEquals(expected.ce32s,data.ce32s);assertContentEquals(expected.ces,data.ces)
        assertContentEquals(expected.jamoCE32s,data.jamoCE32s);assertEquals(expected.contexts,data.contexts);assertEquals(expected.unsafeBackwardSet,data.unsafeBackwardSet)
        assertContentEquals(expected.fastLatinTableHeader,data.fastLatinTableHeader);assertContentEquals(expected.fastLatinTable,data.fastLatinTable)
        assertEquals(expected.numericPrimary,data.numericPrimary);assertEquals(expected.numScripts,data.numScripts)
        assertEquals(field(sd,"fastLatinTable")===field(field(a,"base")!!,"fastLatinTable"),data.fastLatinTable===b.base!!.fastLatinTable)
    }
    @Test fun mappingHistoriesContextCachesAndFinalData() {
        val random=Random(78322);val dataField=com.ibm.icu.text.RuleBasedCollator::class.java.getDeclaredField("data").apply { isAccessible=true }
        val bases=listOf(com.ibm.icu.impl.coll.CollationRoot.getData())+listOf("es@collation=traditional","de@collation=phonebook","th").map { dataField.get(com.ibm.icu.text.Collator.getInstance(com.ibm.icu.util.ULocale(it))) as SourceData }
        for(base in bases) {
            val (a,b)=pair(base)
            for(i in 0 until 90) {
                val prefix=listOf("","x","xy","😀")[i%4];val s=listOf("a","ab","abc","a\u0301","ch","ᄀ","3","😀")[i%8]
                val values=LongArray(1+i%4) { if(i%3==0) compareCE(0x30000000+random.nextInt(100)*0x10000L) else (random.nextLong() and -49153L) }
                operation(a,b,"add",arrayOf(prefix,s,values,values.size));getCEs(a,b,prefix,s)
                if(i%7==0) operation(a,b,"clearContexts",emptyArray())
            }
            operation(a,b,"enableFastLatin",emptyArray());build(a,b)
            operation(a,b,"encodeCEs",arrayOf(longArrayOf(0),1));operation(a,b,"build",arrayOf(bridge.exportTarget(CollationData(b.nfcImpl))),arrayOf(CollationData(b.nfcImpl)))
        }
    }
    @Test fun failuresPartialWritesAndBaseTableReuse() {
        for(length in listOf(-1,0,1,2,3,31,32)) {
            val (a,b)=pair();operation(a,b,"encodeCEs",arrayOf(null,length))
            operation(a,b,"encodeCEs",arrayOf(longArrayOf(0,0),length))
        }
        for(capacity in 0..4) for(start in listOf(-1,0,2,30,31,32)) {
            val (a,b)=pair();val aa=LongArray(capacity) { 99 };val bb=aa.copyOf()
            operation(a,b,"getCEs",arrayOf("abcdef",aa,start),arrayOf("abcdef",bb,start));assertContentEquals(aa,bb)
        }
        for(what in listOf("empty","null","hangul")) {
            val (a,b)=pair();val text=when(what) { "empty" -> "";"null" -> null;else -> "각" }
            operation(a,b,"add",arrayOf("x",text,longArrayOf(compareCE(0x30000000)),1))
        }
        val (a,b)=pair();operation(a,b,"build",arrayOf(null));operation(a,b,"build",arrayOf(null))
        val (c,d)=pair();operation(c,d,"enableFastLatin",emptyArray());build(c,d)
        val fresh=CollationTailoring(null);CollationDataReader.read(null,io.github.heapy.icu4k.impl.ICUBinary.getRequiredData("coll/ucadata.icu"),fresh)
        val base=fresh.data!!;val fast=CollationFastLatinBuilder();assertTrue(fast.forData(base));base.fastLatinTableHeader=fast.getHeader();base.fastLatinTable=fast.getTable()
        val (e,f)=pair(bridge.exportTarget(base));operation(e,f,"optimize",arrayOf(com.ibm.icu.text.UnicodeSet().add(0)),arrayOf(io.github.heapy.icu4k.text.UnicodeSet().add(0)));operation(e,f,"enableFastLatin",emptyArray());build(e,f)
    }
    @Test fun encodeShapesCopyModifiersOptimizationAndSuppression() {
        val (a,b)=pair();val random=Random(78323)
        repeat(500) { i ->
            val values=LongArray(i%7) { if(i%4==0) compareCE(0x30000000+it*0x10000L) else random.nextLong() and -49153L }
            operation(a,b,"encodeCEs",arrayOf(values,values.size))
        }
        for((p,s) in listOf("" to "a","" to "ab","x" to "a","xy" to "abc")) operation(a,b,"add",arrayOf(p,s,longArrayOf(compareCE(0x30000000),compareCE(0x40000000)),2))
        operation(a,b,"add",arrayOf("","m",longArrayOf(compareCE(0x30010000),compareCE(0x40000000)),2))
        val expanded=(0x30040203L shl 32) or 0x06070506L
        operation(a,b,"add",arrayOf("","n",longArrayOf(expanded,expanded+1),2))
        for(fail in 1..5) {
            val (sa,tb)=pair();var count=0
            val modifier=object: CollationDataBuilder.CEModifier {
                fun check() { if(++count==fail) throw IllegalArgumentException("modifier") }
                override fun modifyCE32(ce32: Int): Long { check();return Collation.NO_CE }
                override fun modifyCE(ce: Long): Long { check();return Collation.NO_CE }
            }
            operation(sa,tb,"copyFrom",arrayOf(a,DataBuilderOracle.failingModifier(fail)),arrayOf(b,modifier))
        }
        for(change in listOf(false,true)) {
            val (sa,tb)=pair()
            val modifier=object: CollationDataBuilder.CEModifier {
                override fun modifyCE32(ce32: Int): Long = if(change && ce32 and 0x10000!=0) Collation.ceFromCE32(ce32)+0x100000000L else Collation.NO_CE
                override fun modifyCE(ce: Long): Long = if(change && ce and 1L==0L) ce+0x100000000L else Collation.NO_CE
            }
            operation(sa,tb,"copyFrom",arrayOf(a,DataBuilderOracle.modifier(change)),arrayOf(b,modifier))
            getCEs(sa,tb,"xy","abc");getCEs(sa,tb,"","mn");build(sa,tb)
        }
        for(method in listOf("optimize","suppressContractions")) {
            val s=com.ibm.icu.text.UnicodeSet("[a-z]");val t=io.github.heapy.icu4k.text.UnicodeSet("[a-z]")
            operation(a,b,method,arrayOf(s),arrayOf(t))
        }
        val (copyA,copyB)=pair()
        val unchanged=object: CollationDataBuilder.CEModifier {
            override fun modifyCE32(ce32: Int): Long = Collation.NO_CE
            override fun modifyCE(ce: Long): Long = Collation.NO_CE
        }
        operation(copyA,copyB,"copyFrom",arrayOf(a,DataBuilderOracle.modifier(false)),arrayOf(b,unchanged));build(copyA,copyB)
        build(a,b)
    }
}
