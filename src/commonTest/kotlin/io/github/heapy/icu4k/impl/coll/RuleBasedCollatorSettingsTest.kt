// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

internal class ConfiguredCollatorFixture(val tailoring: CollationTailoring, val reference: SharedObject.Reference<CollationSettings> = tailoring.settings!!.clone()) {
    val core = RuleBasedCollatorCore({ tailoring.data }, { reference.readOnly() })
    val config = RuleBasedCollatorSettings({ tailoring.data }, { reference }, { tailoring.settings!!.readOnly() }, { core.isFrozen() })
    fun cloneAsThawed() = ConfiguredCollatorFixture(tailoring,reference.clone())
}
class RuleBasedCollatorSettingsTest {
    @Test fun copyOnWriteDefaultsAndSharedCloneIsolation() {
        val t=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("[strength 1][numericOrdering on][caseFirst upper]&a<ä")
        val f=ConfiguredCollatorFixture(t); val c=f.config
        assertSame(t.settings!!.readOnly(),f.reference.readOnly())
        c.setStrength(0); c.setNumericCollation(true); c.setStrengthDefault()
        assertSame(t.settings!!.readOnly(),f.reference.readOnly())
        c.setStrength(2)
        assertNotSame(t.settings!!.readOnly(),f.reference.readOnly())
        val cloned=f.cloneAsThawed()
        assertSame(f.reference.readOnly(),cloned.reference.readOnly())
        cloned.config.setUpperCaseFirst(false)
        assertNotSame(f.reference.readOnly(),cloned.reference.readOnly())
        assertTrue(c.isUpperCaseFirst()); assertFalse(cloned.config.isUpperCaseFirst())
        c.setStrengthDefault(); assertEquals(0,c.getStrength())
        c.setNumericCollation(false); c.setNumericCollationDefault(); assertTrue(c.getNumericCollation())
        c.setDecomposition(17); c.setDecompositionDefault(); assertEquals(16,c.getDecomposition())
    }
    @Test fun configurationFeedsKeysComparisonsAndFreezing() {
        val f=ConfiguredCollatorFixture(CollationRoot.getRoot()); val c=f.config
        assertEquals(-1,f.core.doCompare("10","2"))
        c.setNumericCollation(true); assertEquals(1,f.core.doCompare("10","2"))
        c.setStrength(0); assertEquals(0,f.core.doCompare("a","A"))
        c.setCaseLevel(true); assertNotEquals(0,f.core.doCompare("a","A"))
        c.setMaxVariable(4098); assertEquals(4098,c.getMaxVariable())
        c.setVariableTop(" "); assertEquals(4096,c.getMaxVariable())
        c.setReorderCodes(intArrayOf(17,25))
        val order=c.getReorderCodes(); order[0]=999
        assertContentEquals(intArrayOf(17,25),c.getReorderCodes())
        c.setReorderCodes(null); assertTrue(c.getReorderCodes().isEmpty())
        f.core.freeze()
        assertFailsWith<UnsupportedOperationException> { c.setStrength(c.getStrength()) }
        assertFailsWith<UnsupportedOperationException> { c.setVariableTop(null) }
        c.setMaxVariable(4099); assertEquals(4099,c.getMaxVariable()) // Upstream frozen exception.
    }
    @Test fun validationOrderAndDeprecatedHiraganaNoOps() {
        val f=ConfiguredCollatorFixture(CollationRoot.getRoot()); val c=f.config
        c.setHiraganaQuaternary(true); c.setHiraganaQuaternaryDefault(); assertFalse(c.isHiraganaQuaternary())
        assertSame(f.tailoring.settings!!.readOnly(),f.reference.readOnly())
        assertFailsWith<IllegalArgumentException> { c.setStrength(9) }
        assertNotSame(f.tailoring.settings!!.readOnly(),f.reference.readOnly()) // COW occurs before validation.
        assertFailsWith<IllegalArgumentException> { c.setDecomposition(15) }
        assertFailsWith<IllegalArgumentException> { c.setMaxVariable(4100) }
        assertFailsWith<IllegalArgumentException> { c.setVariableTop("") }
        assertFailsWith<IllegalArgumentException> { c.setVariableTop("ab") }
        assertFailsWith<IllegalArgumentException> { c.setVariableTop("a") }
    }
}
