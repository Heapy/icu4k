// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.test.*

class CollationSettingsTest {
    private class ExtraShared(var number: Int, val payload: IntArray) : SharedObject() {
        override fun cloneNative(): SharedObject = ExtraShared(number,payload)
    }
    @Test fun runtimeSubclassFieldsAndNativeTypedCopyShareOnlyPayload() {
        val original=ExtraShared(7,intArrayOf(8)); original.addRef(); original.addRef()
        val copy=original.clone() as ExtraShared
        assertEquals(7,copy.number); assertSame(original.payload,copy.payload)
        assertEquals(0,copy.getRefCount()); assertEquals(2,original.getRefCount())
        copy.number=9; assertEquals(7,original.number)
    }
    @Test fun optionsDefaultsValidationAndConditionalEquality() {
        val s = CollationSettings(); val other = CollationSettings()
        assertEquals(0x2010,s.options); assertEquals(2,s.getStrength()); assertEquals(1,s.getMaxVariable())
        s.variableTop = 0x12345678L; assertEquals(s,other); assertEquals(s.hashCode(),other.hashCode())
        s.setAlternateHandlingShifted(true); other.setAlternateHandlingShifted(true); assertNotEquals(s,other)
        other.variableTop = s.variableTop; assertEquals(s,other)
        s.setStrength(15); assertEquals(15,s.getStrength())
        val old = s.options
        assertEquals("illegal strength value 4",assertFailsWith<IllegalArgumentException> { s.setStrength(4) }.message); assertEquals(old,s.options)
        s.setStrengthDefault(0x3000); assertEquals(3,s.getStrength())
        s.setMaxVariable(-1,0x20); assertEquals(2,s.getMaxVariable())
        assertEquals("illegal maxVariable value 4",assertFailsWith<IllegalArgumentException> { s.setMaxVariable(4,0) }.message)
        s.setCaseFirst(0x300); assertTrue(CollationSettings.sortsTertiaryUpperCaseFirst(s.options))
        s.setFlag(CollationSettings.CASE_LEVEL,true); assertFalse(CollationSettings.isTertiaryWithCaseBits(s.options))
        s.setFlagDefault(CollationSettings.NUMERIC,CollationSettings.NUMERIC); assertTrue(s.isNumeric())
        s.setFlag(CollationSettings.CHECK_FCD,true); assertFalse(s.dontCheckFCD())
    }
    @Test fun scriptPermutationAndAliasesSurviveCloneAndCopy() {
        val s = CollationSettings(); val codes = intArrayOf(14)
        s.setReordering(scriptCollationData(),codes)
        assertSame(codes,s.reorderCodes); assertEquals(0xd2001234L,s.reorder(0x20001234)); assertEquals(0x04001234L,s.reorder(0x30001234))
        assertEquals(0xfe001234L,s.reorder(0xfe001234))
        s.fastLatinPrimaries!![0] = 'a'; s.addRef()
        val c = s.clone()
        assertEquals(0,c.getRefCount()); assertEquals(1,s.getRefCount())
        assertSame(s.reorderTable,c.reorderTable); assertSame(s.reorderCodes,c.reorderCodes); assertSame(s.reorderRanges,c.reorderRanges)
        assertNotSame(s.fastLatinPrimaries,c.fastLatinPrimaries); c.fastLatinPrimaries!![0] = 'b'; assertEquals('a',s.fastLatinPrimaries!![0])
        val copy = CollationSettings(); copy.copyReorderingFrom(s); assertSame(s.reorderTable,copy.reorderTable)
        copy.resetReordering(); assertFalse(copy.hasReordering()); assertTrue(s.hasReordering()); assertEquals(0,copy.reorderCodes!!.size)
        s.setReordering(null,intArrayOf(103)); assertFalse(s.hasReordering())
    }
    @Test fun splitByteReorderingAndAliasValidation() {
        val s = CollationSettings(); val table = ByteArray(256) { it.toByte() }.also { it[0x20] = 0 }
        val serialized = intArrayOf(14,0x20010000,0x30010001)
        s.aliasReordering(null,serialized,1,table)
        assertSame(table,s.reorderTable); assertNotSame(serialized,s.reorderCodes)
        assertContentEquals(longArrayOf(0x20010000,0x30010001),s.reorderRanges)
        assertEquals(0x200000ffL,s.reorder(0x200000ff)); assertEquals(0x21010000L,s.reorder(0x20010000))
        assertEquals(0x21ffffffL,s.reorder(0x20ffffff)); assertEquals(0x31010000L,s.reorder(0x31010000))
        val aliasCodes = intArrayOf(14); val identity = ByteArray(256) { it.toByte() }
        s.aliasReordering(null,aliasCodes,1,identity); assertSame(aliasCodes,s.reorderCodes); assertNull(s.reorderRanges)
        assertEquals(0x20010000L,s.reorder(0x20010000))
    }
    @Test fun sharedOwnershipCopyOnWriteAndClear() {
        val settings = CollationSettings(); val a = SharedObject.Reference(settings); val b = a.clone()
        assertEquals(2,settings.getRefCount()); assertSame(settings,a.readOnly()); assertSame(settings,b.readOnly())
        val own = a.copyOnWrite(); assertNotSame(settings,own); assertEquals(1,own.getRefCount()); assertEquals(1,settings.getRefCount())
        own.setStrength(0); assertEquals(2,settings.getStrength()); assertSame(own,a.copyOnWrite())
        a.clear(); a.clear(); assertNull(a.readOnly()); assertEquals(0,own.getRefCount()); assertFailsWith<NullPointerException> { a.copyOnWrite() }
        b.clear(); assertEquals(0,settings.getRefCount())
        val empty = SharedObject.Reference<SharedObject>(null); val clone = empty.clone(); assertNull(clone.readOnly()); clone.clear()
        val raw = SharedObject(); raw.removeRef(); assertEquals(-1,raw.getRefCount()); val rawCopy=raw.clone(); assertEquals(0,rawCopy.getRefCount()); assertEquals(-1,raw.getRefCount())
    }
}
