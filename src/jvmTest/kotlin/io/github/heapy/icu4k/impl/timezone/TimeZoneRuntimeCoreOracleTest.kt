package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.impl.JavaTimeZone
import com.ibm.icu.impl.OlsonTimeZone
import com.ibm.icu.impl.TimeZoneAdapter
import com.ibm.icu.impl.ZoneMeta
import com.ibm.icu.util.TimeZone
import kotlin.test.*

class TimeZoneRuntimeCoreOracleTest {
    private fun core(configuration: String = "ICU") = TimeZoneRuntimeCore(
        object : TimeZoneRuntimeCore.Owner<TimeZone, java.util.TimeZone> {
            override fun createJavaTimeZone(id: String?) = JavaTimeZone.createTimeZone(id)
            override fun createDefaultJavaTimeZone() = JavaTimeZone()
            override fun getSystemTimeZone(id: String?) = ZoneMeta.getSystemTimeZone(id)
            override fun getCustomTimeZone(id: String?) = ZoneMeta.getCustomTimeZone(id)
            override fun unknownZone() = TimeZone.UNKNOWN_ZONE
            override fun reportUnknownID(id: String?) = Unit
            override fun isFrozen(zone: TimeZone) = zone.isFrozen
            override fun clone(zone: TimeZone) = zone.clone()
            override fun freeze(zone: TimeZone) = zone.freeze()
            override fun cloneAsThawed(zone: TimeZone) = zone.cloneAsThawed()
            override fun isJavaTimeZone(zone: TimeZone) = zone is JavaTimeZone
            override fun unwrapJavaTimeZone(zone: TimeZone) = (zone as JavaTimeZone).unwrap()
            override fun isOlsonTimeZone(zone: TimeZone) = zone is OlsonTimeZone
            override fun getID(zone: TimeZone) = zone.id
            override fun getCanonicalID(id: String?) = TimeZone.getCanonicalID(id)
            override fun wrap(zone: TimeZone) = TimeZoneAdapter.wrap(zone)
        },
        object : TimeZoneRuntimeCore.Host<java.util.TimeZone> {
            override fun <T> withDefaultLock(block: () -> T): T = synchronized(java.util.TimeZone::class.java, block)
            override fun getDefault(): java.util.TimeZone = java.util.TimeZone.getDefault()
            override fun getID(zone: java.util.TimeZone): String = zone.id
            override fun getTimeZone(id: String?): java.util.TimeZone = java.util.TimeZone.getTimeZone(id)
            override fun setDefault(zone: java.util.TimeZone) = java.util.TimeZone.setDefault(zone)
        }, configuration,
    )

    private fun snapshot(zone: TimeZone?): List<Any?> = zone?.let {
        listOf(it.javaClass.name, it.id, it.rawOffset, it.isFrozen, it.getOffset(0), it.getOffset(1750000000000))
    } ?: listOf(null)

    @Test fun factoriesMatchEverySystemIDAndCustomFallback() {
        val core = core()
        for (type in listOf(0, 1, -1, 2, Int.MAX_VALUE)) {
            for (id in TimeZone.getAvailableIDs().toList() + listOf("GMT+05:45", "GMT-23:59:59", "bad", "", "Etc/Unknown")) {
                assertEquals(snapshot(TimeZone.getTimeZone(id, type)), snapshot(core.getTimeZone(id, type)), "$type/$id")
            }
        }
        val oldType = TimeZone.getDefaultTimeZoneType()
        try {
            for (type in listOf(0, 1)) {
                TimeZone.setDefaultTimeZoneType(type)
                core.setDefaultTimeZoneType(type)
                for (id in listOf("America/New_York", "GMT+05:45", "bad")) {
                    assertEquals(snapshot(TimeZone.getFrozenTimeZone(id)), snapshot(core.getFrozenTimeZone(id)))
                }
            }
        } finally { TimeZone.setDefaultTimeZoneType(oldType) }
        assertFailsWith<IllegalArgumentException> { core.setDefaultTimeZoneType(2) }
        assertEquals(1, core.getDefaultTimeZoneType())
        assertEquals(1, core("jDk").getDefaultTimeZoneType())
        assertEquals(0, core("other").getDefaultTimeZoneType())
    }

    @Test fun defaultCacheAndHostOwnershipMatchICU() {
        val originalHost = java.util.TimeZone.getDefault()
        val originalDefault = TimeZone.getDefault()
        val originalType = TimeZone.getDefaultTimeZoneType()
        try {
            for (type in listOf(0, 1)) {
                val core = core(if (type == 1) "JDK" else "ICU")
                TimeZone.setDefaultTimeZoneType(type)
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
                TimeZone.setICUDefault(null)
                assertEquals(snapshot(TimeZone.getDefault()), snapshot(core.getDefault()))
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Paris"))
                assertEquals("Asia/Tokyo", core.getDefault()!!.id)
                core.setDefaultTimeZoneType(1 - type)
                assertEquals("Asia/Tokyo", core.getDefault()!!.id)
                core.setDefault(null)
                assertEquals("Europe/Paris", java.util.TimeZone.getDefault().id)

                for (zone in listOf(TimeZone.getTimeZone("US/Eastern"), JavaTimeZone(),
                    com.ibm.icu.util.SimpleTimeZone(1234567, "custom"), TimeZone.GMT_ZONE)) {
                    TimeZone.setDefault(zone)
                    val expectedHost = java.util.TimeZone.getDefault()
                    val expected = TimeZone.getDefault()
                    core.setDefault(zone)
                    val actualHost = java.util.TimeZone.getDefault()
                    assertEquals(expectedHost.javaClass, actualHost.javaClass)
                    assertEquals(expectedHost.id, actualHost.id)
                    assertEquals(expectedHost.rawOffset, actualHost.rawOffset)
                    assertEquals(snapshot(expected), snapshot(core.getDefault()))
                    val first = core.getDefault()!!
                    val second = core.getDefault()!!
                    assertNotSame(first, second)
                    assertFalse(first.isFrozen)
                    first.id = "changed"
                    assertEquals(zone.id, core.getDefault()!!.id)
                }
            }
        } finally {
            TimeZone.setDefaultTimeZoneType(originalType)
            TimeZone.setICUDefault(originalDefault)
            java.util.TimeZone.setDefault(originalHost)
        }
    }
}
