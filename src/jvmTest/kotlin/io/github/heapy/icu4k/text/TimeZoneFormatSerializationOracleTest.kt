@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.TimeZoneFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import java.util.EnumSet
import kotlin.test.*

class TimeZoneFormatSerializationOracleTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { b -> ObjectOutputStream(b).use { it.writeObject(value) } }.toByteArray()
    private fun read(data: ByteArray, from: String? = null, to: String? = null): Any = object : ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (from != null && desc.name.contains(from)) {
                try { return Class.forName(desc.name.replace(from, to!!)) } catch (_: ClassNotFoundException) { }
            }
            return super.resolveClass(desc)
        }
    }.use { it.readObject() }
    private fun sourceField(a: Source, name: String): Any? = Source::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(a)
    private fun set(a: Source, name: String, value: Any?) { Source::class.java.getDeclaredField(name).also { it.isAccessible = true }.set(a, value) }
    private fun compare(a: Source, b: TimeZoneFormat) {
        assertEquals(a.isFrozen, b.isFrozen())
        assertEquals(a.gmtPattern, b.getGMTPattern()); assertEquals(a.gmtOffsetDigits, b.getGMTOffsetDigits()); assertEquals(a.gmtZeroFormat, b.getGMTZeroFormat())
        assertEquals(a.defaultParseOptions.map { it.name }, b.getDefaultParseOptions()!!.map { it.name })
        for (t in Source.GMTOffsetPatternType.entries) assertEquals(a.getGMTOffsetPattern(t), b.getGMTOffsetPattern(TimeZoneFormat.GMTOffsetPatternType.valueOf(t.name)))
        for (offset in listOf(-19845000, 0, 19845000)) assertEquals(a.formatOffsetLocalizedGMT(offset), b.formatOffsetLocalizedGMT(offset))
        for (s in Source.Style.entries) assertEquals(a.format(s, com.ibm.icu.util.TimeZone.getTimeZone("Europe/London"), 0), b.format(TimeZoneFormat.Style.valueOf(s.name), io.github.heapy.icu4k.util.TimeZone.getTimeZone("Europe/London"), 0))
    }
    @Test fun descriptorsRoundTripsAndCrossRead() {
        val a = ObjectStreamClass.lookup(Source::class.java); val b = ObjectStreamClass.lookup(TimeZoneFormat::class.java)
        assertEquals(a.serialVersionUID, b.serialVersionUID)
        assertEquals(a.fields.map { it.name to it.typeString?.replace("com/ibm/icu/", "io/github/heapy/icu4k/") }, b.fields.map { it.name to it.typeString })
        for (id in listOf("fr_CA_TZFSERIAL", "en_IL@numbers=mathbold")) {
            val source = Source.getInstance(SourceLocale(id)).cloneAsThawed(); val port = TimeZoneFormat.getInstance(ULocale(id)).cloneAsThawed()
            source.setGMTPattern("Offset({0})"); port.setGMTPattern("Offset({0})")
            source.setDefaultParseOptions(EnumSet.allOf(Source.ParseOption::class.java)); port.setDefaultParseOptions(TimeZoneFormat.ParseOption.entries.toSet())
            source.freeze(); port.freeze()
            val ar = read(bytes(source)) as Source; val br = read(bytes(port)) as TimeZoneFormat
            compare(ar, br)
            assertFalse(br.isFrozen())
            assertEquals(setOf(TimeZoneFormat.ParseOption.ALL_STYLES), br.getDefaultParseOptions()!!.toSet())
            assertSame(TimeZoneNames.getInstance(ULocale(id)), br.getTimeZoneNames())
            compare(ar, read(bytes(source), "com.ibm.icu.", "io.github.heapy.icu4k.") as TimeZoneFormat)
            compare(read(bytes(port), "io.github.heapy.icu4k.", "com.ibm.icu.") as Source, br)
        }
    }
    private fun failure(block: () -> Any?): Any? = try { block(); "ok" } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        if (e is NullPointerException) "null" else e.javaClass.simpleName + ":" + e.message
    }
    @Test fun legacyAndMalformedStreamsFollowSourceValidationOrder() {
        val original = Source.getInstance(SourceLocale("en_US_TZFLEGACY")).cloneAsThawed()
        val offsets = sourceField(original, "_gmtOffsetPatterns") as Array<String>
        val cases = listOf(
            "_locale" to null, "_tznames" to null, "_gmtPattern" to null, "_gmtPattern" to "bad",
            "_gmtOffsetPatterns" to null, "_gmtOffsetPatterns" to emptyArray<String>(),
            "_gmtOffsetPatterns" to offsets.copyOfRange(0, 3), "_gmtOffsetPatterns" to offsets.copyOfRange(0, 4),
            "_gmtOffsetPatterns" to offsets.copyOfRange(0, 5), "_gmtOffsetPatterns" to (offsets + "ignored"),
            "_gmtOffsetDigits" to null, "_gmtOffsetDigits" to arrayOf("1"),
            "_gmtZeroFormat" to null, "_gmtZeroFormat" to ""
        )
        for ((name, value) in cases) {
            val a = original.cloneAsThawed(); set(a, name, value)
            val data = bytes(a)
            val left = failure { read(data) }; val right = failure { read(data, "com.ibm.icu.", "io.github.heapy.icu4k.") }
            assertEquals(left, right, "$name/${value?.javaClass}")
            if (left == "ok") compare(read(data) as Source, read(data, "com.ibm.icu.", "io.github.heapy.icu4k.") as TimeZoneFormat)
        }
        val data = bytes(original)
        val needle = "_parseAllStyles".toByteArray(); val replacement = "_parseAllStyleX".toByteArray()
        val at = data.indices.first { start -> start + needle.size <= data.size && needle.indices.all { data[start + it] == needle[it] } }
        replacement.copyInto(data, at)
        assertEquals("InvalidObjectException:Missing field: parseAllStyles", failure { read(data) })
        assertEquals(failure { read(data) }, failure { read(data, "com.ibm.icu.", "io.github.heapy.icu4k.") })
    }
    @Test fun cloneGraphsRestoreSourceArrayAliases() {
        val a = Source.getInstance(SourceLocale("en_US_TZFGRAPH")).cloneAsThawed(); val b = TimeZoneFormat.getInstance(ULocale("en_US_TZFGRAPH")).cloneAsThawed()
        val ar = read(bytes(arrayOf(a, a.clone()))) as Array<Source>
        val br = read(bytes(arrayOf(b, b.clone()))) as Array<TimeZoneFormat>
        assertEquals(sourceField(ar[0], "_gmtOffsetPatterns") === sourceField(ar[1], "_gmtOffsetPatterns"), br[0].localized!!.serializationPatterns() === br[1].localized!!.serializationPatterns())
        assertEquals(sourceField(ar[0], "_gmtOffsetDigits") === sourceField(ar[1], "_gmtOffsetDigits"), br[0].localized!!.serializationDigits() === br[1].localized!!.serializationDigits())
        assertFalse(br[0].localized!!.serializationPatterns() === br[1].localized!!.serializationPatterns())
        assertTrue(br[0].localized!!.serializationDigits() === br[1].localized!!.serializationDigits())
        assertSame(br[0].getTimeZoneNames(), br[1].getTimeZoneNames())
    }
}
