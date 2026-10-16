package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class TZDBTimeZoneNamesCoreTest {
    @Test fun regionalAbbreviationsAndUtf16Offsets() {
        val types = setOf(TimeZoneNamesDataCore.NameType.SHORT_STANDARD)
        val us = TZDBTimeZoneNamesCore(ULocale("en_US"))
        val cn = TZDBTimeZoneNamesCore(ULocale("zh"))
        assertEquals("America_Central",us.find("!cst suffix",1,types).last().mzID())
        assertEquals("China",cn.find("!cst suffix",1,types).last().mzID())
        assertEquals(3,cn.find("!cst suffix",1,types).last().matchLength())
        assertNull(us.getMetaZoneDisplayName("America_Central",null))
        assertNull(us.getTimeZoneDisplayName(null,null))
        assertFailsWith<IllegalArgumentException> { us.find("",0,null) }
        assertFailsWith<NullPointerException> { TZDBTimeZoneNamesCore(null).find("CST",0,types) }
    }
}
