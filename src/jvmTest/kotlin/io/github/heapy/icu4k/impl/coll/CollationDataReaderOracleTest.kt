// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import com.ibm.icu.impl.coll.CollationRoot as SourceRoot
import com.ibm.icu.impl.coll.CollationTailoring as SourceTailoring
import com.ibm.icu.util.VersionInfo as SourceVersion
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.random.Random
import kotlin.test.*

class CollationDataReaderOracleTest {
    @Test fun embeddedRootMatchesOriginalFieldsAndAllMappings() {
        val source=CollationDataOracleTest().importSource(SourceRoot.getData())
        val t=CollationTailoring(null)
        val bytes=ICUBinary.getRequiredData("coll/ucadata.icu")
        CollationDataReader.read(null,bytes,t)
        val actual=t.data!!
        val sourceBytes=com.ibm.icu.impl.ICUBinary.getRequiredData("coll/ucadata.icu")
        val constructor=SourceTailoring::class.java.declaredConstructors.single().apply { isAccessible=true }
        val fresh=constructor.newInstance(null) as SourceTailoring
        Class.forName("com.ibm.icu.impl.coll.CollationDataReader").getDeclaredMethod("read",SourceTailoring::class.java,java.nio.ByteBuffer::class.java,SourceTailoring::class.java).apply { isAccessible=true }.invoke(null,null,sourceBytes,fresh)
        assertEquals(sourceBytes.position(),bytes.position)
        assertEquals(sourceBytes.remaining(),bytes.remaining)
        assertEquals(SourceRoot.getRoot().version,t.version)
        assertEquals(source.numericPrimary,actual.numericPrimary)
        assertContentEquals(source.ces,actual.ces);assertContentEquals(source.ce32s,actual.ce32s)
        assertContentEquals(source.jamoCE32s,actual.jamoCE32s);assertContentEquals(source.rootElements,actual.rootElements)
        assertContentEquals(source.fastLatinTable,actual.fastLatinTable);assertContentEquals(source.fastLatinTableHeader,actual.fastLatinTableHeader)
        assertEquals(source.contexts,actual.contexts);assertEquals(source.numScripts,actual.numScripts)
        assertContentEquals(source.scriptsIndex,actual.scriptsIndex);assertContentEquals(source.scriptStarts,actual.scriptStarts)
        assertContentEquals(source.compressibleBytes,actual.compressibleBytes)
        assertEquals(source.unsafeBackwardSet,actual.unsafeBackwardSet)
        for (c in 0..0x10ffff) assertEquals(source.getCE32(c),actual.getCE32(c),"CE32/$c")
        val original=SourceRoot.getRoot().settings.readOnly();val settings=t.settings!!.readOnly()!!
        assertEquals(original.options,settings.options);assertEquals(original.variableTop,settings.variableTop)
        assertEquals(original.fastLatinOptions,settings.fastLatinOptions);assertContentEquals(original.fastLatinPrimaries,settings.fastLatinPrimaries)
    }
    @Test fun versionPackingMatchesSignedSourceArithmetic() {
        val constructor=SourceTailoring::class.java.declaredConstructors.single().apply { isAccessible=true }
        val source=constructor.newInstance(null) as SourceTailoring
        val setVersion=SourceTailoring::class.java.getDeclaredMethod("setVersion",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
        val ucaVersion=SourceTailoring::class.java.getDeclaredMethod("getUCAVersion").apply { isAccessible=true }
        val makeBaseVersion=SourceTailoring::class.java.getDeclaredMethod("makeBaseVersion",SourceVersion::class.java).apply { isAccessible=true }
        val target=CollationTailoring(null);val random=Random(783)
        repeat(2000) {
            val base=random.nextInt();val rules=random.nextInt()
            setVersion.invoke(source,base,rules);target.setVersion(base,rules)
            assertEquals(source.version,target.version);assertEquals(ucaVersion.invoke(source),target.getUCAVersion())
        }
        for(major in 0..31) for(minor in 0..7) for(milli in 0..3) {
            val s=makeBaseVersion.invoke(null,SourceVersion.getInstance(major,minor,milli,0))
            val t=CollationTailoring.makeBaseVersion(io.github.heapy.icu4k.util.VersionInfo.getInstance(major,minor,milli,0))
            assertEquals(s.toString(),t.toString())
        }
    }
}
