@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DateTimePatternGenerator as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.impl.UResource
import io.github.heapy.icu4k.util.*
import java.io.*
import kotlin.test.*

class DateTimePatternGeneratorOracleTest {
    private fun outcome(action: () -> Any?): String = try { "ok:${action()}" } catch (e: Exception) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message?.replace("com.ibm.icu.","icu.")?.replace("io.github.heapy.icu4k.","icu.") }
    }
    private fun compare(a: Source, b: DateTimePatternGenerator, id: String) {
        assertEquals(a.isFrozen, b.isFrozen(), id)
        assertEquals(a.decimal, b.getDecimal(), id)
        assertEquals(a.defaultHourFormatChar, b.getDefaultHourFormatChar(), id)
        assertEquals(a.defaultHourCycle.name, b.getDefaultHourCycle().name, id)
        assertEquals(a.getSkeletons(linkedMapOf()).toList(), b.getSkeletons(linkedMapOf()).toList(), id)
        assertEquals(a.getBaseSkeletons(null), b.getBaseSkeletons(null), id)
        for (style in 0..3) assertEquals(a.getDateTimeFormat(style), b.getDateTimeFormat(style), id)
        for (field in -1..16) for (width in Source.DisplayWidth.entries) assertEquals(a.getFieldDisplayName(field, width), b.getFieldDisplayName(field, DateTimePatternGenerator.DisplayWidth.entries[width.ordinal]), id)
        for (skeleton in listOf("", "yMd", "yyyyMMMMEEEEd", "yMMMdHm", "HmsSSSS", "jjjmm", "Cmm", "JJmm", "GyQQQQd", "Ehmv")) for (option in listOf(0, 0xffff)) assertEquals(outcome { a.getBestPattern(skeleton, option) }, outcome { b.getBestPattern(skeleton, option) }, "$id/$skeleton/$option")
    }
    @Test fun publicFactoriesAndCalendarsMatchSource() {
        for (id in listOf("en_US", "fr", "ar", "ru", "he@calendar=hebrew", "zh@calendar=chinese", "ja@calendar=japanese", "en_GB@hours=h24", "en_US@rg=gbzzzz", "fa@numbers=latn")) {
            compare(Source.getInstance(SourceLocale(id)), DateTimePatternGenerator.getInstance(ULocale(id)), id)
            compare(Source.getInstanceNoStdPat(SourceLocale(id)), DateTimePatternGenerator.getInstanceNoStdPat(ULocale(id)), "no-standard/$id")
        }
        compare(Source.getInstance(java.util.Locale.CANADA_FRENCH), DateTimePatternGenerator.getInstance(java.util.Locale.CANADA_FRENCH), "jvm-locale")
        assertEquals(outcome { Source.getInstance(null as SourceLocale?) }, outcome { DateTimePatternGenerator.getInstance(null as ULocale?) })
    }
    @Test fun cachedMutationReceiverThawAndSkeletonFactoriesMatchSource() {
        val id = "en_US@icu4kcase=oraclecache"
        val a = Source.getFrozenInstance(SourceLocale(id)); val b = DateTimePatternGenerator.getFrozenInstance(ULocale(id))
        assertTrue(a.isFrozen); assertTrue(b.isFrozen())
        assertSame(a, Source.getFrozenInstance(SourceLocale(id))); assertSame(b, DateTimePatternGenerator.getFrozenInstance(ULocale(id)))
        compare(Source.getInstance(SourceLocale(id)), DateTimePatternGenerator.getInstance(ULocale(id)), id)
        assertFalse(a.isFrozen); assertFalse(b.isFrozen())
        a.addPattern("yyyy 'CACHE' MMMM dd", true, Source.PatternInfo()); b.addPattern("yyyy 'CACHE' MMMM dd", true, DateTimePatternGenerator.PatternInfo())
        val af = com.ibm.icu.text.DateFormat.getInstanceForSkeleton("yyyyMMMMdd", SourceLocale(id)) as com.ibm.icu.text.SimpleDateFormat
        val bf = DateFormat.getInstanceForSkeleton("yyyyMMMMdd", ULocale(id)) as SimpleDateFormat
        assertEquals(af.toPattern(), bf.toPattern()); assertTrue(bf.toPattern()!!.contains("CACHE"))
        a.freeze(); b.freeze(); val ac = a.clone(); val bc = b.clone()
        assertTrue(a.isFrozen); assertTrue(b.isFrozen()); compare(ac, bc, "clone")
        ac.setAppendItemName(1, "shared"); bc.setAppendItemName(1, "shared")
        assertEquals(a.getAppendItemName(1), b.getAppendItemName(1)); assertEquals("shared", b.getAppendItemName(1))
        assertEquals(outcome { a.setDateTimeFormat(4, null) }, outcome { b.setDateTimeFormat(4, null) })
        assertEquals(outcome { a.addPattern(null, false, null) }, outcome { b.addPattern(null, false, null) })
    }
    @Test fun publicMutatorsInfoCollectionsAndStaticHelpersMatch() {
        val a = Source.getEmptyInstance(); val b = DateTimePatternGenerator.getEmptyInstance()
        val ai = Source.PatternInfo(); val bi = DateTimePatternGenerator.PatternInfo()
        assertEquals(ai.status, bi.status); assertEquals(ai.conflictingPattern, bi.conflictingPattern)
        for (pattern in listOf("yyyy-MM-dd", "d MMMM y", "yy/MM/dd", "HH:mm:ss", "H:m:s")) {
            assertSame(a, a.addPattern(pattern, true, ai)); assertSame(b, b.addPattern(pattern, true, bi))
            assertEquals(ai.status, bi.status); assertEquals(ai.conflictingPattern, bi.conflictingPattern)
        }
        compare(a, b, "custom")
        assertEquals(a.getRedundants(linkedSetOf()).toList(), b.getRedundants(linkedSetOf()).toList())
        for (pattern in listOf("dd MMM y", "HH:mm:ss", "'year' yyyy 'час' HH", "{x}", "")) {
            assertEquals(outcome { a.getFields(pattern) }, outcome { b.getFields(pattern) })
            assertEquals(outcome { a.getSkeleton(pattern) }, outcome { b.getSkeleton(pattern) })
            assertEquals(outcome { a.getCanonicalSkeletonAllowingDuplicates(pattern) }, outcome { b.getCanonicalSkeletonAllowingDuplicates(pattern) })
            assertEquals(outcome { a.replaceFieldTypes(pattern, "yyyyMMMMddHHmm", 0xffff) }, outcome { b.replaceFieldTypes(pattern, "yyyyMMMMddHHmm", 0xffff) })
        }
        for (key in listOf<String?>(null, "", "Year", "Week", "*", "Timezone", "bad")) {
            assertEquals(Source.getAppendFormatNumber(key), DateTimePatternGenerator.getAppendFormatNumber(key))
            if (key != null) assertEquals(Source.getAppendFormatNumber(com.ibm.icu.impl.UResource.Key().setString(key)), DateTimePatternGenerator.getAppendFormatNumber(UResource.Key().setString(key)))
            assertEquals(outcome { Source.isSingleField(key) }, outcome { DateTimePatternGenerator.isSingleField(key) })
        }
    }
    @Test fun subclassVirtualCallsCloningAndNonSerializationMatchSource() {
        val aCalls = mutableListOf<String>(); val bCalls = mutableListOf<String>()
        val a = object : Source() {
            var extra = 42
            override fun getDefaultHourFormatChar(): Char { aCalls.add("hour"); return 'K' }
            override fun getDateTimeFormat(style: Int): String { aCalls.add("glue:$style"); return "{1}/{0}" }
            override fun setDateTimeFormat(style: Int, value: String?) { aCalls.add("set:$style:$value"); super.setDateTimeFormat(style, value) }
            override fun getFieldDisplayName(field: Int, width: Source.DisplayWidth?): String { aCalls.add("name:$field:$width"); return "name" }
            override fun replaceFieldTypes(pattern: String?, skeleton: String?, options: Int): String { aCalls.add("replace:$options"); return "virtual" }
            override fun getBestPattern(skeleton: String?, options: Int): String { aCalls.add("best:$options"); return "override" }
        }
        val b = object : DateTimePatternGenerator() {
            var extra = 42
            override fun getDefaultHourFormatChar(): Char { bCalls.add("hour"); return 'K' }
            override fun getDateTimeFormat(style: Int): String { bCalls.add("glue:$style"); return "{1}/{0}" }
            override fun setDateTimeFormat(style: Int, value: String?) { bCalls.add("set:$style:$value"); super.setDateTimeFormat(style, value) }
            override fun getFieldDisplayName(field: Int, width: DisplayWidth?): String { bCalls.add("name:$field:$width"); return "name" }
            override fun replaceFieldTypes(pattern: String?, skeleton: String?, options: Int): String { bCalls.add("replace:$options"); return "virtual" }
            override fun getBestPattern(skeleton: String?, options: Int): String { bCalls.add("best:$options"); return "override" }
        }
        assertEquals(a.defaultHourCycle.name, b.getDefaultHourCycle().name)
        assertEquals(a.dateTimeFormat, b.getDateTimeFormat())
        a.setDateTimeFormat("glue"); b.setDateTimeFormat("glue")
        assertEquals(a.getAppendItemName(1), b.getAppendItemName(1))
        assertEquals(a.replaceFieldTypes("y", "yy"), b.replaceFieldTypes("y", "yy"))
        assertEquals(a.getBestPattern(""), b.getBestPattern("")) // Private path bypasses the two-argument override.
        a.addPattern("MMM d, y", true, Source.PatternInfo()); b.addPattern("MMM d, y", true, DateTimePatternGenerator.PatternInfo())
        a.addPattern("HH:mm", true, Source.PatternInfo()); b.addPattern("HH:mm", true, DateTimePatternGenerator.PatternInfo())
        assertEquals(a.getBestPattern("yMMMdHm"), b.getBestPattern("yMMMdHm"))
        assertEquals(aCalls, bCalls)
        val clone = b.clone(); assertEquals<Class<*>>(b.javaClass, clone.javaClass)
        assertEquals(42, clone.javaClass.getDeclaredField("extra").also { it.isAccessible = true }.getInt(clone))
        assertTrue(b is Cloneable); assertFalse((b as Any) is Serializable)
        assertEquals(outcome { ObjectOutputStream(ByteArrayOutputStream()).use { it.writeObject(Source.getEmptyInstance()) } }, outcome { ObjectOutputStream(ByteArrayOutputStream()).use { it.writeObject(DateTimePatternGenerator.getEmptyInstance()) } })
    }
    @Test fun addPatternAndFrozenChecksDispatchThroughPublicOverrides() {
        val callsA = mutableListOf<String>(); val callsB = mutableListOf<String>()
        val returnedA = Source.getEmptyInstance(); val returnedB = DateTimePatternGenerator.getEmptyInstance()
        val a = object : Source() {
            override fun addPatternWithSkeleton(pattern: String?, skeleton: String?, override: Boolean, info: Source.PatternInfo?): Source {
                callsA.add("add:$pattern:$skeleton:$override"); return returnedA
            }
            override fun isFrozen(): Boolean { callsA.add("frozen"); return false }
        }
        val b = object : DateTimePatternGenerator() {
            override fun addPatternWithSkeleton(pattern: String?, skeleton: String?, override: Boolean, info: PatternInfo?): DateTimePatternGenerator {
                callsB.add("add:$pattern:$skeleton:$override"); return returnedB
            }
            override fun isFrozen(): Boolean { callsB.add("frozen"); return false }
        }
        assertSame(returnedA, a.addPattern("y", true, null)); assertSame(returnedB, b.addPattern("y", true, null))
        a.freeze(); b.freeze()
        a.setDecimal("."); b.setDecimal(".")
        a.setDateTimeFormat("{1}/{0}"); b.setDateTimeFormat("{1}/{0}")
        a.setAppendItemName(-1, "ignored"); b.setAppendItemName(-1, "ignored")
        assertEquals(callsA, callsB)
        assertEquals(a.decimal, b.getDecimal()); assertEquals(a.getDateTimeFormat(0), b.getDateTimeFormat(0))
    }
    private class SerializableSource : Source(), Serializable { var extra = 7 }
    private class SerializablePort : DateTimePatternGenerator(), Serializable { var extra = 7 }
    @Test fun serializableSubclassesReinitializeTheNonSerializableGeneratorBase() {
        fun roundTrip(value: Any): Any {
            val data = ByteArrayOutputStream().also { stream -> ObjectOutputStream(stream).use { it.writeObject(value) } }.toByteArray()
            return ObjectInputStream(ByteArrayInputStream(data)).use { it.readObject() }
        }
        val a = SerializableSource(); val b = SerializablePort()
        a.extra = 42; b.extra = 42
        a.addPattern("yyyy-MM-dd", true, Source.PatternInfo()); b.addPattern("yyyy-MM-dd", true, DateTimePatternGenerator.PatternInfo())
        a.freeze(); b.freeze()
        val ar = roundTrip(a) as SerializableSource; val br = roundTrip(b) as SerializablePort
        assertEquals(ar.extra, br.extra); assertEquals(42, br.extra)
        assertFalse(ar.isFrozen); assertFalse(br.isFrozen())
        assertEquals(ar.getSkeletons(null), br.getSkeletons(null)); assertTrue(br.getSkeletons(null).isEmpty())
        assertEquals(ar.decimal, br.getDecimal()); assertEquals(ar.defaultHourFormatChar, br.getDefaultHourFormatChar())
        for (style in 0..3) assertEquals(ar.getDateTimeFormat(style), br.getDateTimeFormat(style))
    }
    @Test fun clonedGeneratorUsesItsOwnMonitorForGetters() {
        val a = Source.getEmptyInstance(); val b = DateTimePatternGenerator.getEmptyInstance()
        val ac = a.clone(); val bc = b.clone()
        for ((owner, getter) in listOf<Pair<Any, () -> String>>(a to { ac.getSkeleton("yyyy-MM-dd") }, b to { bc.getSkeleton("yyyy-MM-dd") })) {
            val done = java.util.concurrent.CountDownLatch(1)
            val error = java.util.concurrent.atomic.AtomicReference<Throwable?>()
            lateinit var worker: Thread
            val independent = synchronized(owner) {
                worker = Thread {
                    try { getter() } catch (failure: Throwable) { error.set(failure) } finally { done.countDown() }
                }.apply { isDaemon = true; start() }
                done.await(5, java.util.concurrent.TimeUnit.SECONDS)
            }
            worker.join(5000)
            error.get()?.let { throw it }
            assertTrue(independent, "Clone getter must use the clone's own monitor: ${owner.javaClass.name}")
        }
    }

}
