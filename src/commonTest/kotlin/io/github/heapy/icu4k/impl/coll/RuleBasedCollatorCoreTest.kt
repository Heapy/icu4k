// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.text.RawCollationKey
import kotlin.test.*

internal fun collatorCore(t: CollationTailoring): RuleBasedCollatorCore = RuleBasedCollatorCore({ t.data }, { t.settings!!.readOnly() })
class RuleBasedCollatorCoreTest {
    @Test fun tailoredComparisonsKeysAndIdenticalLevel() {
        val t=CollationBuilder(CollationRoot.getRoot()).parseAndBuild("&c<ch<d&a=b")
        val c=collatorCore(t)
        assertEquals(-1,c.doCompare("c","ch")); assertEquals(-1,c.doCompare("ch","d"))
        assertEquals(0,c.doCompare("a","b"))
        val settings=t.settings!!.copyOnWrite()!!
        settings.setStrength(15)
        settings.fastLatinOptions=CollationFastLatin.getOptions(t.data,settings,settings.fastLatinPrimaries)
        assertEquals(-1,c.doCompare("a","b"))
        assertEquals(0,c.doCompare("é","e\u0301"))
        for(a in listOf("a","b","é","e\u0301","😀","\ufffe")) for(b in listOf("a","b","e","😀")) {
            assertEquals(c.doCompare(a,b),c.getCollationKey(a)!!.compareTo(c.getCollationKey(b)))
        }
        assertEquals(0,c.doCompare(null,null))
        assertFailsWith<NullPointerException> { c.doCompare(null,"x") }
    }
    @Test fun reuseNullInputsAndFrozenBuffers() {
        val c=collatorCore(CollationRoot.getRoot())
        val raw=RawCollationKey(0)
        assertSame(raw,c.getRawCollationKey("abcdef",raw))
        val storage=raw.bytes
        assertNull(c.getRawCollationKey(null,raw)); assertSame(storage,raw.bytes)
        assertNull(c.getCollationKey(null))
        val before=c.getCollationKey("é😀 abc")!!.toByteArray()
        c.getCollationKey("different"); assertContentEquals(before,c.getCollationKey("é😀 abc")!!.toByteArray())
        assertFalse(c.isFrozen()); c.freeze(); c.freeze(); assertTrue(c.isFrozen())
        assertContentEquals(before,c.getCollationKey("é😀 abc")!!.toByteArray())
        assertContentEquals(longArrayOf(),c.internalGetCEs(""))
        assertTrue(c.internalGetCEs("abc").isNotEmpty())
    }
}
