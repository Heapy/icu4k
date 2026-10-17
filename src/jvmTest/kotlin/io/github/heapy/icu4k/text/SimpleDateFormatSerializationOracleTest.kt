@file:Suppress("UNCHECKED_CAST", "DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.SimpleDateFormat as Source
import com.ibm.icu.text.NumberFormat as SourceNumber
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import java.io.*
import kotlin.test.*

class SimpleDateFormatSerializationOracleTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { b -> ObjectOutputStream(b).use { it.writeObject(value) } }.toByteArray()
    private fun read(bytes: ByteArray, from: String? = null, to: String? = null): Any = object : ObjectInputStream(ByteArrayInputStream(bytes)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (from != null && desc.name.contains(from)) try { return Class.forName(desc.name.replace(from, to!!)) } catch (_: ClassNotFoundException) { }
            return super.resolveClass(desc)
        }
    }.use { it.readObject() }
    private fun field(owner: Source, name: String): Any? = Source::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(owner)
    private fun compare(a: Source, b: SimpleDateFormat) {
        assertEquals(a.toPattern(), b.toPattern())
        assertEquals(a.get2DigitYearStart().time, b.get2DigitYearStart()!!.getTime())
        assertEquals(a.calendar.timeInMillis, b.getCalendar()!!.getTimeInMillis())
        assertEquals(a.getContext(com.ibm.icu.text.DisplayContext.Type.CAPITALIZATION).toString(), b.getContext(DisplayContext.Type.CAPITALIZATION).toString())
        assertEquals(field(a, "useFastFormat"), b.state.useFastFormat)
        assertEquals(field(a, "override"), b.state.numberingOverride)
        assertEquals(a.getLocale(SourceLocale.VALID_LOCALE)?.toString(), b.getLocale(ULocale.VALID_LOCALE)?.toString())
        for (time in listOf(0L, -2208988800000L, 951827696123L, 1712345678901L)) {
            assertEquals(a.format(java.util.Date(time)), b.format(Date(time)))
        }
    }
    private fun pair(id: String): Pair<Source, SimpleDateFormat> {
        val a = Source("G yyyy-MM-dd HH:mm z", "y=thai", SourceLocale(id))
        val b = SimpleDateFormat("G yyyy-MM-dd HH:mm z", "y=thai", ULocale(id))
        a.timeZone = com.ibm.icu.util.TimeZone.getTimeZone("Asia/Tokyo"); b.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"))
        a.set2DigitYearStart(java.util.Date(0)); b.set2DigitYearStart(Date(0))
        a.setContext(com.ibm.icu.text.DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
        b.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
        a.format(java.util.Date(123456789)); b.format(Date(123456789))
        return a to b
    }
    @Test fun exactDescriptorsAndBidirectionalStreams() {
        val a = ObjectStreamClass.lookup(Source::class.java); val b = ObjectStreamClass.lookup(SimpleDateFormat::class.java)
        assertEquals(a.serialVersionUID, b.serialVersionUID)
        assertEquals(a.fields.map { Triple(it.name, it.typeCode, it.typeString?.replace("com/ibm/icu/", "io/github/heapy/icu4k/")) }, b.fields.map { Triple(it.name, it.typeCode, it.typeString) })
        for (id in listOf("en_US", "ru", "zh@calendar=chinese", "he@calendar=hebrew", "ja@calendar=japanese")) {
            val (source, port) = pair(id)
            compare(read(bytes(source)) as Source, read(bytes(port)) as SimpleDateFormat)
            compare(read(bytes(port), "io.github.heapy.icu4k.", "com.ibm.icu.") as Source, read(bytes(source), "com.ibm.icu.", "io.github.heapy.icu4k.") as SimpleDateFormat)
        }
    }
    @Test fun cloneGraphKeepsSharedOverridesCenturyAndZoneAfterSerialization() {
        val (a, b) = pair("en_US")
        val asCopy = a.clone(); val bsCopy = b.clone()
        val aa = read(bytes(arrayOf(a, asCopy))) as Array<Source>
        val bb = read(bytes(arrayOf(b, bsCopy))) as Array<SimpleDateFormat>
        assertSame(bb[0].state.numberFormatters, bb[1].state.numberFormatters)
        assertSame(bb[0].state.overrideMap, bb[1].state.overrideMap)
        assertSame(bb[0].get2DigitYearStart(), bb[1].get2DigitYearStart())
        assertEquals(field(aa[0], "tzFormat") === field(aa[1], "tzFormat"), bb[0].state.zoneFormat === bb[1].state.zoneFormat)
        aa[1].setNumberFormat("M", SourceNumber.getInstance(SourceLocale("en@numbers=deva")))
        bb[1].setNumberFormat("M", NumberFormat.getInstance(ULocale("en@numbers=deva")))
        compare(aa[0], bb[0]); compare(aa[1], bb[1])
    }
    @Test fun lazyCenturyIsWrittenAndLocalizedPatternLocaleFallsBackOnRead() {
        val a = Source("yyyy-MM-dd", SourceLocale.FRANCE)
        val b = SimpleDateFormat("yyyy-MM-dd", ULocale.FRANCE)
        Source::class.java.getDeclaredField("defaultCenturyBase").also { it.isAccessible = true }.setLong(a, 0)
        b.state.defaultCenturyBase = 0
        a.timeZone = com.ibm.icu.util.TimeZone.getTimeZone("Etc/UTC"); b.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
        a.format(java.util.Date(123456789)); b.format(Date(123456789))
        a.applyLocalizedPattern("MM"); b.applyLocalizedPattern("MM")
        val ar = read(bytes(a)) as Source; val br = read(bytes(b)) as SimpleDateFormat
        compare(ar, br)
        assertFalse(br.state.useFastFormat)
        assertEquals("01", br.format(Date(0)))
        assertEquals(0L, br.state.defaultCenturyBase)
    }
}
