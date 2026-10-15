// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.text.RuleBasedCollator as Source
import com.ibm.icu.impl.coll.CollationSettings as SourceSettings
import com.ibm.icu.impl.coll.SharedObject as SourceShared
import kotlin.random.Random
import kotlin.test.*

class RuleBasedCollatorSettingsOracleTest {
    private fun field(a: Any,name: String): Any? = a.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(a)
    @Suppress("UNCHECKED_CAST") private fun settings(a: Source): SourceSettings = (field(a,"settings") as SourceShared.Reference<SourceSettings>).readOnly()
    private fun defaults(a: Source): SourceSettings = (field(a,"tailoring") as com.ibm.icu.impl.coll.CollationTailoring).settings.readOnly()
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun state(a: Source,b: ConfiguredCollatorFixture,label: String) {
        val s=settings(a); val t=b.reference.readOnly()!!; val c=b.config
        assertEquals(s.options,t.options,label); assertEquals(s.variableTop,t.variableTop,label)
        assertEquals(s.fastLatinOptions,t.fastLatinOptions,label); assertContentEquals(s.fastLatinPrimaries,t.fastLatinPrimaries,label)
        assertContentEquals(s.reorderCodes,t.reorderCodes,label); assertContentEquals(s.reorderTable,t.reorderTable,label)
        assertEquals(field(s,"minHighNoReorder"),t.minHighNoReorder,label); assertContentEquals(field(s,"reorderRanges") as LongArray?,t.reorderRanges,label)
        assertEquals(s===defaults(a),t===b.tailoring.settings!!.readOnly(),label)
        assertEquals(listOf(a.strength,a.decomposition,a.isUpperCaseFirst,a.isLowerCaseFirst,a.isAlternateHandlingShifted,a.isCaseLevel,a.isFrenchCollation,a.isHiraganaQuaternary,a.variableTop,a.numericCollation,a.maxVariable),
            listOf(c.getStrength(),c.getDecomposition(),c.isUpperCaseFirst(),c.isLowerCaseFirst(),c.isAlternateHandlingShifted(),c.isCaseLevel(),c.isFrenchCollation(),c.isHiraganaQuaternary(),c.getVariableTop(),c.getNumericCollation(),c.getMaxVariable()),label)
        assertContentEquals(a.reorderCodes,c.getReorderCodes(),label)
        for(text in listOf("aAé","123٤٥"," æ-.$","😀中")) assertContentEquals(a.getCollationKey(text).toByteArray(),b.core.getCollationKey(text)!!.toByteArray(),label)
        for((x,y) in listOf("2" to "10","A" to "a","a" to "á","一" to "a")) assertEquals(a.compare(x,y),b.core.doCompare(x,y),label)
    }
    @Test fun configurationHistoriesPartialFailuresAndDefaults() {
        val random=Random(78433)
        val orders=listOf<IntArray?>(null,intArrayOf(),intArrayOf(103),intArrayOf(-1),intArrayOf(17,25),intArrayOf(25,17),intArrayOf(25,25),intArrayOf(-1,25),intArrayOf(4096,25,103,17),intArrayOf(5000))
        for(rule in listOf("", "[strength 1][numericOrdering on][caseFirst upper]&a<ä", "[backwards 2][alternate shifted][maxVariable currency][reorder Hani Latn]&c<ch")) {
            var a=Source(rule); var b=ConfiguredCollatorFixture(CollationBuilder(CollationRoot.getRoot()).parseAndBuild(rule))
            // Keep both generations alive so GC timing cannot alter COW ownership.
            val sourceOwners=mutableListOf<Source>()
            val targetOwners=mutableListOf<ConfiguredCollatorFixture>()
            repeat(500) { step ->
                val op=random.nextInt(25); val flag=random.nextBoolean()
                val strength=listOf(-1,0,1,2,3,4,15,16,Int.MAX_VALUE).random(random)
                val decomposition=listOf(-1,0,15,16,17,18).random(random)
                val group=listOf(-2,-1,4095,4096,4097,4098,4099,4100,Int.MAX_VALUE).random(random)
                val order=orders.random(random)
                val text=listOf<String?>(null,""," ","$","-","©","a","ab","æ","😀","\u0000").random(random)
                val top=if(random.nextBoolean()) random.nextInt() else a.variableTop
                val oldS=settings(a); val oldT=b.reference.readOnly()
                val x=outcome { when(op) {
                    0 -> a.setUpperCaseFirst(flag); 1 -> a.setLowerCaseFirst(flag); 2 -> a.setCaseFirstDefault()
                    3 -> a.setAlternateHandlingDefault(); 4 -> a.setCaseLevelDefault(); 5 -> a.setDecompositionDefault()
                    6 -> a.setFrenchCollationDefault(); 7 -> a.setStrengthDefault(); 8 -> a.setNumericCollationDefault()
                    9 -> a.setFrenchCollation(flag); 10 -> a.setAlternateHandlingShifted(flag); 11 -> a.setCaseLevel(flag)
                    12 -> a.setDecomposition(decomposition); 13 -> a.setStrength(strength)
                    14 -> { a.setMaxVariable(group); Unit }; 15 -> a.setVariableTop(text); 16 -> a.setVariableTop(top)
                    17 -> a.setNumericCollation(flag)
                    18 -> { val method=Source::class.java.getMethod("setReorderCodes",IntArray::class.java); try { method.invoke(a,order); Unit } catch(e: java.lang.reflect.InvocationTargetException) { throw e.targetException } }
                    19 -> a.setHiraganaQuaternary(flag); 20 -> a.setHiraganaQuaternaryDefault()
                    21 -> { sourceOwners.add(a); a=a.cloneAsThawed(); Unit }; 22 -> { a.freeze(); Unit }
                    23 -> { a.reorderCodes.fill(999); Unit }; else -> a.setStrength(a.strength)
                } }
                val c=b.config
                val y=outcome { when(op) {
                    0 -> c.setUpperCaseFirst(flag); 1 -> c.setLowerCaseFirst(flag); 2 -> c.setCaseFirstDefault()
                    3 -> c.setAlternateHandlingDefault(); 4 -> c.setCaseLevelDefault(); 5 -> c.setDecompositionDefault()
                    6 -> c.setFrenchCollationDefault(); 7 -> c.setStrengthDefault(); 8 -> c.setNumericCollationDefault()
                    9 -> c.setFrenchCollation(flag); 10 -> c.setAlternateHandlingShifted(flag); 11 -> c.setCaseLevel(flag)
                    12 -> c.setDecomposition(decomposition); 13 -> c.setStrength(strength)
                    14 -> c.setMaxVariable(group); 15 -> c.setVariableTop(text); 16 -> c.setVariableTop(top)
                    17 -> c.setNumericCollation(flag); 18 -> c.setReorderCodes(order)
                    19 -> c.setHiraganaQuaternary(flag); 20 -> c.setHiraganaQuaternaryDefault()
                    21 -> { targetOwners.add(b); b=b.cloneAsThawed(); Unit }; 22 -> b.core.freeze()
                    23 -> { c.getReorderCodes().fill(999); Unit }; else -> c.setStrength(c.getStrength())
                } }
                val label="$rule/$step/$op"
                assertEquals(x,y,label)
                assertEquals(oldS===settings(a),oldT===b.reference.readOnly(),"COW/$label")
                assertEquals(a.isFrozen,b.core.isFrozen(),label)
                state(a,b,label)
            }
        }
    }
    @Test fun reorderInputCopyAndDefaultArraySharing() {
        val rule="[reorder Hani Latn]"
        val a=Source(rule); val b=ConfiguredCollatorFixture(CollationBuilder(CollationRoot.getRoot()).parseAndBuild(rule))
        val order=intArrayOf(25,17)
        a.setReorderCodes(*order); b.config.setReorderCodes(order)
        order.fill(999)
        state(a,b,"defensive copy")
        a.setReorderCodes(-1); b.config.setReorderCodes(intArrayOf(-1))
        state(a,b,"default reorder")
        assertSame(defaults(a).reorderCodes,settings(a).reorderCodes)
        assertSame(b.tailoring.settings!!.readOnly()!!.reorderCodes,b.reference.readOnly()!!.reorderCodes)
        assertSame(defaults(a).reorderTable,settings(a).reorderTable)
        assertSame(b.tailoring.settings!!.readOnly()!!.reorderTable,b.reference.readOnly()!!.reorderTable)
    }
    @Test fun firstFailureDetachmentAndFrozenMaxVariableQuirk() {
        for(op in 0..3) {
            val a=Source(""); val b=ConfiguredCollatorFixture(CollationRoot.getRoot())
            assertEquals(outcome { when(op) { 0 -> a.setStrength(8); 1 -> a.setDecomposition(18); 2 -> a.setMaxVariable(0); else -> a.setReorderCodes(25,25) }; Unit },
                outcome { when(op) { 0 -> b.config.setStrength(8); 1 -> b.config.setDecomposition(18); 2 -> b.config.setMaxVariable(0); else -> b.config.setReorderCodes(intArrayOf(25,25)) }; Unit })
            state(a,b,"first failure $op")
        }
        val a=Source(""); val b=ConfiguredCollatorFixture(CollationRoot.getRoot())
        a.freeze(); b.core.freeze()
        a.setMaxVariable(4099); b.config.setMaxVariable(4099)
        state(a,b,"frozen maxVariable")
        assertEquals(outcome { a.setStrength(a.strength) },outcome { b.config.setStrength(b.config.getStrength()) })
    }
}
