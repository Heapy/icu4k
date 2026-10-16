@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.impl.TZDBTimeZoneNames as Source
import com.ibm.icu.text.TimeZoneNames.NameType as SType
import io.github.heapy.icu4k.util.ULocale
import java.util.EnumSet
import kotlin.test.*

class TZDBTimeZoneNamesCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        if (e is NullPointerException) "null" else e.javaClass.simpleName + ":" + e.message
    }
    @Test fun allNamesAndRegionalPrefixFilters() {
        for (locale in listOf("en", "en_US", "en_GB", "zh", "zh_TW", "he", "hi", "en_AU", "und", "und_ZZ", "en@rg=cnzzzz")) {
            val a = Source(com.ibm.icu.util.ULocale(locale))
            val b = TZDBTimeZoneNamesCore(ULocale(locale))
            val abbreviations = mutableSetOf("missing", "CST", "IST", "EST", "GMT", "UTC")
            for (id in a.availableMetaZoneIDs + setOf("", "missing")) {
                for (type in SType.entries) {
                    val expected = a.getMetaZoneDisplayName(id,type)
                    assertEquals(expected,b.getMetaZoneDisplayName(id,TimeZoneNamesDataCore.NameType.valueOf(type.name)),"$id/$type")
                    if (expected != null) abbreviations.add(expected)
                }
                assertNull(b.getMetaZoneDisplayName(id,null))
            }
            for (abbr in abbreviations) for (mask in -1 until 128) {
                val types = if (mask == -1) null else EnumSet.noneOf(SType::class.java).also { set ->
                    SType.entries.forEachIndexed { i, type -> if (mask and (1 shl i) != 0) set.add(type) }
                }
                val text = "!" + abbr.lowercase() + " trailing"
                val expected = outcome { a.find(text,1,types).map { listOf(it.nameType().name,it.tzID(),it.mzID(),it.matchLength()) } }
                val actual = outcome { b.find(text,1,types?.map { TimeZoneNamesDataCore.NameType.valueOf(it.name) }?.toSet()).map { listOf(it.nameType().name,it.tzID(),it.mzID(),it.matchLength()) } }
                assertEquals(expected,actual,"$locale/$abbr/$mask")
            }
        }
    }
    @Test fun lazyLocaleAndInputValidation() {
        val a = Source(null)
        val b = TZDBTimeZoneNamesCore(null)
        assertEquals(a.getMetaZoneDisplayName("America_Central",SType.SHORT_STANDARD),b.getMetaZoneDisplayName("America_Central",TimeZoneNamesDataCore.NameType.SHORT_STANDARD))
        for ((text,start) in listOf(null to 0,"" to 0,"CST" to -1,"CST" to 3,"CST" to 0)) {
            assertEquals(outcome { a.find(text,start,null) },outcome { b.find(text,start,null) })
        }
    }
}
