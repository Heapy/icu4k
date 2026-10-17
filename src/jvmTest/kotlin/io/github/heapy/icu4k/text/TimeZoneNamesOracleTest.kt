@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.TimeZoneNames as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.TimeZoneNamesImpl
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import java.util.EnumSet
import kotlin.test.*

class TimeZoneNamesOracleTest {
    private fun sourceType(type: TimeZoneNames.NameType) = Source.NameType.valueOf(type.name)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        if (e is NullPointerException) "null" else if (e is IndexOutOfBoundsException) "bounds" else e.javaClass.simpleName + ":" + e.message
    }
    @Test fun factoriesLocalesAndEveryNameType() {
        for (id in listOf("en_US", "fr_FR", "ru", "ar_EG", "ja", "zh_Hant", "en_US@calendar=hebrew", "zz_ZZ")) {
            val a = Source.getInstance(SourceLocale(id)); val b = TimeZoneNames.getInstance(ULocale(id))!!
            assertEquals(a.javaClass.simpleName, b.javaClass.simpleName)
            assertEquals(a.availableMetaZoneIDs, b.getAvailableMetaZoneIDs())
            for (zone in listOf(null, "", "America/New_York", "Europe/London", "Asia/Kolkata", "America/Indiana/Knox", "Etc/Unknown", "Bad/ID")) {
                assertEquals(a.getAvailableMetaZoneIDs(zone), b.getAvailableMetaZoneIDs(zone))
                assertEquals(a.getExemplarLocationName(zone), b.getExemplarLocationName(zone))
                for (date in listOf(Long.MIN_VALUE, -2208988800000L, 0L, 1710000000000L, Long.MAX_VALUE)) {
                    assertEquals(a.getMetaZoneID(zone, date), b.getMetaZoneID(zone, date))
                    for (type in TimeZoneNames.NameType.entries) {
                        assertEquals(a.getDisplayName(zone, sourceType(type), date), b.getDisplayName(zone, type, date), "$id/$zone/$type/$date")
                        assertEquals(a.getTimeZoneDisplayName(zone, sourceType(type)), b.getTimeZoneDisplayName(zone, type))
                    }
                }
            }
            for (mz in listOf(null, "", "America_Eastern", "Europe_Central", "India", "not-a-zone")) {
                for (region in listOf(null, "", "US", "CA", "001", "ZZ")) assertEquals(a.getReferenceZoneID(mz, region), b.getReferenceZoneID(mz, region))
                for (type in TimeZoneNames.NameType.entries) assertEquals(a.getMetaZoneDisplayName(mz, sourceType(type)), b.getMetaZoneDisplayName(mz, type))
            }
        }
        val b = TimeZoneNames.getInstance(ULocale.US)
        assertSame(b, TimeZoneNames.getInstance(ULocale("en_US@calendar=chinese")))
        assertSame(b, TimeZoneNames.getInstance(java.util.Locale.US))
        assertEquals(outcome { Source.getInstance(null as SourceLocale?) }, outcome { TimeZoneNames.getInstance(null as ULocale?) })
    }
    private fun matches(a: Source, b: TimeZoneNames, text: String?, start: Int, types: Set<TimeZoneNames.NameType>?) {
        val sourceTypes = types?.let { EnumSet.noneOf(Source.NameType::class.java).also { s -> it.forEach { t -> s.add(sourceType(t)) } } }
        val left = outcome { a.find(text, start, sourceTypes).map { "${it.nameType()}:${it.tzID()}:${it.mzID()}:${it.matchLength()}" }.sorted() }
        val right = outcome { b.find(text, start, types).map { "${it.nameType()}:${it.tzID()}:${it.mzID()}:${it.matchLength()}" }.sorted() }
        assertEquals(left, right, "$text/$start/$types")
    }
    @Test fun localizedAndTZDBPrefixMatches() {
        for (id in listOf("en_US", "fr", "en_IL", "en_IN", "zh")) for (tzdb in listOf(false, true)) {
            val a = if (tzdb) Source.getTZDBInstance(SourceLocale(id)) else Source.getInstance(SourceLocale(id))
            val b = if (tzdb) TimeZoneNames.getTZDBInstance(ULocale(id)) else TimeZoneNames.getInstance(ULocale(id))!!
            for (text in listOf("Eastern Standard Time", "xxEastern Time suffix", "IST", "CST", "GMT", "heure normale de l’Est nord-américain", "New York", "not-a-name", "", null)) {
                for (start in listOf(-1, 0, 2, text?.length ?: 0)) for (types in listOf(null, emptySet(), setOf(TimeZoneNames.NameType.SHORT_STANDARD, TimeZoneNames.NameType.SHORT_DAYLIGHT), TimeZoneNames.NameType.entries.toSet())) matches(a, b, text, start, types)
            }
        }
    }
    @Test fun bulkNullsErrorsAndMatchInfoValidation() {
        val a = Source.getInstance(SourceLocale.US); val b = TimeZoneNames.getInstance(ULocale.US)!!
        for (zone in listOf(null, "", "Europe/London", "Bad/ID")) for (offset in listOf(-1, 0, 1, 5)) {
            val at = Source.NameType.values(); val bt = TimeZoneNames.NameType.entries.toTypedArray()
            val ad = arrayOfNulls<String>(8); val bd = arrayOfNulls<String>(8)
            assertEquals(outcome { a.getDisplayNames(zone, at, 0, ad, offset) }, outcome { b.getDisplayNames(zone, bt, 0, bd, offset) })
            assertContentEquals(ad, bd)
            assertEquals(outcome { a.getDisplayNames(zone, null, 0, null, offset) }, outcome { b.getDisplayNames(zone, null, 0, null, offset) })
            assertEquals(outcome { a.getTimeZoneDisplayName(zone, null) }, outcome { b.getTimeZoneDisplayName(zone, null) })
        }
        for (type in listOf(null, TimeZoneNames.NameType.LONG_GENERIC)) for (tz in listOf(null, "")) for (mz in listOf(null, "meta")) for (length in listOf(-1, 0, 1)) {
            assertEquals(outcome { Source.MatchInfo(type?.let(::sourceType), tz, mz, length).let { listOf(it.nameType().name, it.tzID(), it.mzID(), it.matchLength()) } }, outcome { TimeZoneNames.MatchInfo(type, tz, mz, length).let { listOf(it.nameType().name, it.tzID(), it.mzID(), it.matchLength()) } })
        }
    }
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { b -> ObjectOutputStream(b).use { it.writeObject(value) } }.toByteArray()
    private fun read(bytes: ByteArray, from: String? = null, to: String? = null): Any = object : ObjectInputStream(ByteArrayInputStream(bytes)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (from != null && desc.name.startsWith(from)) return Class.forName(desc.name.replace(from, to!!))
            return super.resolveClass(desc)
        }
    }.use { it.readObject() }
    @Test fun exactDescriptorsAndCrossReadSerialization() {
        for ((ac, bc) in listOf(Source::class.java to TimeZoneNames::class.java, com.ibm.icu.impl.TimeZoneNamesImpl::class.java to TimeZoneNamesImpl::class.java, com.ibm.icu.impl.TZDBTimeZoneNames::class.java to io.github.heapy.icu4k.impl.TZDBTimeZoneNames::class.java)) {
            val a = ObjectStreamClass.lookup(ac); val b = ObjectStreamClass.lookup(bc)
            assertEquals(a.serialVersionUID, b.serialVersionUID)
            assertEquals(a.fields.map { it.name to it.typeString?.replace("com/ibm/icu/", "io/github/heapy/icu4k/") }, b.fields.map { it.name to it.typeString })
        }
        for (id in listOf("fr_CA_ZONE_SERIAL", "en_IL@calendar=hebrew")) for (tzdb in listOf(false, true)) {
            val a = if (tzdb) Source.getTZDBInstance(SourceLocale(id)) else Source.getInstance(SourceLocale(id))
            val b = if (tzdb) TimeZoneNames.getTZDBInstance(ULocale(id)) else TimeZoneNames.getInstance(ULocale(id))!!
            a.loadAllDisplayNames(); b.loadAllDisplayNames()
            val restoredA = read(bytes(a)) as Source; val restoredB = read(bytes(b)) as TimeZoneNames
            val crossA = read(bytes(b), "io.github.heapy.icu4k.", "com.ibm.icu.") as Source
            val crossB = read(bytes(a), "com.ibm.icu.", "io.github.heapy.icu4k.") as TimeZoneNames
            for (type in TimeZoneNames.NameType.entries) for (zone in listOf("Europe/London", "Asia/Calcutta", "America/New_York")) {
                val expected = a.getDisplayName(zone, sourceType(type), 0)
                assertEquals(expected, restoredA.getDisplayName(zone, sourceType(type), 0))
                assertEquals(expected, restoredB.getDisplayName(zone, type, 0))
                assertEquals(expected, crossA.getDisplayName(zone, sourceType(type), 0))
                assertEquals(expected, crossB.getDisplayName(zone, type, 0))
            }
            matches(restoredA, restoredB, "IST", 0, setOf(TimeZoneNames.NameType.SHORT_STANDARD))
        }
    }

    private class SourceBase : Source() {
        val calls = mutableListOf<String>()
        override fun getAvailableMetaZoneIDs(): Set<String> = emptySet()
        override fun getAvailableMetaZoneIDs(id: String?): Set<String> = emptySet()
        override fun getMetaZoneID(id: String?, date: Long): String? { calls.add("meta:$id:$date"); return null }
        override fun getReferenceZoneID(id: String?, region: String?): String? = null
        override fun getMetaZoneDisplayName(id: String?, type: Source.NameType?): String? { calls.add("metaName:$id:$type"); return "fallback" }
        override fun getTimeZoneDisplayName(id: String?, type: Source.NameType?): String? { calls.add("zoneName:$id:$type"); return null }
    }
    private class PortBase : TimeZoneNames() {
        val calls = mutableListOf<String>()
        override fun getAvailableMetaZoneIDs(): Set<String> = emptySet()
        override fun getAvailableMetaZoneIDs(id: String?): Set<String> = emptySet()
        override fun getMetaZoneID(id: String?, date: Long): String? { calls.add("meta:$id:$date"); return null }
        override fun getReferenceZoneID(id: String?, region: String?): String? = null
        override fun getMetaZoneDisplayName(id: String?, type: NameType?): String? { calls.add("metaName:$id:$type"); return "fallback" }
        override fun getTimeZoneDisplayName(id: String?, type: NameType?): String? { calls.add("zoneName:$id:$type"); return null }
    }
    @Test fun baseFallbackUsesVirtualGettersAndSourceCallOrder() {
        val a = SourceBase(); val b = PortBase()
        assertEquals(a.getDisplayName("Custom/Zone", Source.NameType.LONG_GENERIC, 1), b.getDisplayName("Custom/Zone", TimeZoneNames.NameType.LONG_GENERIC, 1))
        val ad = arrayOfNulls<String>(3); val bd = arrayOfNulls<String>(3)
        a.getDisplayNames("Custom/Zone", arrayOf(Source.NameType.LONG_GENERIC, null, Source.NameType.SHORT_STANDARD), 2, ad, 0)
        b.getDisplayNames("Custom/Zone", arrayOf(TimeZoneNames.NameType.LONG_GENERIC, null, TimeZoneNames.NameType.SHORT_STANDARD), 2, bd, 0)
        assertContentEquals(ad, bd); assertEquals(a.calls, b.calls)
        assertEquals(outcome { a.find(null, -1, null) }, outcome { b.find(null, -1, null) })
        for (id in listOf("Custom/New_York", "NoSlash", "/leading", "trailing/", "Etc/Unknown", "Asia/Riyadh87", "Etc/line\nname")) {
            assertEquals(a.getExemplarLocationName(id), b.getExemplarLocationName(id))
        }
    }
    private class SourceOverride : com.ibm.icu.impl.TimeZoneNamesImpl(SourceLocale.US) {
        override fun getAvailableMetaZoneIDs(id: String?): Set<String> { sourceCalls.add("available:$id"); return super.getAvailableMetaZoneIDs(id) }
        override fun getMetaZoneID(id: String?, date: Long): String { sourceCalls.add("meta:$id:$date"); return "Europe_Central" }
    }
    private class PortOverride : TimeZoneNamesImpl(ULocale.US) {
        override fun getAvailableMetaZoneIDs(id: String?): Set<String> { portCalls.add("available:$id"); return super.getAvailableMetaZoneIDs(id) }
        override fun getMetaZoneID(id: String?, date: Long): String { portCalls.add("meta:$id:$date"); return "Europe_Central" }
    }
    @Test fun concreteProviderKeepsVirtualConstructionAndBulkMapping() {
        val sourceZone = com.ibm.icu.util.TimeZone.getDefault(); val portZone = io.github.heapy.icu4k.util.TimeZone.getDefault()
        sourceCalls.clear(); portCalls.clear()
        try {
            com.ibm.icu.util.TimeZone.setDefault(com.ibm.icu.util.TimeZone.getTimeZone("America/New_York"))
            io.github.heapy.icu4k.util.TimeZone.setDefault(io.github.heapy.icu4k.util.TimeZone.getTimeZone("America/New_York"))
            val a = SourceOverride(); val b = PortOverride()
            assertTrue(sourceCalls.isNotEmpty()); assertEquals(sourceCalls, portCalls)
            val ad = arrayOfNulls<String>(3); val bd = arrayOfNulls<String>(3)
            a.getDisplayNames("Custom/Zone", arrayOf(Source.NameType.LONG_GENERIC, Source.NameType.SHORT_STANDARD, Source.NameType.LONG_DAYLIGHT), 3, ad, 0)
            b.getDisplayNames("Custom/Zone", arrayOf(TimeZoneNames.NameType.LONG_GENERIC, TimeZoneNames.NameType.SHORT_STANDARD, TimeZoneNames.NameType.LONG_DAYLIGHT), 3, bd, 0)
            assertContentEquals(ad, bd); assertEquals(sourceCalls, portCalls)
        } finally {
            com.ibm.icu.util.TimeZone.setDefault(sourceZone)
            io.github.heapy.icu4k.util.TimeZone.setDefault(portZone)
        }
    }
    @Test fun configuredFactoriesFallbackAndNullCache() {
        val classpath = listOf(TimeZoneNamesFactoryProbe::class.java, TimeZoneNames::class.java, Source::class.java, kotlin.Unit::class.java)
            .map { java.io.File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(java.io.File.pathSeparator)
        fun run(mode: String, scenario: String): String {
            val process = ProcessBuilder(java.io.File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath,
                TimeZoneNamesFactoryProbe::class.java.name, mode, scenario).redirectErrorStream(true).start()
            try {
                assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS), "factory probe timed out")
                val result = process.inputStream.bufferedReader().readText().trim()
                assertEquals(0, process.exitValue(), result)
                return result
            } finally { if (process.isAlive) process.destroyForcibly() }
        }
        for (scenario in listOf("missing", "null", "custom", "wrong")) assertEquals(run("source", scenario), run("port", scenario), scenario)
    }
    companion object {
        private val sourceCalls = mutableListOf<String>()
        private val portCalls = mutableListOf<String>()
    }
}
