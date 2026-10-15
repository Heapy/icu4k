// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUException
import io.github.heapy.icu4k.util.VersionInfo
import kotlin.test.*

class CollationDataReaderTest {
    @Test fun embeddedRootAndSettingsOnlyTailoring() {
        val root=CollationRoot.getRoot()
        assertSame(root,CollationRoot.getRoot())
        assertSame(root.data,root.ownedData)
        assertSame(root.trie,root.data!!.trie)
        assertSame(root.unsafeBackwardSet,root.data!!.unsafeBackwardSet)
        assertTrue(root.unsafeBackwardSet!!.isFrozen())
        assertEquals(67,root.data!!.jamoCE32s!!.size)
        assertTrue(root.data!!.isUnsafeBackward(0x301,false))
        val settings=CollationRoot.getSettings()
        val child=CollationTailoring(root.settings)
        val input=settingsOnly(root.version, (root.data!!.numericPrimary.toInt()) or settings.options)
        CollationDataReader.read(root,input,child)
        assertSame(root.data,child.data)
        assertNull(child.ownedData)
        assertSame(settings,child.settings!!.readOnly())
        assertEquals(40,input.position)
        val changed=CollationTailoring(root.settings)
        CollationDataReader.read(root,settingsOnly(root.version,root.data!!.numericPrimary.toInt() or ((settings.options and 0xffff0fff.toInt()) or 0x1000)),changed)
        assertNotSame(settings,changed.settings!!.readOnly())
        assertEquals(1,changed.settings!!.readOnly()!!.getStrength())
        child.settings!!.clear();changed.settings!!.clear()
    }
    @Test fun invalidIndexesAssignVersionBeforeFailure() {
        val t=CollationTailoring(null)
        val bytes=settingsOnly(0x09880000,0)
        bytes.bytes[35]=1
        assertFailsWith<ICUException> { CollationDataReader.read(null,bytes,t) }
        assertEquals(0x09880000,t.version)
        assertEquals(36,bytes.position)
        assertNull(t.data)
    }
    @Test fun rulesAndVersionContainer() {
        val t=CollationTailoring(null)
        assertEquals("",t.getRules());t.setRules("&a<b");assertEquals("&a<b",t.getRules())
        val version=CollationTailoring.makeBaseVersion(VersionInfo.getInstance(17,0,0,0))
        assertEquals(9,version.major);assertEquals(136,version.minor)
        t.setVersion(0x09880000,0);assertEquals(0x880,t.getUCAVersion())
        t.ensureOwnedData();val owned=t.data;t.ensureOwnedData();assertSame(owned,t.data)
    }
}
internal fun settingsOnly(version: Int,options: Int): BinaryBuffer {
    val bytes=ICUBinary.writeHeader(0x55436f6c,0x05000000,version)+ByteArray(8)
    BinaryBuffer(bytes).apply { position=32;writeInt(2);writeInt(options) }
    return BinaryBuffer(bytes)
}
