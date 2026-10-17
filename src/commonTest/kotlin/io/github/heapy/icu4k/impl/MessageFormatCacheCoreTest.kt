package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.MessagePattern
import kotlin.test.*

class MessageFormatCacheCoreTest {
    @Test fun stockScanDescendsButCustomSettersOnlyVisitTopLevel() {
        val pattern=MessagePattern("{n,plural,other{{n,number,0.0}}} {n,number} {0}")
        val core=MessageFormatCacheCore({pattern}) { type,style->"$type<$style>" }
        core.cacheExplicitFormats()
        assertEquals(2,core.cachedFormatters!!.size)
        assertEquals(listOf(null,"number<>",null),core.formats())
        core.setFormatByArgumentName("n","custom")
        assertEquals(listOf("custom","custom",null),core.formats())
        assertTrue(core.cachedFormatters!!.values.contains("number<0.0>"))
        assertEquals(2,core.customFormatArgStarts!!.size)
        assertFailsWith<IllegalArgumentException> { core.setFormatByArgumentIndex(0,"bad") }
    }
    @Test fun indexNameAndOrdinalSettersRetainNullEntries() {
        val pattern=MessagePattern("{2,number} {0} {2,number,integer}")
        val core=MessageFormatCacheCore({pattern}) { type,style->"$type<$style>" }
        core.cacheExplicitFormats()
        assertEquals(listOf(null,null,"number<integer>"),core.formatsByArgumentIndex())
        assertEquals("number<>",core.formatByArgumentName("2"))
        core.setFormat(0,null)
        assertNull(core.formatByArgumentName("2"))
        assertTrue(core.cachedFormatters!!.containsKey(1))
        core.setFormatsByArgumentIndex(listOf("zero"))
        assertEquals(listOf(null,"zero","number<integer>"),core.formats())
        core.setFormatsByArgumentName(mapOf("2" to "two","01" to "ignored"))
        assertEquals(listOf("two","zero","two"),core.formats())
        assertEquals(setOf("0","2"),core.argumentNames())
        assertFailsWith<IndexOutOfBoundsException> { core.setFormat(-1,"bad") }
    }
    @Test fun emptyPatternNullArgumentsAndCacheAllocationRemainObservable() {
        val pattern=MessagePattern("")
        val core=MessageFormatCacheCore<String>({pattern}) { _,_->"unused" }
        core.cacheExplicitFormats()
        assertNull(core.formatByArgumentName(null))
        core.setFormatsByArgumentIndex(null); core.setFormatsByArgumentName(null)
        assertFailsWith<NullPointerException> { core.setFormats(null) }
        pattern.parse("{0}"); core.setFormat(0,null)
        pattern.parse(""); core.cacheExplicitFormats()
        assertFailsWith<NullPointerException> { core.formatByArgumentName(null) }
    }
    @Test fun cloneCopiesMapsAndMarkersWhileSharingFormatObjects() {
        val pattern=MessagePattern("{0} {1}")
        val format=Any()
        val a=MessageFormatCacheCore<Any>({pattern}) { _,_->null }
        a.setFormat(0,format)
        val b=MessageFormatCacheCore<Any>({pattern}) { _,_->null }
        b.copyStateFrom(a)
        assertNotSame(a.cachedFormatters,b.cachedFormatters)
        assertNotSame(a.customFormatArgStarts,b.customFormatArgStarts)
        assertSame(a.formats()[0],b.formats()[0])
        b.setFormat(1,null)
        assertEquals(1,a.customFormatArgStarts!!.size)
        assertEquals(2,b.customFormatArgStarts!!.size)
    }
}
