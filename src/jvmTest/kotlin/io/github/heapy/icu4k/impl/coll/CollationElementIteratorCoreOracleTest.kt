// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.text.RuleBasedCollator as SourceCollator
import com.ibm.icu.text.CollationElementIterator as Source
import io.github.heapy.icu4k.text.*
import kotlin.random.Random
import kotlin.test.*

class CollationElementIteratorCoreOracleTest {
    private fun result(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName + ":" + e.message }
    }
    private fun sourceField(a: Any, name: String): Any? = a.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(a)
    @Test fun fullCeStreamsOffsetsAndMixedHistories() {
        val texts = listOf("", "abc", "chu æ Œß", "é\u0327a\u0301\u0327", "😀a𠀀", "123٤٥٦007", "각각", charArrayOf(0xd800.toChar(),'a',0xdc00.toChar()).concatToString())
        for(rule in listOf("", "&c<ch<cu&a<æ/e<é<😀|b", "[import de-u-co-phonebk]")) {
            val s = SourceCollator(rule)
            val t = CollationBuilder(CollationRoot.getRoot()).parseAndBuild(rule)
            val ts = t.settings!!.copyOnWrite()!!
            for(numeric in listOf(false,true)) for(fcd in listOf(false,true)) {
                s.numericCollation=numeric; s.decomposition=if(fcd) 17 else 16
                ts.setFlag(CollationSettings.NUMERIC,numeric); ts.setFlag(CollationSettings.CHECK_FCD,fcd)
                for(text in texts) for(kind in 0..2) {
                    val a = when(kind) { 0 -> s.getCollationElementIterator(text); 1 -> s.getCollationElementIterator(com.ibm.icu.text.UCharacterIterator.getInstance(text)); else -> s.getCollationElementIterator(java.text.StringCharacterIterator(text)) }
                    val b = elementCore(t)
                    when(kind) { 0 -> b.setText(text); 1 -> b.setText(UCharacterIterator.getInstance(text)); else -> b.setText(StringCharacterIterator(text)) }
                    val label = "$rule/$numeric/$fcd/$text/$kind"
                    repeat(2*(text.length+20)) {
                        assertEquals(result { a.next() },result { b.next() },label)
                        assertEquals(result { a.offset },result { b.getOffset() },label)
                    }
                    a.reset(); b.reset()
                    repeat(2*(text.length+20)) {
                        assertEquals(result { a.previous() },result { b.previous() },label)
                        assertEquals(result { a.offset },result { b.getOffset() },label)
                    }
                    val random = Random(7820)
                    repeat(100) {
                        val op=random.nextInt(4); val offset=random.nextInt(-1,text.length+2)
                        assertEquals(result { when(op) { 0 -> a.next(); 1 -> a.previous(); 2 -> a.reset(); else -> a.setOffset(offset) } },result { when(op) { 0 -> b.next(); 1 -> b.previous(); 2 -> b.reset(); else -> b.setOffset(offset) } },label)
                        assertEquals(result { a.offset },result { b.getOffset() },label)
                        assertEquals(sourceField(a,"otherHalf_"),sourceField(b,"otherHalf"),label)
                        assertEquals((sourceField(a,"dir_") as Byte).toInt(),sourceField(b,"dir"),label)
                    }
                }
            }
        }
    }
    @Test fun nullTextKeepsPreviousCursorAndLazyFailures() {
        val a = SourceCollator("").getCollationElementIterator("abc")
        val b = elementCore(); b.setText("abc")
        a.next(); b.next()
        assertEquals(result { a.setText(null as String?) },result { b.setText(null as String?) })
        assertEquals(result { a.offset },result { b.getOffset() })
        assertEquals(result { a.next() },result { b.next() })
        assertEquals(result { a.setOffset(0) },result { b.setOffset(0) })
        assertEquals(result { a.previous() },result { b.previous() })
        a.reset(); b.reset()
        assertEquals(result { a.previous() },result { b.previous() })
        assertEquals(result { a.setOffset(1) },result { b.setOffset(1) })
    }
    @Test fun fullWidthHalfHelpersAndIteratorEquality() {
        val first = Source::class.java.getDeclaredMethod("getFirstHalf",Long::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
        val second = Source::class.java.getDeclaredMethod("getSecondHalf",Long::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
        val needs = Source::class.java.getDeclaredMethod("ceNeedsTwoParts",Long::class.javaPrimitiveType).apply { isAccessible=true }
        val random=Random(78052)
        repeat(10000) {
            val p=random.nextLong(); val lower=random.nextInt()
            assertEquals(first.invoke(null,p,lower),CollationElementIteratorCore.getFirstHalf(p,lower))
            assertEquals(second.invoke(null,p,lower),CollationElementIteratorCore.getSecondHalf(p,lower))
            assertEquals(needs.invoke(null,p),CollationElementIteratorCore.ceNeedsTwoParts(p))
        }
        // FCDUTF16CollationIterator's recursive equals is an existing upstream quirk;
        // use the unnormalized iterator here to check the outer equality contract.
        val sc=SourceCollator(""); sc.decomposition=16
        val tc=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("")
        tc.settings!!.copyOnWrite()!!.setFlag(CollationSettings.CHECK_FCD,false)
        val a=sc.getCollationElementIterator("abc"); val other=sc.getCollationElementIterator("abc")
        val b=elementCore(tc); val otherB=elementCore(tc)
        b.setText("abc"); otherB.setText("abc")
        assertEquals(a==other,b==otherB)
        other.setOffset(0); otherB.setOffset(0)
        assertEquals(a==other,b==otherB)
        a.next(); b.next(); assertEquals(a==other,b==otherB)
        other.next(); otherB.next(); assertEquals(a==other,b==otherB)
        assertEquals(result { a.hashCode() },result { b.hashCode() })
        assertSame(tc,b.getOwner())
    }
    @Test fun expansionMapsAndFullWidthOrderExtraction() {
        val compute = Source::class.java.getDeclaredMethod("computeMaxExpansions",com.ibm.icu.impl.coll.CollationData::class.java).apply { isAccessible=true }
        for(rule in listOf("", "&a<æ/e&c<ch/ab")) {
            val a = SourceCollator(rule); val b = CollationBuilder(CollationRoot.getRoot()).parseAndBuild(rule)
            val expected = compute.invoke(null,sourceField(a,"data")) as Map<*,*>
            val actual = CollationElementIteratorCore.computeMaxExpansions(b.data)
            assertEquals(expected,actual,rule)
            b.maxExpansions=actual
            val ac=a.getCollationElementIterator(""); val bc=elementCore(b)
            bc.setText("")
            val random=Random(7878)
            for(ce in expected.keys.map { it as Int } + List(1000) { random.nextInt() } + listOf(0,0xc0,-1)) {
                assertEquals(ac.getMaxExpansion(ce),bc.getMaxExpansion(ce))
                assertEquals(Source.primaryOrder(ce),CollationElementIteratorCore.primaryOrder(ce))
                assertEquals(Source.secondaryOrder(ce),CollationElementIteratorCore.secondaryOrder(ce))
                assertEquals(Source.tertiaryOrder(ce),CollationElementIteratorCore.tertiaryOrder(ce))
            }
        }
    }
}
