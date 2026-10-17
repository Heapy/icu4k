@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.TimeZoneFormat as Source
import com.ibm.icu.text.TimeZoneNames as SourceNames
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import java.io.*
import java.util.EnumSet
import kotlin.test.*

class TimeZoneFormatVirtualOracleTest {
    private class A : Source(SourceLocale.US) {
        val calls = mutableListOf<String>()
        var thawed = false
        var nullFormat = false
        override fun isFrozen(): Boolean = !thawed && super.isFrozen()
        override fun getTimeZoneNames(): SourceNames? { calls.add("names"); return null }
        override fun formatOffsetLocalizedGMT(offset: Int): String? { calls.add("long:$offset"); return if (nullFormat) null else super.formatOffsetLocalizedGMT(offset) }
        override fun formatOffsetShortLocalizedGMT(offset: Int): String? { calls.add("short:$offset"); return super.formatOffsetShortLocalizedGMT(offset) }
        override fun parseOffsetLocalizedGMT(text: String?, pos: java.text.ParsePosition?): Int { calls.add("parseLong:$text"); return super.parseOffsetLocalizedGMT(text, pos) }
        override fun parseOffsetShortLocalizedGMT(text: String?, pos: java.text.ParsePosition?): Int { calls.add("parseShort:$text"); return super.parseOffsetShortLocalizedGMT(text, pos) }
        override fun getDefaultParseOptions(): EnumSet<Source.ParseOption> { calls.add("options"); return super.getDefaultParseOptions() }
    }
    private class B : TimeZoneFormat(ULocale.US) {
        val calls = mutableListOf<String>()
        var thawed = false
        var nullFormat = false
        override fun isFrozen(): Boolean = !thawed && super.isFrozen()
        override fun getTimeZoneNames(): TimeZoneNames? { calls.add("names"); return null }
        override fun formatOffsetLocalizedGMT(offset: Int): String? { calls.add("long:$offset"); return if (nullFormat) null else super.formatOffsetLocalizedGMT(offset) }
        override fun formatOffsetShortLocalizedGMT(offset: Int): String? { calls.add("short:$offset"); return super.formatOffsetShortLocalizedGMT(offset) }
        override fun parseOffsetLocalizedGMT(text: String?, pos: ParsePosition?): Int { calls.add("parseLong:$text"); return super.parseOffsetLocalizedGMT(text, pos) }
        override fun parseOffsetShortLocalizedGMT(text: String?, pos: ParsePosition?): Int { calls.add("parseShort:$text"); return super.parseOffsetShortLocalizedGMT(text, pos) }
        override fun getDefaultParseOptions(): MutableSet<ParseOption>? { calls.add("options"); return super.getDefaultParseOptions() }
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        if (e is NullPointerException) "null-error" else e.javaClass.simpleName + ":" + e.message
    }
    @Test fun virtualCallbacksFrozenChecksAndPrivateNamesState() {
        val a = A(); val b = B()
        for (style in TimeZoneFormat.Style.entries) {
            a.calls.clear(); b.calls.clear()
            assertEquals(outcome { a.format(Source.Style.valueOf(style.name), com.ibm.icu.util.TimeZone.getTimeZone("America/New_York"), 0) }, outcome { b.format(style, TimeZone.getTimeZone("America/New_York"), 0) })
            assertEquals(a.calls, b.calls, "$style")
            for (text in listOf("GMT+05:30", "GMT", "bad")) {
                a.calls.clear(); b.calls.clear()
                val ap = java.text.ParsePosition(0); val bp = ParsePosition(0)
                assertEquals(a.parse(Source.Style.valueOf(style.name), text, ap, null)?.id, b.parse(style, text, bp, null)?.getID())
                assertEquals(a.calls, b.calls, "$style/$text")
            }
        }
        a.freeze(); b.freeze(); a.thawed = true; b.thawed = true
        a.setGMTPattern("custom {0}"); b.setGMTPattern("custom {0}")
        assertEquals(a.gmtPattern, b.getGMTPattern())
        a.nullFormat = true; b.nullFormat = true
        val az = com.ibm.icu.util.SimpleTimeZone(0, "custom"); val bz = SimpleTimeZone(0, "custom")
        for (field in listOf(0, com.ibm.icu.text.DateFormat.TIMEZONE_FIELD)) {
            val ab = StringBuffer(); val bb = StringBuilder()
            assertEquals(outcome { a.format(az, ab, java.text.FieldPosition(field)); ab.toString() }, outcome { b.format(bz, bb, FormatFieldPosition(field)); bb.toString() })
            assertEquals(ab.toString(), bb.toString())
        }
    }
    private open class ANames : SourceNames() {
        override fun getAvailableMetaZoneIDs(): Set<String> = emptySet()
        override fun getAvailableMetaZoneIDs(id: String?): Set<String> = emptySet()
        override fun getMetaZoneID(id: String?, date: Long): String? = null
        override fun getReferenceZoneID(id: String?, region: String?): String? = null
        override fun getMetaZoneDisplayName(id: String?, type: SourceNames.NameType?): String? = null
        override fun getTimeZoneDisplayName(id: String?, type: SourceNames.NameType?): String? = if (type == SourceNames.NameType.LONG_STANDARD) "custom standard" else null
        override fun find(text: CharSequence?, start: Int, types: EnumSet<SourceNames.NameType>?): Collection<MatchInfo>? = null
        companion object { private const val serialVersionUID = 1L }
    }
    private open class BNames : TimeZoneNames() {
        override fun getAvailableMetaZoneIDs(): Set<String> = emptySet()
        override fun getAvailableMetaZoneIDs(id: String?): Set<String> = emptySet()
        override fun getMetaZoneID(id: String?, date: Long): String? = null
        override fun getReferenceZoneID(id: String?, region: String?): String? = null
        override fun getMetaZoneDisplayName(id: String?, type: NameType?): String? = null
        override fun getTimeZoneDisplayName(id: String?, type: NameType?): String? = if (type == NameType.LONG_STANDARD) "custom standard" else null
        override fun find(text: CharSequence?, start: Int, types: Set<NameType>?): Collection<MatchInfo>? = null
        companion object { private const val serialVersionUID = 1L }
    }
    private fun roundtrip(value: Any): Any {
        val bytes = ByteArrayOutputStream().also { b -> ObjectOutputStream(b).use { it.writeObject(value) } }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
    }
    @Test fun customNamesNullMatchCollectionsAndSerialization() {
        val a = Source.getInstance(SourceLocale.US).cloneAsThawed(); val b = TimeZoneFormat.getInstance(ULocale.US).cloneAsThawed()
        a.setTimeZoneNames(ANames()); b.setTimeZoneNames(BNames())
        for ((source, port) in listOf(a to b, (roundtrip(a) as Source) to (roundtrip(b) as TimeZoneFormat))) {
            assertTrue(port.getTimeZoneNames() is BNames)
            for (style in TimeZoneFormat.Style.entries) {
                assertEquals(source.format(Source.Style.valueOf(style.name), com.ibm.icu.util.TimeZone.getTimeZone("America/New_York"), 0), port.format(style, TimeZone.getTimeZone("America/New_York"), 0))
                val ap = java.text.ParsePosition(0); val bp = ParsePosition(0)
                assertEquals(source.parse(Source.Style.valueOf(style.name), "bad", ap, EnumSet.of(Source.ParseOption.ALL_STYLES), null)?.id, port.parse(style, "bad", bp, setOf(TimeZoneFormat.ParseOption.ALL_STYLES), null)?.getID())
                assertEquals(listOf(ap.index, ap.errorIndex), listOf(bp.index, bp.errorIndex))
            }
        }
    }

    @Test fun matchCollectionsKeepIteratorAndVirtualGetterOrder() {
        val sourceCalls = mutableListOf<String>(); val portCalls = mutableListOf<String>()
        val an = object : ANames() {
            override fun find(text: CharSequence?, start: Int, types: EnumSet<SourceNames.NameType>?): Collection<MatchInfo> = object : AbstractCollection<MatchInfo>() {
                override val size: Int get() { sourceCalls.add("size"); return 2 }
                override fun iterator(): Iterator<MatchInfo> {
                    sourceCalls.add("iterator")
                    return object : Iterator<MatchInfo> {
                        var index = 0
                        override fun hasNext(): Boolean { sourceCalls.add("hasNext:$index"); return index < 2 }
                        override fun next(): MatchInfo {
                            val n = ++index; sourceCalls.add("next:$n")
                            return object : MatchInfo(SourceNames.NameType.LONG_STANDARD, "America/New_York", null, n) {
                                override fun matchLength(): Int { sourceCalls.add("length:$n"); return super.matchLength() }
                                override fun tzID(): String? { sourceCalls.add("zone:$n"); return super.tzID() }
                                override fun mzID(): String? { sourceCalls.add("meta:$n"); return super.mzID() }
                                override fun nameType(): SourceNames.NameType { sourceCalls.add("type:$n"); return super.nameType() }
                            }
                        }
                    }
                }
            }
        }
        val bn = object : BNames() {
            override fun find(text: CharSequence?, start: Int, types: Set<NameType>?): Collection<MatchInfo> = object : AbstractCollection<MatchInfo>() {
                override val size: Int get() { portCalls.add("size"); return 2 }
                override fun iterator(): Iterator<MatchInfo> {
                    portCalls.add("iterator")
                    return object : Iterator<MatchInfo> {
                        var index = 0
                        override fun hasNext(): Boolean { portCalls.add("hasNext:$index"); return index < 2 }
                        override fun next(): MatchInfo {
                            val n = ++index; portCalls.add("next:$n")
                            return object : MatchInfo(NameType.LONG_STANDARD, "America/New_York", null, n) {
                                override fun matchLength(): Int { portCalls.add("length:$n"); return super.matchLength() }
                                override fun tzID(): String? { portCalls.add("zone:$n"); return super.tzID() }
                                override fun mzID(): String? { portCalls.add("meta:$n"); return super.mzID() }
                                override fun nameType(): NameType { portCalls.add("type:$n"); return super.nameType() }
                            }
                        }
                    }
                }
            }
        }
        val a = Source.getInstance(SourceLocale.US).cloneAsThawed(); val b = TimeZoneFormat.getInstance(ULocale.US).cloneAsThawed()
        a.setTimeZoneNames(an); b.setTimeZoneNames(bn)
        for (style in listOf(TimeZoneFormat.Style.SPECIFIC_LONG, TimeZoneFormat.Style.EXEMPLAR_LOCATION, TimeZoneFormat.Style.GENERIC_LONG)) {
            sourceCalls.clear(); portCalls.clear()
            val ap = java.text.ParsePosition(0); val bp = ParsePosition(0)
            assertEquals(a.parse(Source.Style.valueOf(style.name), "abc", ap, EnumSet.noneOf(Source.ParseOption::class.java), null)?.id, b.parse(style, "abc", bp, emptySet(), null)?.getID())
            assertEquals(sourceCalls, portCalls)
        }
    }
}
