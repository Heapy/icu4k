// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.coll.CollationSettings as Source
import com.ibm.icu.impl.coll.SharedObject as SourceShared

class CollationSettingsOracleTest {
    private fun source(): Source = Source::class.java.getDeclaredConstructor().also { it.isAccessible=true }.newInstance()
    private val methods = Source::class.java.declaredMethods.onEach { it.isAccessible=true }.associateBy { it.name to it.parameterCount }
    private fun call(s: Source?,name: String,vararg args: Any?): Any? = try { methods.getValue(name to args.size).let { method -> val value = method.invoke(s,*args); if (method.returnType == Void.TYPE) Unit else value } } catch(e: InvocationTargetException) { throw e.targetException }
    private fun field(s: Source,name: String): Any? = Source::class.java.getDeclaredField(name).also { it.isAccessible=true }.get(s)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is NegativeArraySizeException -> "negative-size"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun state(s: Source,t: CollationSettings) {
        assertEquals(s.options,t.options); assertEquals(s.variableTop,t.variableTop); assertEquals(s.fastLatinOptions,t.fastLatinOptions)
        assertContentEquals(s.fastLatinPrimaries,t.fastLatinPrimaries); assertContentEquals(s.reorderCodes,t.reorderCodes); assertContentEquals(s.reorderTable,t.reorderTable)
        assertEquals(field(s,"minHighNoReorder"),t.minHighNoReorder); assertContentEquals(field(s,"reorderRanges") as LongArray?,t.reorderRanges)
        assertEquals(outcome { s.hashCode() },outcome { t.hashCode() }); assertEquals(s.refCount,t.getRefCount())
    }
    @Test fun allOptionBitsAndRandomMutationHistories() {
        for (options in 0..0xffff) {
            assertEquals(call(null,"getStrength",options),CollationSettings.getStrength(options))
            assertEquals(call(null,"getTertiaryMask",options),CollationSettings.getTertiaryMask(options))
            assertEquals(call(null,"isTertiaryWithCaseBits",options),CollationSettings.isTertiaryWithCaseBits(options))
            assertEquals(call(null,"sortsTertiaryUpperCaseFirst",options),CollationSettings.sortsTertiaryUpperCaseFirst(options))
        }
        val s = source(); val t = CollationSettings(); val random = Random(783)
        repeat(1000) { step ->
            val value = random.nextInt(); val flag = random.nextBoolean()
            when(step % 9) {
                0 -> { s.options=value; t.options=value }
                1 -> { s.setFlag(value,flag); t.setFlag(value,flag) }
                2 -> { s.setFlagDefault(value,value.inv()); t.setFlagDefault(value,value.inv()) }
                3 -> { s.setStrengthDefault(value); t.setStrengthDefault(value) }
                4 -> { s.setCaseFirstDefault(value); t.setCaseFirstDefault(value) }
                5 -> { s.setAlternateHandlingDefault(value); t.setAlternateHandlingDefault(value) }
                6 -> { s.setAlternateHandlingShifted(flag); t.setAlternateHandlingShifted(flag) }
                7 -> assertEquals(outcome { s.setStrength(value % 17) },outcome { t.setStrength(value % 17) })
                else -> assertEquals(outcome { s.setMaxVariable(value % 6,value) },outcome { t.setMaxVariable(value % 6,value) })
            }
            state(s,t); assertEquals(s.strength,t.getStrength()); assertEquals(s.caseFirst,t.getCaseFirst()); assertEquals(s.maxVariable,t.getMaxVariable())
            assertEquals(s.alternateHandling,t.getAlternateHandling()); assertEquals(s.isNumeric,t.isNumeric()); assertEquals(s.dontCheckFCD(),t.dontCheckFCD()); assertEquals(call(s,"hasBackwardSecondary"),t.hasBackwardSecondary())
        }
        for (value in listOf(0,0x200,0x300,1,-1)) assertEquals(outcome { s.setCaseFirst(value) },outcome { t.setCaseFirst(value) })
    }
    @Test fun rootScriptReorderingSplitPrimariesAndAliasRoundTrips() {
        val sd = com.ibm.icu.impl.coll.CollationRoot.getData(); val td = CollationDataOracleTest().importSource(sd)
        val random = Random(7883)
        val codes = listOf(intArrayOf(),intArrayOf(103),intArrayOf(25),intArrayOf(14),intArrayOf(8,25),intArrayOf(4096,4097,14,103,25),intArrayOf(25,25),intArrayOf(-1),intArrayOf(5000)) + List(40) { intArrayOf(random.nextInt(0,180),103,random.nextInt(0,180)) }
        val s=source(); val t=CollationSettings()
        for (order in codes) {
            assertEquals(outcome { s.setReordering(sd,order) },outcome { t.setReordering(td,order) },order.toList().toString()); state(s,t)
            if (s.hasReordering()) {
                val primaries = List(300) { random.nextLong(0,0x100000000L) } + List(256) { it.toLong() shl 24 } + listOf(0L,1L,0xffffffffL,-1L,Long.MIN_VALUE,Long.MAX_VALUE)
                for (p in primaries) assertEquals(outcome { s.reorder(p) },outcome { t.reorder(p) },"${order.toList()}/$p")
                val activeOrder = s.reorderCodes
                val ranges = com.ibm.icu.impl.coll.UVector32(); sd.javaClass.getDeclaredMethod("makeReorderRanges",IntArray::class.java,ranges.javaClass).also { it.isAccessible=true }.invoke(sd,activeOrder,ranges)
                val serialized = activeOrder + ranges.buffer.copyOf(ranges.size())
                for (useTable in listOf(true,false)) {
                    val sa=source(); val ta=CollationSettings()
                    assertEquals(outcome { call(sa,"aliasReordering",sd,serialized,activeOrder.size,if (useTable) s.reorderTable else null) },outcome { ta.aliasReordering(td,serialized,activeOrder.size,if (useTable) t.reorderTable else null) }); state(sa,ta)
                    assertEquals(s.equals(sa),t.equals(ta))
                    for (p in primaries) assertEquals(outcome { sa.reorder(p) },outcome { ta.reorder(p) })
                }
            }
        }
    }
    @Test fun aliasesFailuresClonesAndConditionalEquality() {
        val s=source(); val t=CollationSettings()
        val arrays = listOf(intArrayOf(),intArrayOf(14),intArrayOf(14,0x20010000,0x30010001),intArrayOf(14,0x20010001,0x30010001))
        val tables = listOf(null,byteArrayOf(),ByteArray(256) { it.toByte() },ByteArray(256) { it.toByte() }.also { it[0x20]=0 })
        for (arr in arrays) for (length in listOf(-1,0,1,arr.size,arr.size+1)) for (table in tables) {
            assertEquals(outcome { call(s,"aliasReordering",null,arr,length,table) },outcome { t.aliasReordering(null,arr,length,table) },"${arr.toList()}/$length/${table?.size}"); state(s,t)
        }
        s.reorderCodes=null; t.reorderCodes=null
        assertEquals(s.equals(s.clone()),t.equals(t.clone())); state(s,t)
        s.options=0; t.options=0; s.variableTop=Long.MAX_VALUE; t.variableTop=Long.MAX_VALUE
        val sc=s.clone(); val tc=t.clone(); sc.variableTop=0; tc.variableTop=0
        assertEquals(s.equals(sc),t.equals(tc)); s.options=4;t.options=4;sc.options=4;tc.options=4;assertEquals(s.equals(sc),t.equals(tc))
        s.fastLatinPrimaries=null; t.fastLatinPrimaries=null
        assertEquals(outcome { s.clone() },outcome { t.clone() })
    }
    @Test fun sharedReferenceCopyOnWriteFailureAndFinalizationTokens() {
        val s=source(); val t=CollationSettings(); val a=SourceShared.Reference(s); val b=SharedObject.Reference(t)
        assertTrue(b is Cloneable)
        val ac=a.clone(); val bc=b.clone(); state(s,t)
        val ownS=a.copyOnWrite(); val ownT=b.copyOnWrite(); state(ownS,ownT); state(s,t)
        assertNotSame(s,ownS); assertNotSame(t,ownT); assertSame(s.reorderCodes,ownS.reorderCodes); assertSame(t.reorderCodes,ownT.reorderCodes)
        assertNotSame(s.fastLatinPrimaries,ownS.fastLatinPrimaries); assertNotSame(t.fastLatinPrimaries,ownT.fastLatinPrimaries)
        ac.clear();bc.clear();ac.clear();bc.clear();state(s,t)
        val finalize=SourceShared.Reference::class.java.getDeclaredMethod("finalize").also { it.isAccessible=true }; finalize.invoke(a)
        val token=SharedObject.Reference::class.java.getDeclaredField("cleanup").also { it.isAccessible=true }.get(b)
        token.javaClass.getDeclaredMethod("finalize").also { it.isAccessible=true }.invoke(token)
        assertNull(a.readOnly());assertNull(b.readOnly());state(ownS,ownT)
        val throwingS=object: SourceShared() { override fun clone(): SourceShared { throw IllegalStateException("clone failed") } }
        val throwingT=object: SharedObject() { override fun clone(): SharedObject { throw IllegalStateException("clone failed") } }
        val x=SourceShared.Reference(throwingS);val y=SharedObject.Reference(throwingT);val xx=x.clone();val yy=y.clone()
        assertEquals(outcome { x.copyOnWrite() },outcome { y.copyOnWrite() });assertEquals(throwingS.refCount,throwingT.getRefCount());assertSame(throwingT,y.readOnly())
        x.clear();y.clear();xx.clear();yy.clear()
    }
    @Test fun unknownJvmSubclassShallowCloneAndAtomicCounter() {
        class Extra: SharedObject() { var value=7; val array=intArrayOf(8) }
        val value=Extra();val clone=value.clone() as Extra;assertEquals(7,clone.value);assertSame(value.array,clone.array)
        assertFalse(java.io.Serializable::class.java.isInstance(value))
        val threads=List(4) { Thread { repeat(10000) { value.addRef() } }.also { it.start() } };threads.forEach { it.join() };assertEquals(40000,value.getRefCount())
        val removing=List(4) { Thread { repeat(10000) { value.removeRef() } }.also { it.start() } };removing.forEach { it.join() };assertEquals(0,value.getRefCount())
    }
}
