@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.JavaTimeZone
import io.github.heapy.icu4k.impl.TimeZoneAdapter
import kotlin.test.*
import com.ibm.icu.util.TimeZone as JZone
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.impl.JavaTimeZone as JJava
import com.ibm.icu.impl.TimeZoneAdapter as JAdapter

class TimeZoneFactoryOracleTest {
    private fun compare(k: TimeZone, j: JZone, label: String) {
        assertEquals(j.id, k.getID(), label)
        assertEquals(j.javaClass.simpleName, k.javaClass.simpleName, label)
        assertEquals(j.isFrozen, k.isFrozen(), label)
        assertEquals(j.rawOffset, k.getRawOffset(), label)
        assertEquals(j.useDaylightTime(), k.useDaylightTime(), label)
        assertEquals(j.observesDaylightTime(), k.observesDaylightTime(), label)
        assertEquals(j.dstSavings, k.getDSTSavings(), label)
        for (date in listOf(-2208988800000L, 0L, 1710037800000L, 1730597400000L, 4102444800000L)) {
            for (local in listOf(false, true)) {
                val ko = IntArray(2); val jo = IntArray(2)
                k.getOffset(date, local, ko); j.getOffset(date, local, jo)
                assertContentEquals(jo, ko, "$label/$date/$local")
            }
        }
    }
    @Test fun allIcuAndJdkIdsAndCustomFallbacksMatchFactories() {
        val ids = (TimeZone.getAvailableIDs().toList() + java.util.TimeZone.getAvailableIDs().toList() +
            listOf("", "Etc/Unknown", "GMT+5:30", "GMT-001530", "GMT+25:00", "gmt+0530", "unrecognized")).distinct()
        for (type in listOf(TimeZone.TIMEZONE_ICU, TimeZone.TIMEZONE_JDK, 99)) for (id in ids) {
            compare(TimeZone.getTimeZone(id, type), JZone.getTimeZone(id, type), "$type/$id")
        }
        val kt = TimeZone.getDefaultTimeZoneType(); val jt = JZone.getDefaultTimeZoneType()
        try {
            for (type in 0..1) {
                TimeZone.setDefaultTimeZoneType(type); JZone.setDefaultTimeZoneType(type)
                for (id in listOf("Europe/Minsk", "US/Eastern", "GMT+0530", "unrecognized")) {
                    compare(TimeZone.getFrozenTimeZone(id), JZone.getFrozenTimeZone(id), "frozen/$type/$id")
                }
            }
        } finally { TimeZone.setDefaultTimeZoneType(kt); JZone.setDefaultTimeZoneType(jt) }
        for (type in 0..1) {
            assertFailsWith<NullPointerException> { TimeZone.getTimeZone(null, type) }
            assertFailsWith<NullPointerException> { JZone.getTimeZone(null, type) }
        }
    }
    @Test fun defaultHostSynchronizationAndNullResetMatchSource() {
        val host = java.util.TimeZone.getDefault()
        val oldK = TimeZone.getDefault(); val oldJ = JZone.getDefault()
        val kt = TimeZone.getDefaultTimeZoneType(); val jt = JZone.getDefaultTimeZoneType()
        try {
            for (type in 0..1) {
                TimeZone.setDefaultTimeZoneType(type); JZone.setDefaultTimeZoneType(type)
                for (id in listOf("Europe/Minsk", "US/Eastern", "GMT+05:30", "unrecognized")) {
                    val k = TimeZone.getTimeZone(id, type); val j = JZone.getTimeZone(id, type)
                    TimeZone.setDefault(k)
                    val kh = java.util.TimeZone.getDefault()
                    JZone.setDefault(j)
                    val jh = java.util.TimeZone.getDefault()
                    assertEquals(jh.id, kh.id); assertEquals(jh.getOffset(0), kh.getOffset(0))
                    compare(TimeZone.getDefault(), JZone.getDefault(), "default/$type/$id")
                    k.setRawOffset(k.getRawOffset() + 123000); j.rawOffset = j.rawOffset + 123000
                    compare(TimeZone.getDefault(), JZone.getDefault(), "defensive/$type/$id")
                    TimeZone.setDefault(null); JZone.setDefault(null)
                    compare(TimeZone.getDefault(), JZone.getDefault(), "reset/$type/$id")
                }
            }
            TimeZone.setDefaultTimeZoneType(0); JZone.setDefaultTimeZoneType(0)
            TimeZone.setICUDefault(SimpleTimeZone(123000, "icu-only")); JZone.setICUDefault(com.ibm.icu.util.SimpleTimeZone(123000, "icu-only"))
            for (locale in listOf("en", "en@timezone=Europe/Minsk", "en-u-tz-usnyc", "en@timezone=bogus")) {
                compare(TimeZone.forULocaleOrDefault(ULocale(locale)), JZone.forULocaleOrDefault(JLocale(locale)), locale)
            }
            for (tag in listOf("en-US", "en-u-tz-usnyc")) {
                val locale = java.util.Locale.forLanguageTag(tag)
                compare(TimeZone.forLocaleOrDefault(locale), JZone.forLocaleOrDefault(locale), tag)
            }
            assertFailsWith<NullPointerException> { TimeZone.forLocaleOrDefault(null) }
            assertFailsWith<NullPointerException> { JZone.forLocaleOrDefault(null) }
        } finally {
            java.util.TimeZone.setDefault(host)
            TimeZone.setICUDefault(oldK); JZone.setICUDefault(oldJ)
            TimeZone.setDefaultTimeZoneType(kt); JZone.setDefaultTimeZoneType(jt)
        }
    }
    @Test fun jdkCloneCalendarBindingMatchesSourceAfterMutation() {
        val k = JavaTimeZone(java.util.SimpleTimeZone(0, "private"), null)
        val j = JJava(java.util.SimpleTimeZone(0, "private"), null)
        val kc = k.cloneAsThawed(); val jc = j.cloneAsThawed()
        k.setRawOffset(3600000); j.rawOffset = 3600000
        compare(kc, jc, "clone-after-original-mutation")
        assertEquals(0, kc.getRawOffset())
        val offsets = IntArray(2); kc.getOffset(0, false, offsets)
        assertEquals(3600000, offsets[0])
        kc.setRawOffset(7200000); jc.rawOffset = 7200000
        compare(kc, jc, "clone-after-own-mutation")
        assertEquals(jc.hashCode(), kc.hashCode())
    }
    @Test fun adapterCloneAndFailedRenamePreserveSourceOrder() {
        val k = SimpleTimeZone(123000, "private"); val j = com.ibm.icu.util.SimpleTimeZone(123000, "private")
        val ka = TimeZoneAdapter(k); val ja = JAdapter(j)
        assertEquals(ja.getOffset(0), ka.getOffset(0)); assertSame(k, ka.unwrap())
        val kc = ka.clone(); val jc = ja.clone()
        kc.rawOffset = 456000; jc.rawOffset = 456000
        assertEquals(j.rawOffset, k.getRawOffset()); assertEquals(jc.rawOffset, kc.rawOffset)
        k.freeze(); j.freeze()
        assertFailsWith<UnsupportedOperationException> { ka.id = "renamed" }
        assertFailsWith<UnsupportedOperationException> { ja.id = "renamed" }
        assertEquals(ja.id, ka.id); assertEquals(j.id, k.getID())
        assertSame(k, ka.clone().unwrap())
    }
    @Test fun jdkOffsetArrayFailuresPreservePartialWrites() {
        val k = JavaTimeZone.createTimeZone("America/New_York")!!
        val j = JJava.createTimeZone("America/New_York")!!
        for (local in listOf(false, true)) for (size in 0..1) {
            val ko = IntArray(size) { 42 }; val jo = IntArray(size) { 42 }
            assertFailsWith<IndexOutOfBoundsException> { k.getOffset(1710037800000L, local, ko) }
            assertFailsWith<IndexOutOfBoundsException> { j.getOffset(1710037800000L, local, jo) }
            assertContentEquals(jo, ko)
        }
        assertFailsWith<NullPointerException> { k.getOffset(0, false, null) }
        assertFailsWith<NullPointerException> { j.getOffset(0, false, null) }
    }
    @Test fun unknownIdFineLogMatchesSourceMessage() {
        fun capture(name: String, block: () -> Unit): List<String> {
            val logger = java.util.logging.Logger.getLogger(name)
            val level = logger.level; val parents = logger.useParentHandlers
            val messages = arrayListOf<String>()
            val handler = object : java.util.logging.Handler() {
                override fun publish(record: java.util.logging.LogRecord) { messages.add(record.message) }
                override fun flush() { }
                override fun close() { }
            }
            logger.level = java.util.logging.Level.FINE; logger.useParentHandlers = false; logger.addHandler(handler)
            return try { block(); messages } finally { logger.removeHandler(handler); logger.level = level; logger.useParentHandlers = parents }
        }
        val k = capture(TimeZone::class.java.name) { TimeZone.getTimeZone("unknown-log-probe", 0) }
        val j = capture(JZone::class.java.name) { JZone.getTimeZone("unknown-log-probe", 0) }
        assertEquals(j, k); assertEquals(1, k.size)
    }
}
