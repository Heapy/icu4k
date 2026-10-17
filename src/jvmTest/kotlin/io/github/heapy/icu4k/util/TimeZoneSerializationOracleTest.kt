@file:Suppress("UNCHECKED_CAST")
package io.github.heapy.icu4k.util

import com.ibm.icu.util.TimeZone as Source
import com.ibm.icu.util.SimpleTimeZone as SourceSimple
import com.ibm.icu.util.RuleBasedTimeZone as SourceRules
import com.ibm.icu.impl.OlsonTimeZone as SourceOlson
import io.github.heapy.icu4k.impl.OlsonTimeZone
import java.io.*
import kotlin.test.*

class TimeZoneSerializationOracleTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { b -> ObjectOutputStream(b).use { it.writeObject(value) } }.toByteArray()
    private fun read(data: ByteArray, from: String? = null, to: String? = null): Any = object : ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (from != null && desc.name.contains(from)) try { return Class.forName(desc.name.replace(from, to!!)) } catch (_: ClassNotFoundException) { }
            return super.resolveClass(desc)
        }
    }.use { it.readObject() }
    private fun compare(a: Source, b: TimeZone) {
        assertEquals(a.id, b.getID())
        assertEquals(a.isFrozen, b.isFrozen())
        for (time in listOf(-62167219200000L, -2300000000000L, 0L, 951827696123L, 1712345678901L, 4102444800000L)) {
            for (local in listOf(false, true)) {
                val aa = IntArray(2); val bb = IntArray(2)
                a.getOffset(time, local, aa); b.getOffset(time, local, bb)
                assertContentEquals(aa, bb, "${a.id}/$time/$local")
            }
        }
        if (a is com.ibm.icu.util.BasicTimeZone && b is BasicTimeZone) {
            assertEquals(a.timeZoneRules.map { it?.toString() }, b.getTimeZoneRules()!!.map { it?.toString() })
            for (time in listOf(-2300000000000L, 0L, 1712345678901L, 4102444800000L)) {
                assertEquals(a.getNextTransition(time, false)?.toString(), b.getNextTransition(time, false)?.toString())
                assertEquals(a.getPreviousTransition(time, true)?.toString(), b.getPreviousTransition(time, true)?.toString())
            }
        }
        fun canonical(query: () -> String?): String? = try { query() } catch (_: AssertionError) { "assertion" }
        if (a is SourceOlson && b is OlsonTimeZone) assertEquals(canonical { a.canonicalID }, canonical { b.getCanonicalID() })
    }
    private fun pairs(): List<Pair<Source, TimeZone>> {
        val a = SourceSimple(1000, "rules", 2, 2, 1, 7200000, 10, 1, 1, 7200000)
        val b = SimpleTimeZone(1000, "rules", 2, 2, 1, 7200000, 10, 1, 1, 7200000)
        a.setStartYear(1999); b.setStartYear(1999)
        a.setStartRule(2, 12, 1, 1234567, true); b.setStartRule(2, 12, 1, 1234567, true)
        a.setEndRule(10, 9, 7654321); b.setEndRule(10, 9, 7654321)
        val ar = SourceRules("transitions", com.ibm.icu.util.InitialTimeZoneRule("initial", 0, 0))
        val br = RuleBasedTimeZone("transitions", InitialTimeZoneRule("initial", 0, 0))
        ar.addTransitionRule(com.ibm.icu.util.TimeArrayTimeZoneRule("historical", 3600000, 0, longArrayOf(0), 2))
        br.addTransitionRule(TimeArrayTimeZoneRule("historical", 3600000, 0, longArrayOf(0), 2))
        val result = arrayListOf<Pair<Source, TimeZone>>(a to b, ar to br, Source.UNKNOWN_ZONE to TimeZone.UNKNOWN_ZONE, Source.GMT_ZONE to TimeZone.GMT_ZONE)
        for (id in listOf("America/New_York", "Europe/Dublin", "Asia/Tokyo", "Etc/UTC")) {
            val az = SourceOlson(id); val bz = OlsonTimeZone(id)
            result.add(az to bz)
            val ac = az.cloneAsThawed(); val bc = bz.cloneAsThawed()
            ac.rawOffset = 12345000; bc.setRawOffset(12345000)
            ac.id = "custom-$id"; bc.setID("custom-$id")
            result.add(ac to bc)
        }
        return result
    }
    @Test fun sourceDescriptorsMatchAndAllZoneKindsCrossRead() {
        for (name in listOf("util.TimeZone", "util.BasicTimeZone", "util.SimpleTimeZone", "util.RuleBasedTimeZone", "impl.OlsonTimeZone", "util.STZInfo", "util.TimeZone\$ConstantZone")) {
            val a = ObjectStreamClass.lookup(Class.forName("com.ibm.icu.$name")); val b = ObjectStreamClass.lookup(Class.forName("io.github.heapy.icu4k.$name"))
            assertEquals(a.serialVersionUID, b.serialVersionUID, name)
            assertEquals(a.fields.map { Triple(it.name, it.typeCode, it.typeString?.replace("com/ibm/icu/", "io/github/heapy/icu4k/")) }, b.fields.map { Triple(it.name, it.typeCode, it.typeString) }, name)
        }
        for ((a, b) in pairs()) {
            compare(read(bytes(a)) as Source, read(bytes(b)) as TimeZone)
            compare(read(bytes(b), "io.github.heapy.icu4k.", "com.ibm.icu.") as Source, read(bytes(a), "com.ibm.icu.", "io.github.heapy.icu4k.") as TimeZone)
        }
    }
    @Test fun legacyOlsonStreamsReloadResourcesOrConstructEmptyWithoutChangingVersion() {
        for (id in listOf("America/New_York", "not-a-zone")) {
            val a = SourceOlson("Etc/UTC"); val b = OlsonTimeZone("Etc/UTC")
            a.id = id; b.setID(id)
            SourceOlson::class.java.getDeclaredField("serialVersionOnStream").also { it.isAccessible = true }.setInt(a, 0)
            val core = OlsonTimeZone::class.java.getDeclaredField("core").also { it.isAccessible = true }.get(b)
            core.javaClass.getDeclaredField("serialVersionOnStream").also { it.isAccessible = true }.setInt(core, 0)
            val ar = read(bytes(a)) as Source; val br = read(bytes(b)) as TimeZone
            compare(ar, br)
            compare(read(bytes(ar)) as Source, read(bytes(br)) as TimeZone)
        }
    }
}
