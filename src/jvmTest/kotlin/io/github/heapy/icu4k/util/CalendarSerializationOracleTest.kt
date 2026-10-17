@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import com.ibm.icu.util.Calendar as Source
import com.ibm.icu.util.ULocale as SourceLocale
import java.io.*
import kotlin.test.*

class CalendarSerializationOracleTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { b -> ObjectOutputStream(b).use { it.writeObject(value) } }.toByteArray()
    private fun read(data: ByteArray, from: String? = null, to: String? = null): Any = object : ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (from != null && desc.name.contains(from)) try { return Class.forName(desc.name.replace(from, to!!)) } catch (_: ClassNotFoundException) { }
            return super.resolveClass(desc)
        }
    }.use { it.readObject() }
    private fun compare(a: Source, b: Calendar) {
        assertEquals(a.type, b.getType())
        assertEquals(a.timeInMillis, b.getTimeInMillis())
        assertEquals(a.isLenient, b.isLenient())
        assertEquals(a.firstDayOfWeek, b.getFirstDayOfWeek())
        assertEquals(a.minimalDaysInFirstWeek, b.getMinimalDaysInFirstWeek())
        assertEquals(a.repeatedWallTimeOption, b.getRepeatedWallTimeOption())
        assertEquals(a.skippedWallTimeOption, b.getSkippedWallTimeOption())
        assertEquals(a.getLocale(SourceLocale.VALID_LOCALE)?.toString(), b.getLocale(ULocale.VALID_LOCALE)?.toString())
        assertEquals((0 until a.fieldCount).map { a.get(it) }, (0 until b.getFieldCount()).map { b.get(it) })
    }
    @Test fun allCalendarDescriptorsMatchSource() {
        for (name in listOf("Calendar", "GregorianCalendar", "BuddhistCalendar", "TaiwanCalendar", "JapaneseCalendar", "ChineseCalendar", "DangiCalendar", "HebrewCalendar", "PersianCalendar", "IndianCalendar", "CECalendar", "CopticCalendar", "EthiopicCalendar", "IslamicCalendar")) {
            val a = ObjectStreamClass.lookup(Class.forName("com.ibm.icu.util.$name"))
            val b = ObjectStreamClass.lookup(Class.forName("io.github.heapy.icu4k.util.$name"))
            assertEquals(a.serialVersionUID, b.serialVersionUID, name)
            assertEquals(a.fields.map { Triple(it.name, it.typeCode, it.typeString?.replace("com/ibm/icu/", "io/github/heapy/icu4k/")) }, b.fields.map { Triple(it.name, it.typeCode, it.typeString) }, name)
        }
    }
    @Test fun everyCalendarRoundTripsAndCrossReadsWithLazyFieldsAndAlgorithmState() {
        for (type in listOf("gregorian", "iso8601", "buddhist", "roc", "japanese", "chinese", "dangi", "hebrew", "persian", "indian", "coptic", "ethiopic", "ethiopic-amete-alem", "islamic", "islamic-civil", "islamic-tbla", "islamic-umalqura")) {
            val id = "en_US@calendar=$type"
            val a = Source.getInstance(com.ibm.icu.util.TimeZone.getTimeZone("America/New_York"), SourceLocale(id))
            val b = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"), ULocale(id))
            a.firstDayOfWeek = 2; b.setFirstDayOfWeek(2)
            a.minimalDaysInFirstWeek = 4; b.setMinimalDaysInFirstWeek(4)
            a.repeatedWallTimeOption = 1; b.setRepeatedWallTimeOption(1)
            a.skippedWallTimeOption = 2; b.setSkippedWallTimeOption(2)
            for (time in listOf(0L, -62167219200000L, 951827696123L, 1712345678901L)) {
                a.timeInMillis = time; b.setTimeInMillis(time)
                val ar = read(bytes(a)) as Source; val br = read(bytes(b)) as Calendar
                compare(ar, br)
                compare(read(bytes(b), "io.github.heapy.icu4k.", "com.ibm.icu.") as Source, read(bytes(a), "com.ibm.icu.", "io.github.heapy.icu4k.") as Calendar)
                for (field in listOf(Calendar.YEAR, Calendar.MONTH, Calendar.DATE)) {
                    ar.add(field, 1); br.add(field, 1); compare(ar, br)
                }
            }
        }
    }
    @Test fun unresolvedInvalidFieldsDoNotPreventSerialization() {
        val a = com.ibm.icu.util.GregorianCalendar(com.ibm.icu.util.TimeZone.getTimeZone("Etc/UTC"), SourceLocale.US)
        val b = GregorianCalendar(TimeZone.getTimeZone("Etc/UTC"), ULocale.US)
        a.timeInMillis = 0; b.setTimeInMillis(0)
        a.isLenient = false; b.setLenient(false)
        a.set(Source.MONTH, 99); b.set(Calendar.MONTH, 99)
        compare(read(bytes(a)) as Source, read(bytes(b)) as Calendar)
    }
}
