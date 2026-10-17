package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.*
import kotlin.test.*

class DateIntervalInfoTest {
    @Test fun splitPatternsAliasesAndStickyDefaultOrder() {
        val info = DateIntervalInfo()
        assertEquals("{0} – {1}", info.getFallbackIntervalPattern())
        info.setFallbackIntervalPattern("{1} ~ {0}")
        info.setFallbackIntervalPattern("{0} / {1}")
        assertTrue(info.getDefaultOrder())
        info.setIntervalPattern("yMd", Calendar.HOUR_OF_DAY, "earliestFirst:HH:mm – HH:mm")
        val pattern = info.getIntervalPattern("yMd", Calendar.HOUR_OF_DAY)!!
        assertEquals("HH:mm – ", pattern.getFirstPart()); assertEquals("HH:mm", pattern.getSecondPart())
        assertFalse(pattern.firstDateInPtnIsLaterDate())
        assertSame(pattern, info.getIntervalPattern("yMd", Calendar.HOUR))
        assertSame(pattern, info.getIntervalPattern("yMd", Calendar.AM_PM))
        info.setIntervalPattern(null, Calendar.DAY_OF_WEEK, "d – d")
        assertSame(info.getIntervalPattern(null, Calendar.DAY_OF_WEEK), info.getIntervalPattern(null, Calendar.DATE))
        assertNull(info.getIntervalPattern("absent", -1))
        assertFailsWith<IndexOutOfBoundsException> { info.getIntervalPattern(null, -1) }
    }
    @Test fun localeCopyOnWriteFrozenCloneAndDetachedSnapshots() {
        val locale = ULocale("en_US@icu4kcase=intervalinfo")
        val a = DateIntervalInfo(locale); val b = DateIntervalInfo(locale)
        assertFalse(a.isFrozen()); assertEquals(a, b)
        a.setIntervalPattern("yMd", Calendar.YEAR, "'custom' y – y")
        assertNotEquals(a.getIntervalPattern("yMd", Calendar.YEAR), b.getIntervalPattern("yMd", Calendar.YEAR))
        a.freeze(); assertSame(a, a.clone())
        val thaw = a.cloneAsThawed(); assertFalse(thaw.isFrozen()); assertTrue(a.isFrozen())
        thaw.setIntervalPattern("yMd", Calendar.YEAR, "y / y")
        assertNotEquals(a, thaw)
        val snapshot = a.getRawPatterns(); snapshot.clear(); assertTrue(a.getRawPatterns().isNotEmpty())
        assertFailsWith<UnsupportedOperationException> { a.setIntervalPattern(null, Int.MAX_VALUE, null) }
        assertFailsWith<UnsupportedOperationException> { a.setFallbackIntervalPattern(null) }
        assertEquals(0, DateIntervalInfo().hashCode())
    }
    @Test fun sourceLocalePatternOrderContentsAndHashSnapshots() {
        // ICU4J 78.3 snapshots include key order, split patterns, endpoint order and map hash.
        val expected = listOf(
            Triple("en_US", -6765520510641408014L, -832801415),
            Triple("fr", 2867842648104962656L, 145238565),
            Triple("ar", -2127902054780016674L, 2102707727),
            Triple("ru", -1866206445720843815L, -922905971),
            Triple("zh@calendar=chinese", 541756130300259262L, 1776320006),
            Triple("he@calendar=hebrew", -8905924997709180332L, 490429077),
            Triple("th@calendar=buddhist", 7508200521089152632L, 192198820),
        )
        for ((id, fingerprint, mapHash) in expected) {
            val info = DateIntervalInfo(ULocale(id))
            var hash = -3750763034362895579L
            fun word(value: Int) { hash = (hash xor value.toLong()) * 1099511628211L }
            fun text(value: String?) { word(value?.length ?: -1); if (value != null) for (ch in value) word(ch.code) }
            for ((key, fields) in info.getRawPatterns()) {
                text(key)
                for ((field, pattern) in fields) { text(field); text(pattern.getFirstPart()); text(pattern.getSecondPart()); word(if (pattern.firstDateInPtnIsLaterDate()) 1 else 0) }
                word(-2)
            }
            assertEquals(fingerprint, hash, id)
            assertEquals(mapHash, info.hashCode(), id)
        }
    }
    @Test fun bestSkeletonSnapshotsAndPatternValueEquality() {
        val info = DateIntervalInfo(ULocale.US)
        assertEquals("yMMMd", info.getBestSkeleton("yMMMd").bestMatchSkeleton)
        assertEquals(0, info.getBestSkeleton("yMMMd").bestMatchDistanceInfo)
        assertEquals(2, info.getBestSkeleton("ahmz").bestMatchDistanceInfo)
        assertFailsWith<IndexOutOfBoundsException> { info.getBestSkeleton("#") }
        val p = DateIntervalInfo.PatternInfo("a", "b", true)
        assertEquals(-4, p.hashCode()); assertEquals(p, DateIntervalInfo.PatternInfo("a", "b", true))
        assertEquals("{first=«a», second=«b», reversed:true}", p.toString())
        assertEquals(DateIntervalInfo.PatternInfo("", null, false), DateIntervalInfo.genPatternInfo("", false))
    }
}
