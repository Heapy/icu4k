@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DateIntervalInfo as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class DateIntervalInfoOracleTest {
    private fun outcome(action: () -> Any?): String = try { "ok:${action()}" } catch (e: Exception) {
        when(e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName+":"+e.message }
    }
    private fun pattern(value: Any?): List<Any?>? = value?.let { v ->
        listOf(v.javaClass.getMethod("getFirstPart").invoke(v), v.javaClass.getMethod("getSecondPart").invoke(v), v.javaClass.getMethod("firstDateInPtnIsLaterDate").invoke(v))
    }
    private fun raw(value: Any): List<Any?> =
        (value.javaClass.getMethod("getRawPatterns").invoke(value) as Map<*, *>).entries.map { (key, fields) -> key to (fields as Map<*, *>).entries.map { it.key to pattern(it.value) } }
    private fun best(value: Source, request: String?): String {
        val result = Source::class.java.getDeclaredMethod("getBestSkeleton", String::class.java).also { it.isAccessible = true }.invoke(value, request)
        val fields = result.javaClass
        return fields.getDeclaredField("bestMatchSkeleton").also { it.isAccessible = true }.get(result).toString()+"/"+fields.getDeclaredField("bestMatchDistanceInfo").also { it.isAccessible = true }.get(result)
    }
    private fun compare(a: Source, b: DateIntervalInfo, label: String) {
        assertEquals(a.fallbackIntervalPattern, b.getFallbackIntervalPattern(), label)
        assertEquals(a.defaultOrder, b.getDefaultOrder(), label)
        assertEquals(a.isFrozen, b.isFrozen(), label)
        assertEquals(a.hashCode(), b.hashCode(), label)
        assertEquals(raw(a), raw(b), label)
        assertEquals(a.patterns.entries.map { it.key to it.value.toList() }, b.getPatterns().entries.map { it.key to it.value.toList() }, label)
        for (request in listOf("", "yMd", "yyyyMMdd", "yMMMMd", "yMMMd", "GyMEd", "Hm", "hmsSSS", "Kmaz", "ahmbz", "EEEd", "QQQQy")) {
            val result = b.getBestSkeleton(request)
            assertEquals(best(a, request), "${result.bestMatchSkeleton}/${result.bestMatchDistanceInfo}", "$label/$request")
        }
    }
    @Test fun allLocaleCalendarResourcesAndAliasesMatchSource() {
        val ids = (SourceLocale.getAvailableLocales().map { it.toString() } + listOf("en@calendar=chinese", "en@calendar=hebrew", "en@calendar=japanese", "en@calendar=dangi", "en@calendar=islamic", "en@calendar=buddhist", "en@calendar=UNKNOWN")).distinct()
        for (id in ids) compare(Source(SourceLocale(id)), DateIntervalInfo(ULocale(id)), id)
    }
    @Test fun customPatternSplitOrderingFieldsAndFailureStatesMatch() {
        val patterns = listOf<String?>("", "d-d", "dd MM", "d-d MM", "MMM d – d, y", "'from' y 'to' y", "''yy'' – yy", "'d-d' y", "latestFirst:y-y", "earliestFirst:M/M", "latestFirst:", null)
        for (field in -1..16) for (text in patterns) {
            val a = Source(); val b = DateIntervalInfo()
            for (fallback in listOf("{1}/{0}", "{0}/{1}")) { a.setFallbackIntervalPattern(fallback); b.setFallbackIntervalPattern(fallback) }
            assertEquals(outcome { a.setIntervalPattern(null, field, text) }, outcome { b.setIntervalPattern(null, field, text) }, "$field/$text")
            assertEquals(raw(a), raw(b))
            for (readField in -1..16) assertEquals(outcome { pattern(a.getIntervalPattern(null, readField)) }, outcome { pattern(b.getIntervalPattern(null, readField)) })
            if (text != null) {
                assertEquals(pattern(Source.genPatternInfo(text, true)), pattern(DateIntervalInfo.genPatternInfo(text, true)), text)
                assertEquals(Source.genPatternInfo(text, true).hashCode(), DateIntervalInfo.genPatternInfo(text, true).hashCode())
            }
        }
        val a = Source(); val b = DateIntervalInfo()
        for (fallback in listOf<String?>(null, "", "{0}", "{1}", "{1}{0}", "{0}{1}", "'{1}'{0}")) {
            assertEquals(outcome { a.setFallbackIntervalPattern(fallback) }, outcome { b.setFallbackIntervalPattern(fallback) })
            assertEquals(a.defaultOrder, b.getDefaultOrder()); assertEquals(a.fallbackIntervalPattern, b.getFallbackIntervalPattern())
        }
    }
    @Test fun cloneCopyOnWriteAliasesAndFallbackIndependentEqualityMatch() {
        val id = "en_US@icu4kcase=intervalclone"
        val a = Source(SourceLocale(id)); val b = DateIntervalInfo(ULocale(id))
        val ac = a.clone(); val bc = b.clone()
        a.setIntervalPattern("yyyyMMdd", 1, "y / y"); b.setIntervalPattern("yyyyMMdd", 1, "y / y")
        compare(a,b,"mutated"); compare(ac,bc,"clone")
        a.freeze(); b.freeze(); assertSame(a,a.clone()); assertSame(b,b.clone())
        val at = a.cloneAsThawed(); val bt = b.cloneAsThawed()
        assertTrue(a.isFrozen); assertTrue(b.isFrozen()); compare(at,bt,"thawed")
        for (field in listOf(-1, 0, 15)) assertEquals(outcome { a.setIntervalPattern("y",field,null) }, outcome { b.setIntervalPattern("y",field,null) })
        val x = Source(); val y = DateIntervalInfo(); val x2 = Source(); val y2 = DateIntervalInfo()
        x2.setFallbackIntervalPattern("{1}/{0}"); y2.setFallbackIntervalPattern("{1}/{0}")
        assertEquals(x == x2, y == y2); assertEquals(x.hashCode(),y.hashCode())
        val snapshot = b.getRawPatterns(); snapshot.values.first().clear(); assertEquals(raw(a),raw(b))
    }
    @Test fun constructorsRetainVirtualCloneAndFallbackCallsButThawBypassesCloneOverride() {
        val eventsA = mutableListOf<String>(); val eventsB = mutableListOf<String>()
        val id = "en_US@icu4kcase=intervalhooks"
        val a = object : Source(SourceLocale(id)) {
            override fun setFallbackIntervalPattern(pattern: String?) { eventsA.add("fallback:$pattern"); super.setFallbackIntervalPattern(pattern) }
            override fun clone(): Source { eventsA.add("clone"); return super.clone() }
            override fun freeze(): Source { eventsA.add("freeze"); return super.freeze() }
        }
        val b = object : DateIntervalInfo(ULocale(id)) {
            override fun setFallbackIntervalPattern(pattern: String?) { eventsB.add("fallback:$pattern"); super.setFallbackIntervalPattern(pattern) }
            override fun clone(): DateIntervalInfo { eventsB.add("clone"); return super.clone() }
            override fun freeze(): DateIntervalInfo { eventsB.add("freeze"); return super.freeze() }
        }
        assertEquals(eventsA,eventsB)
        val ac = a.cloneAsThawed(); val bc = b.cloneAsThawed()
        assertEquals(eventsA,eventsB); compare(ac,bc,"subclass-thaw")
        assertEquals<Class<*>>(b.javaClass,bc.javaClass)
    }
}
