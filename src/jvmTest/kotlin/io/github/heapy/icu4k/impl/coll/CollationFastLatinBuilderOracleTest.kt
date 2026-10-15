// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.coll.UVector64 as SourceVector

class CollationFastLatinBuilderOracleTest {
    private val type=Class.forName("com.ibm.icu.impl.coll.CollationFastLatinBuilder")
    private fun source(): Any=type.getDeclaredConstructor().also { it.isAccessible=true }.newInstance()
    private val methods=type.declaredMethods.onEach { it.isAccessible=true }.associateBy { it.name }
    private fun invoke(source: Any?,name: String,vararg args: Any?): Any?=try { methods.getValue(name).invoke(source,*args) } catch(e: InvocationTargetException) { throw e.targetException }
    private fun outcome(block: () -> Any?): Any?=try { block() } catch(e: Throwable) { when(e) { is NullPointerException -> "null";is IndexOutOfBoundsException -> "bounds";is NegativeArraySizeException -> "negative-size";else -> e.javaClass.simpleName+":"+e.message } }
    private val bridge=CollationDataOracleTest()
    private fun compareData(sd: com.ibm.icu.impl.coll.CollationData?,td: CollationData?) {
        val s=source();val t=CollationFastLatinBuilder()
        assertEquals(outcome { invoke(s,"forData",sd) },outcome { t.forData(td) })
        for(name in listOf("getHeader","getTable")) {
            val a=outcome { invoke(s,name) };val b=outcome { if(name=="getHeader") t.getHeader() else t.getTable() }
            if(a is CharArray && b is CharArray) assertContentEquals(a,b,name) else assertEquals(a,b,name)
        }
        for(name in listOf("ce0","ce1","firstDigitPrimary","firstLatinPrimary","lastLatinPrimary","firstShortPrimary","shortPrimaryOverflow","headerLength")) {
            assertEquals(type.getDeclaredField(name).also { it.isAccessible=true }.get(s),CollationFastLatinBuilder::class.java.getDeclaredField(name).also { it.isAccessible=true }.get(t),name)
        }
        for(name in listOf("lastSpecialPrimaries","miniCEs","charCEs","result")) {
            val a=type.getDeclaredField(name).also { it.isAccessible=true }.get(s);val b=CollationFastLatinBuilder::class.java.getDeclaredField(name).also { it.isAccessible=true }.get(t)
            when(a) { is LongArray -> assertContentEquals(a,b as LongArray);is CharArray -> assertContentEquals(a,b as CharArray);is Array<*> -> { b as Array<*>;for(i in a.indices) assertContentEquals(a[i] as LongArray,b[i] as LongArray) };else -> assertEquals(a?.toString(),b?.toString()) }
        }
        for(name in listOf("contractionCEs","uniqueCEs")) {
            val a=type.getDeclaredField(name).also { it.isAccessible=true }.get(s) as SourceVector
            val b=CollationFastLatinBuilder::class.java.getDeclaredField(name).also { it.isAccessible=true }.get(t) as UVector64
            assertEquals(a.size(),b.size());assertContentEquals(a.buffer,b.getBuffer(),name)
        }
        assertEquals(outcome { invoke(s,"forData",sd) },outcome { t.forData(td) })
    }
    @Test fun realRootTailoringsAndSyntheticOverflowTables() {
        val field=com.ibm.icu.text.RuleBasedCollator::class.java.getDeclaredField("data").also { it.isAccessible=true }
        val data=listOf(com.ibm.icu.impl.coll.CollationRoot.getData())+listOf("de@collation=phonebook","es@collation=traditional","cs","da","en_US_POSIX","fr","sv").map { field.get(com.ibm.icu.text.Collator.getInstance(com.ibm.icu.util.ULocale(it))) as com.ibm.icu.impl.coll.CollationData }
        for(sd in data) compareData(sd,bridge.importSource(sd))
        for((digits,latin) in listOf(0 to 1,0 to 59,0 to 60,50 to 20,129 to 20,0 to 100)) { val d=fastBuilderFixture(digits,latin);compareData(bridge.exportTarget(d),d) }
        val contexts=iteratorFixtureData(); val groups=fastBuilderFixture(0,1)
        contexts.numScripts=groups.numScripts;contexts.scriptsIndex=groups.scriptsIndex;contexts.scriptStarts=groups.scriptStarts
        compareData(bridge.exportTarget(contexts),contexts)
    }
    @Test fun nullMissingMetadataAndPartialFailures() {
        compareData(null,null)
        val empty=CollationData(null);compareData(bridge.exportTarget(empty),empty)
        for(i in 0..4) {
            val d=fastBuilderFixture(0,1);d.scriptsIndex!![26+i]=0.toChar();compareData(bridge.exportTarget(d),d)
        }
        val d=fastBuilderFixture(0,1);d.trie=null;compareData(bridge.exportTarget(d),d)
    }
    @Test fun unsignedOrderingBinarySearchAndLongVectorState() {
        val companion=CollationFastLatinBuilder::class.java.getDeclaredField("Companion").get(null)
        val comparator=companion.javaClass.getDeclaredMethod("compareInt64AsUnsigned",Long::class.javaPrimitiveType,Long::class.javaPrimitiveType).also { it.isAccessible=true }
        val search=companion.javaClass.getDeclaredMethod("binarySearch",LongArray::class.java,Int::class.javaPrimitiveType,Long::class.javaPrimitiveType).also { it.isAccessible=true }
        val random=Random(78371);val values=listOf(0L,-1L,Long.MIN_VALUE,Long.MAX_VALUE)+List(2000) { random.nextLong() }
        for(a in values) { val b=random.nextLong();assertEquals(invoke(null,"compareInt64AsUnsigned",a,b),comparator.invoke(companion,a,b)) }
        val sorted=values.distinct().sortedWith { a,b -> java.lang.Long.compareUnsigned(a,b) }.toLongArray()
        for(a in values+List(1000) { random.nextLong() }) assertEquals(invoke(null,"binarySearch",sorted,sorted.size,a),search.invoke(companion,sorted,sorted.size,a))
        val s=SourceVector();val t=UVector64()
        repeat(1000) {
            val value=random.nextLong();val index=random.nextInt(-2,s.buffer.size+3)
            when(random.nextInt(4)) {
                0 -> { s.addElement(value);t.addElement(value) }
                1 -> assertEquals(outcome { s.insertElementAt(value,index) },outcome { t.insertElementAt(value,index) })
                2 -> assertEquals(outcome { s.setElementAt(value,index) },outcome { t.setElementAt(value,index) })
                else -> { s.removeAllElements();t.removeAllElements() }
            }
            assertEquals(s.size(),t.size());assertContentEquals(s.buffer,t.getBuffer())
        }
    }
}
