// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.CollationBuilder as Source
import kotlin.random.Random
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class CollationNodeGraphOracleTest {
    private fun call(a: Any,name: String,vararg args: Any?): Any? {
        val method=a.javaClass.declaredMethods.single { it.name==name && it.parameterCount==args.size }.apply { isAccessible=true }
        return try { method.invoke(a,*args).let { if(method.returnType==Void.TYPE) Unit else it } } catch(e: InvocationTargetException) { throw e.targetException }
    }
    private fun sourceCall(name: String,vararg args: Any?): Any? {
        val method=Source::class.java.declaredMethods.single { it.name==name && it.parameterCount==args.size }.apply { isAccessible=true }
        return try { method.invoke(null,*args) } catch(e: InvocationTargetException) { throw e.targetException }
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) { when(e) { is IndexOutOfBoundsException -> "bounds";is NullPointerException -> "null";else -> e.javaClass.simpleName+":"+e.message } }
    private fun state(a: Source,b: CollationNodeGraph) {
        for(name in listOf("nodes","rootPrimaryIndexes")) {
            val source=Source::class.java.getDeclaredField(name).apply { isAccessible=true }.get(a)
            val target=if(name=="nodes") b.nodes else b.rootPrimaryIndexes
            assertEquals(call(source,"size"),call(target,"size"))
            val sa=call(source,"getBuffer");val ta=call(target,"getBuffer")
            if(sa is LongArray) assertContentEquals(sa,ta as LongArray) else assertContentEquals(sa as IntArray,ta as IntArray)
        }
    }
    private fun op(a: Source,b: CollationNodeGraph,name: String,vararg args: Any?): Any? {
        val s=outcome { call(a,name,*args) };assertEquals(s,outcome { call(b,name,*args) },"$name/${args.toList()}");state(a,b);return s
    }
    @Test fun allBitEncodingsAndFullWidthJavaArithmetic() {
        val random=Random(78324);val companion=CollationNodeGraph.Companion
        val methods=listOf("tempCEFromIndexAndStrength","indexFromTempCE","strengthFromTempCE","isTempCE","indexFromTempCE32","isTempCE32","ceStrength","nodeFromWeight32","nodeFromWeight16","nodeFromPreviousIndex","nodeFromNextIndex","nodeFromStrength","weight32FromNode","weight16FromNode","previousIndexFromNode","nextIndexFromNode","strengthFromNode","nodeHasBefore2","nodeHasBefore3","nodeHasAnyBefore","isTailoredNode","changeNodePreviousIndex","changeNodeNextIndex")
        for(name in methods) repeat(1000) {
            val method=Source::class.java.declaredMethods.single { it.name==name }
            val args=method.parameterTypes.map { if(it==Long::class.javaPrimitiveType) random.nextLong() as Any else random.nextInt() as Any }.toTypedArray()
            assertEquals(outcome { sourceCall(name,*args) },outcome { call(companion,name,*args) },name)
        }
    }
    @Test fun graphMutationsBeforeWeightsAndPartialAllocationFailures() {
        val random=Random(78325);val data=CollationRoot.getData()
        for(case in 0 until 80) {
            val a=Source(com.ibm.icu.impl.coll.CollationRoot.getRoot());val b=CollationNodeGraph(data)
            val primaries=mutableListOf<Int>()
            for(c in listOf('a','b','c','x','z')) primaries.add(op(a,b,"findOrInsertNodeForRootCE",data.getSingleCE(c.code),0) as Int)
            repeat(70) { step ->
                val index=primaries.random(random)
                if(step%7==0) op(a,b,"findOrInsertWeakNode",index,Collation.BEFORE_WEIGHT16,1)
                else if(step%11==0) op(a,b,"findOrInsertWeakNode",index,0x600,1)
                else op(a,b,"insertTailoredNodeAfter",index,random.nextInt(4))
                if(step%13==0) op(a,b,"findCommonNode",index,random.nextInt(1,3))
            }
            op(a,b,"makeTailoredCEs")
        }
    }
    @Test fun precedingWeakWeightsForRootAndTailoredParents() {
        val a=Source(com.ibm.icu.impl.coll.CollationRoot.getRoot());val b=CollationNodeGraph(CollationRoot.getData())
        for(ce in listOf(CollationRoot.getData().getSingleCE('a'.code),CollationRoot.getData().getSingleCE(0x301),0L)) {
            val index=op(a,b,"findOrInsertNodeForRootCE",ce,2) as Int
            for(level in 1..2) op(a,b,"getWeight16Before",index,b.nodes.elementAti(index),level)
        }
        val root=op(a,b,"findOrInsertNodeForRootCE",CollationRoot.getData().getSingleCE('b'.code),0) as Int
        val tailored=op(a,b,"insertTailoredNodeAfter",root,0) as Int
        for(level in 1..2) op(a,b,"getWeight16Before",tailored,b.nodes.elementAti(tailored),level)
    }
    @Test fun realRuleGraphsAllocatedExactlyLikeOriginal() {
        val rules=listOf("&a<b<c<<d<<<e<<<<f", "&[before 2]a << à << á &a<b", "&[before 3]A <<< B <<< C", "&[first tertiary ignorable] <<< a", "&[first primary ignorable] << a", "&[last variable] < a < b", "&a<<<<b<<<<c<<<<d<<<<e")
        for(rule in rules) {
            val fresh=Source(com.ibm.icu.impl.coll.CollationRoot.getRoot());val graph=CollationNodeGraph(CollationRoot.getData())
            Source::class.java.getDeclaredField("variableTop").apply { isAccessible=true }.setLong(fresh,com.ibm.icu.impl.coll.CollationRoot.getRoot().settings.readOnly().variableTop)
            val parserClass=Class.forName("com.ibm.icu.impl.coll.CollationRuleParser")
            val parser=parserClass.getDeclaredConstructor(com.ibm.icu.impl.coll.CollationData::class.java).apply { isAccessible=true }.newInstance(com.ibm.icu.impl.coll.CollationRoot.getData())
            call(parser,"setSink",fresh)
            val settings=com.ibm.icu.impl.coll.CollationSettings::class.java.getDeclaredConstructor().apply { isAccessible=true }.newInstance()
            val parseResult=outcome { call(parser,"parse",rule,settings) }
            assertEquals(Unit,parseResult,"parse/$rule")
            for(name in listOf("nodes","rootPrimaryIndexes")) {
                val vector=Source::class.java.getDeclaredField(name).apply { isAccessible=true }.get(fresh)
                val count=call(vector,"size") as Int
                if(name=="nodes") for(i in 0 until count) graph.nodes.addElement(call(vector,"elementAti",i) as Long)
                else for(i in 0 until count) graph.rootPrimaryIndexes.addElement(call(vector,"elementAti",i) as Int)
            }
            op(fresh,graph,"makeTailoredCEs")
        }
    }
}
