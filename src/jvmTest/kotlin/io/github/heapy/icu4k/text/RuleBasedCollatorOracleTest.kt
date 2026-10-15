// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedCollator as Source
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class RuleBasedCollatorOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is ClassCastException -> "cast"; else -> e.javaClass.simpleName+":"+e.message }
    }
    @Test fun publicRuleConstructionCloningAndCompleteCeStreams() {
        val texts=listOf("","abc","ch æé😀","e\u0301\u0327","123٤٥","一",charArrayOf(0xd800.toChar(),'a',0xdc00.toChar()).concatToString())
        for(rule in listOf("","&a<b<c","&c<ch<cu&a<æ/e","[strength I]&a=b","[import de-u-co-phonebk]")) {
            val a=Source(rule); val b=RuleBasedCollator(rule)
            assertEquals(a.rules,b.getRules()); assertEquals(a.getRules(true),b.getRules(true))
            assertEquals(a.version.toString(),b.getVersion().toString()); assertEquals(a.ucaVersion.toString(),b.getUCAVersion().toString())
            assertEquals(a.getLocale(com.ibm.icu.util.ULocale.VALID_LOCALE)?.name,b.getLocale(ULocale.VALID_LOCALE)?.getName())
            for(text in texts) {
                assertContentEquals(a.getCollationKey(text).toByteArray(),b.getCollationKey(text)!!.toByteArray())
                for(other in texts) assertEquals(a.compare(text,other),b.compare(text,other))
                val x=a.getCollationElementIterator(text); val y=b.getCollationElementIterator(text)
                repeat(40) { assertEquals(x.next(),y.next()); assertEquals(x.offset,y.getOffset()) }
                assertEquals(outcome { x.previous() },outcome { y.previous() })
                x.reset(); y.reset()
                repeat(40) { assertEquals(x.previous(),y.previous()); assertEquals(x.offset,y.getOffset()) }
            }
            val ac=a.clone(); val bc=b.clone()
            assertEquals(a==ac,b==bc); assertEquals(a.hashCode(),b.hashCode())
            ac.setStrength(0); bc.setStrength(0)
            ac.setNumericCollation(true); bc.setNumericCollation(true)
            assertEquals(a.strength,b.getStrength()); assertEquals(a.numericCollation,b.getNumericCollation())
            for(text in texts) assertContentEquals(ac.getCollationKey(text).toByteArray(),bc.getCollationKey(text)!!.toByteArray())
            a.freeze(); b.freeze(); assertSame(a,a.clone()); assertSame(b,b.clone())
            val at=a.cloneAsThawed(); val bt=b.cloneAsThawed(); assertFalse(bt.isFrozen())
            assertEquals(at==a,bt==b); assertEquals(at.hashCode(),bt.hashCode())
            assertEquals(outcome { a.setStrength(a.strength) },outcome { b.setStrength(b.getStrength()) })
            assertEquals(outcome { a.getCollationElementIterator(null as String?) },outcome { b.getCollationElementIterator(null as String?) })
        }
        for(rule in listOf<String?>(null,"&","&[last implicit]<a","&a<<<<b<<<<c<<<<d<<<<e")) {
            assertEquals(outcome { Source(rule); Unit },outcome { RuleBasedCollator(rule); Unit })
        }
    }
    @Test fun baseObjectDispatchNullInputsAndNullableReorderArray() {
        val a=Source(""); val b=RuleBasedCollator("")
        val ac: java.util.Comparator<Any?> = a; val bc: Comparator<Any?> = b
        for(x in listOf(null,"a",StringBuilder("a"),1)) for(y in listOf(null,"b",StringBuilder("b"),2)) {
            assertEquals(outcome { ac.compare(x,y) },outcome { bc.compare(x,y) })
        }
        val method=Source::class.java.getMethod("setReorderCodes",IntArray::class.java)
        a.setReorderCodes(17,25); b.setReorderCodes(17,25)
        method.invoke(a,null as Any?); b.setReorderCodes(null)
        assertContentEquals(a.reorderCodes,b.getReorderCodes())
        a.freeze(); b.freeze()
        assertEquals(outcome { try { method.invoke(a,null as Any?) } catch(e: java.lang.reflect.InvocationTargetException) { throw e.targetException }; Unit },outcome { b.setReorderCodes(null) })
        for(code in listOf(-1,0,17,20,22,25,103,4096,9999)) assertContentEquals(com.ibm.icu.text.Collator.getEquivalentReorderCodes(code),Collator.getEquivalentReorderCodes(code))
    }
}
