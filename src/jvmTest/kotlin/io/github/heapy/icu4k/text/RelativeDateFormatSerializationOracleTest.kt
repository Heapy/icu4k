@file:Suppress("UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.impl.RelativeDateFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.Calendar as SourceCalendar
import io.github.heapy.icu4k.impl.RelativeDateFormat
import io.github.heapy.icu4k.util.*
import java.io.*
import kotlin.test.*

class RelativeDateFormatSerializationOracleTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(value) } }.toByteArray()
    private fun read(data: ByteArray, from: String? = null, to: String? = null): Any = object : ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (from != null && desc.name.contains(from)) try { return Class.forName(desc.name.replace(from, to!!)) } catch (_: ClassNotFoundException) { }
            return super.resolveClass(desc)
        }
    }.use { it.readObject() }
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(owner)
    private fun compare(a: Source, b: RelativeDateFormat) {
        for (name in listOf("fDatePattern", "fTimePattern", "fDateStyle", "fTimeStyle", "combinedFormatHasDateAtStart", "capitalizationInfoIsSet", "capitalizationOfRelativeUnitsForListOrMenu", "capitalizationOfRelativeUnitsForStandAlone")) assertEquals(field(a, name), field(b, name), name)
        for (delta in listOf(-10, -1, 0, 1, 10)) {
            val now = System.currentTimeMillis()
            val ac = SourceCalendar.getInstance(SourceLocale.US); val bc = Calendar.getInstance(ULocale.US)
            ac.timeInMillis = now; bc.setTimeInMillis(now)
            ac.add(SourceCalendar.DATE, delta); bc.add(Calendar.DATE, delta)
            assertEquals(a.format(ac, StringBuffer(), java.text.FieldPosition(0)).toString(), b.format(bc, StringBuilder(), FormatFieldPosition(0)).toString())
        }
    }
    @Test fun exactDescriptorBidirectionalStreamsAndLazyRelativeData() {
        val a = ObjectStreamClass.lookup(Source::class.java); val b = ObjectStreamClass.lookup(RelativeDateFormat::class.java)
        assertEquals(a.serialVersionUID, b.serialVersionUID)
        assertEquals(a.fields.map { Triple(it.name, it.typeCode, it.typeString?.replace("com/ibm/icu/", "io/github/heapy/icu4k/")) }, b.fields.map { Triple(it.name, it.typeCode, it.typeString) })
        for (id in listOf("en_US", "fr", "ru", "zh@calendar=chinese")) for ((date, time) in listOf(131 to -1, 128 to 3, 3 to 131, -1 to 131)) {
            val source = Source(time, date, SourceLocale(id), null); val port = RelativeDateFormat(time, date, ULocale(id), null)
            source.setContext(com.ibm.icu.text.DisplayContext.CAPITALIZATION_FOR_STANDALONE); port.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
            val ar = read(bytes(source)) as Source; val br = read(bytes(port)) as RelativeDateFormat
            assertNull(field(br, "fDates")); assertNull(field(br, "capitalizationBrkIter"))
            compare(ar, br)
            compare(read(bytes(port), "io.github.heapy.icu4k.", "com.ibm.icu.") as Source, read(bytes(source), "com.ibm.icu.", "io.github.heapy.icu4k.") as RelativeDateFormat)
        }
    }
    @Test fun clonedLazyLookupDoesNotAcquireOriginalMonitor() {
        val a = read(bytes(Source(-1, 131, SourceLocale.US, null))) as Source
        val b = read(bytes(RelativeDateFormat(-1, 131, ULocale.US, null))) as RelativeDateFormat
        val ac = a.clone() as Source; val bc = b.clone() as RelativeDateFormat
        for ((owner, format) in listOf<Pair<Any, () -> String>>(a to { ac.format(java.util.Date()) }, b to { bc.format(Date()) })) {
            val done = java.util.concurrent.CountDownLatch(1)
            val error = java.util.concurrent.atomic.AtomicReference<Throwable?>()
            lateinit var worker: Thread
            val independent = synchronized(owner) {
                worker = Thread {
                    try { format() } catch (failure: Throwable) { error.set(failure) } finally { done.countDown() }
                }.apply { isDaemon = true; start() }
                done.await(5, java.util.concurrent.TimeUnit.SECONDS)
            }
            worker.join(5000)
            error.get()?.let { throw it }
            assertTrue(independent, "A clone's lazy relative lookup must use its own monitor: ${owner.javaClass.name}")
        }
    }
    @Test fun legacyDateOnlyStreamUsesTheRetainedFormatter() {
        val a = Source(3, 131, SourceLocale.FRANCE, null); val b = RelativeDateFormat(3, 131, ULocale.FRANCE, null)
        fun set(owner: Any, name: String, value: Any?) { owner.javaClass.getDeclaredField(name).also { it.isAccessible = true }.set(owner, value) }
        for (owner in listOf(a, b)) for (name in listOf("fDateTimeFormat", "fDatePattern", "fTimePattern")) set(owner, name, null)
        set(a, "fDateFormat", com.ibm.icu.text.DateFormat.getDateInstance(3, SourceLocale.FRANCE))
        set(b, "fDateFormat", DateFormat.getDateInstance(3, ULocale.FRANCE))
        compare(read(bytes(a)) as Source, read(bytes(b)) as RelativeDateFormat)
        compare(read(bytes(b), "io.github.heapy.icu4k.", "com.ibm.icu.") as Source, read(bytes(a), "com.ibm.icu.", "io.github.heapy.icu4k.") as RelativeDateFormat)
    }
    @Test fun clonesAndSerializedGraphsShareHeldFormatterButNotBaseCalendar() {
        val a = Source(3, 131, SourceLocale.US, null); val b = RelativeDateFormat(3, 131, ULocale.US, null)
        val ac = a.clone() as Source; val bc = b.clone() as RelativeDateFormat
        assertSame(field(b, "fDateTimeFormat"), field(bc, "fDateTimeFormat"))
        assertSame(field(b, "fCombinedFormat"), field(bc, "fCombinedFormat"))
        assertSame(field(b, "fDates"), field(bc, "fDates"))
        assertNotSame(b.getCalendar(), bc.getCalendar())
        val aa = read(bytes(arrayOf(a, ac))) as Array<Source>; val bb = read(bytes(arrayOf(b, bc))) as Array<RelativeDateFormat>
        assertSame(field(bb[0], "fDateTimeFormat"), field(bb[1], "fDateTimeFormat"))
        assertSame(field(bb[0], "fCombinedFormat"), field(bb[1], "fCombinedFormat"))
        compare(aa[0], bb[0]); compare(aa[1], bb[1])
    }
}
