// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.Collator as Source
import kotlin.test.*

class CollatorBaseOracleTest {
    private class SourceGeneric : Source() {
        var frozen=false
        var number=7
        val shared=mutableListOf(1)
        private val delegate=com.ibm.icu.text.RuleBasedCollator("")
        override fun isFrozen()=frozen
        override fun compare(a: String?,b: String?)=a!!.compareTo(b!!)
        override fun getCollationKey(s: String?)=delegate.getCollationKey(s)
        override fun getRawCollationKey(s: String?,key: com.ibm.icu.text.RawCollationKey?)=delegate.getRawCollationKey(s,key)
        override fun setVariableTop(s: String?)=delegate.setVariableTop(s)
        override fun setVariableTop(v: Int)=delegate.setVariableTop(v)
        override fun getVariableTop()=delegate.variableTop
        override fun getVersion()=delegate.version
        override fun getUCAVersion()=delegate.ucaVersion
    }
    private class TargetGeneric : Collator() {
        var frozen=false
        var number=7
        val shared=mutableListOf(1)
        private val delegate=RuleBasedCollator("")
        override fun isFrozen()=frozen
        override fun compare(source: String?,target: String?)=source!!.compareTo(target!!)
        override fun getCollationKey(source: String?)=delegate.getCollationKey(source)
        override fun getRawCollationKey(source: String?,key: RawCollationKey?)=delegate.getRawCollationKey(source,key)
        override fun setVariableTop(varTop: String?)=delegate.setVariableTop(varTop)
        override fun setVariableTop(varTop: Int)=delegate.setVariableTop(varTop)
        override fun getVariableTop()=delegate.getVariableTop()
        override fun getVersion()=delegate.getVersion()
        override fun getUCAVersion()=delegate.getUCAVersion()
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        when(e) { is NullPointerException -> "null"; is ClassCastException -> "cast"; else -> e.javaClass.simpleName+":"+e.message }
    }
    @Test fun baseDefaultsObjectDispatchEqualityAndShallowCloning() {
        val a=SourceGeneric(); val b=TargetGeneric()
        assertEquals(a.strength,b.getStrength()); assertEquals(a.decomposition,b.getDecomposition()); assertEquals(a.maxVariable,b.getMaxVariable())
        assertEquals(a.tailoredSet.toPattern(false),b.getTailoredSet().toPattern(false))
        assertEquals(a.getLocale(null).name,b.getLocale(null)!!.getName())
        assertEquals(a==SourceGeneric(),b==TargetGeneric()); assertEquals(a.hashCode(),b.hashCode())
        val ac=a.clone() as SourceGeneric; val bc=b.clone() as TargetGeneric
        assertNotSame(a,ac); assertNotSame(b,bc); assertSame(a.shared,ac.shared); assertSame(b.shared,bc.shared)
        ac.number=9; bc.number=9; assertEquals(a.number,b.number)
        val x: java.util.Comparator<Any?> = a; val y: Comparator<Any?> = b
        for(left in listOf(null,"a",StringBuilder("a"),1)) for(right in listOf(null,"z",StringBuilder("z"),2)) assertEquals(outcome { x.compare(left,right) },outcome { y.compare(left,right) })
        for(frozen in listOf(false,true)) {
            a.frozen=frozen; b.frozen=frozen
            assertEquals(outcome { a.setStrength(-1) },outcome { b.setStrength(-1) })
            assertEquals(outcome { a.setDecomposition(-1) },outcome { b.setDecomposition(-1) })
            assertEquals(outcome { a.setMaxVariable(-1) },outcome { b.setMaxVariable(-1) })
            assertEquals(outcome { a.setReorderCodes(17) },outcome { b.setReorderCodes(17) })
            assertEquals(outcome { a.reorderCodes },outcome { b.getReorderCodes() })
            assertEquals(outcome { a.freeze() },outcome { b.freeze() })
            assertEquals(outcome { a.cloneAsThawed() },outcome { b.cloneAsThawed() })
        }
        assertFalse((b as Any) is java.io.Serializable)
    }
}
