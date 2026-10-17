@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import com.ibm.icu.text.DateIntervalFormat as Source
import com.ibm.icu.text.DateIntervalInfo as SourceInfo
import com.ibm.icu.text.DateTimePatternGenerator as SourceGenerator
import com.ibm.icu.text.SimpleDateFormat as SourceFormat
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.random.Random
import kotlin.test.*

class DateIntervalPatternCoreOracleTest {
    private class State(override val info: DateIntervalInfo, override val fullPattern: String?, override val locale: ULocale?, override var skeleton: String?) : DateIntervalPatternCore.Owner {
        override var datePattern: String? = null
        override var timePattern: String? = null
        override var dateTimeFormat: String? = null
    }
    private fun sourceState(source: Source, field: String): Any? = Source::class.java.getDeclaredField(field).also { it.isAccessible = true }.get(source)
    private fun snapshot(patterns: Map<*, *>): List<Any?> = patterns.entries.map { (key, value) ->
        val type = value!!.javaClass
        listOf(key, type.getMethod("getFirstPart").invoke(value), type.getMethod("getSecondPart").invoke(value), type.getMethod("firstDateInPtnIsLaterDate").invoke(value))
    }
    private fun compare(id: String, skeleton: String?, custom: Boolean = false) {
        val sl = SourceLocale(id); val locale = ULocale(id)
        val input = skeleton ?: "yMdHm"
        val full = try { SourceGenerator.getInstance(sl).getBestPattern(input) } catch (sourceFailure: Exception) {
            val failure = assertFails("$id/$skeleton") { DateTimePatternGenerator.getInstance(locale).getBestPattern(input) }
            assertEquals<Class<*>>(sourceFailure.javaClass, failure.javaClass, "$id/$skeleton")
            assertEquals(sourceFailure.message, failure.message, "$id/$skeleton")
            return
        }
        val sourceInfo = if (custom) SourceInfo() else SourceInfo(sl)
        val info = if (custom) DateIntervalInfo() else DateIntervalInfo(locale)
        if (custom) for ((key, field, pattern) in listOf(
            Triple("yMd", Calendar.DATE, "latestFirst:yyyy LLL d 'to' d"),
            Triple("yMd", Calendar.MONTH, "y/M/d ~ M/d"),
            Triple("hm", Calendar.HOUR, "h 'a' a - h a"),
            Triple("Hm", Calendar.MINUTE, "HH:mm – HH:mm"),
            Triple("ahm", Calendar.HOUR, "h a / h a"))) {
            sourceInfo.setIntervalPattern(key, field, pattern); info.setIntervalPattern(key, field, pattern)
        }
        // The deprecated constructor always takes the uncached initialization path.
        val source = Source(skeleton, sourceInfo, SourceFormat(full, sl))
        val state = State(info.freeze(), full, locale, skeleton)
        val result = DateIntervalPatternCore(state).initialize()
        assertEquals(snapshot(source.rawPatterns), snapshot(result), "$id/$skeleton/$custom")
        assertEquals(sourceState(source, "fSkeleton"), state.skeleton)
        assertEquals(sourceState(source, "fDatePattern"), state.datePattern)
        assertEquals(sourceState(source, "fTimePattern"), state.timePattern)
        assertEquals(sourceState(source, "fDateTimeFormat"), state.dateTimeFormat)
    }
    @Test fun localeCalendarsHourCyclesWidthsAndFallbackPatternsMatch() {
        val ids = listOf("en_US", "en_GB", "fr", "de", "ja", "zh", "ko", "ar", "fa", "ru", "pl", "fi", "he", "hi", "bn", "th", "my", "am", "root", "en@calendar=hebrew", "zh@calendar=chinese", "ja@calendar=japanese", "th@calendar=buddhist", "ar@calendar=islamic", "en@hours=h23", "en@hours=h24", "en_GB@hours=h12")
        val skeletons = listOf<String?>(null, "", "y", "yM", "yMd", "yMMMd", "yyyyMMMMdd", "GyMMMEd", "LLLLd", "QQQQy", "Yw", "UMLd", "Hm", "hm", "Hms", "hmsSSS", "Hmz", "hmv", "hmb", "hmB", "Jm", "jm", "Cm", "jjjjjm", "BBBBBhhmm", "yMdHm", "yMMMdjm", "MMMMdHms", "EEEEdHm", "GyMMMdJm")
        for (id in ids) for (skeleton in skeletons) compare(id, skeleton)
        for (id in listOf("en_US", "fr", "ja")) for (skeleton in skeletons) compare(id, skeleton, true)
    }
    @Test fun sourceQuoteReplacementAndFieldWidthRulesMatch() {
        val replace = Source::class.java.getDeclaredMethod("findReplaceInPattern", String::class.java, String::class.java, String::class.java).also { it.isAccessible = true }
        val adjust = Source::class.java.getDeclaredMethod("adjustFieldWidth", String::class.java, String::class.java, String::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).also { it.isAccessible = true }
        val patterns = listOf<String?>(null, "", "h a – h a", "a\u00a0h - a\u00a0h", "h\u202fa – h\u202fa", "'h a' h a", "''h 'a' h a", "h 'unfinished a", "LLLL d - d, y", "M/d/yy – M/d/yy", "h b v", "HH:mm v", "a a", "'a''a'a")
        for (pattern in patterns) for ((input, best) in listOf("yyyyMMMMdd" to "yMMMd", "KKKKKbbbb" to "ah", "kkmmz" to "Hmv", "Jm" to "hm", "aaaaahh" to "h", "hhbbbbb" to "bh")) for (difference in -1..2) for (suppress in listOf(false, true)) {
            assertEquals(adjust.invoke(null, input, best, pattern, difference, suppress), DateIntervalPatternCore.adjustFieldWidth(input, best, pattern, difference, suppress), "$input/$best/$pattern/$difference/$suppress")
        }
        for (pattern in patterns.filterNotNull()) for (old in listOf("a", " a", "h", "v")) assertEquals(replace.invoke(null, pattern, old, "!"), DateIntervalPatternCore.findReplaceInPattern(pattern, old, "!"))
    }
    @Test fun randomSkeletonPartitionAndHourNormalizationMatch() {
        val partition = Source::class.java.getDeclaredMethod("getDateTimeSkeleton", String::class.java, StringBuilder::class.java, StringBuilder::class.java, StringBuilder::class.java, StringBuilder::class.java).also { it.isAccessible = true }
        val normalize = Source::class.java.getDeclaredMethod("normalizeHourMetacharacters", String::class.java, SourceLocale::class.java).also { it.isAccessible = true }
        val random = Random(20261017)
        repeat(500) {
            val skeleton = buildString { repeat(random.nextInt(50)) { append("GyMEdHhmsSaBbKkCJjZzvVQqLluYwWDFgecUr X'".random(random)) } }
            val a = Array(4) { StringBuilder("prefix") }; val b = Array(4) { StringBuilder("prefix") }
            partition.invoke(null, skeleton, *a)
            DateIntervalPatternCore.getDateTimeSkeleton(skeleton, b[0], b[1], b[2], b[3])
            assertEquals(a.map { it.toString() }, b.map { it.toString() }, skeleton)
        }
        for (id in listOf("en_US", "en_GB", "ar", "zh", "en@hours=h24")) {
            val sl = SourceLocale(id); val source = Source.getInstance("yMd", sl)
            for (hour in "jJChHkK") for (count in 1..6) for (period in listOf("", "a", "bbb", "BBBBB")) {
                val skeleton = "yMd" + hour.toString().repeat(count) + period + "msz"
                assertEquals(normalize.invoke(source, skeleton, sl), DateIntervalPatternCore.normalizeHourMetacharacters(skeleton, ULocale(id)), "$id/$skeleton")
            }
        }
    }
}
