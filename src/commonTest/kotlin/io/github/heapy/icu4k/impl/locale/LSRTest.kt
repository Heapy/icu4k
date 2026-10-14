package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.impl.*
import kotlin.test.*

class LSRTest {
    @Test fun regionIndexesAreUniqueForAllValidCodes() {
        val indexes = HashSet<Int>()
        for (a in 'A'..'Z') for (b in 'A'..'Z') assertTrue(indexes.add(LSR.indexForRegion("$a$b")))
        for (number in 0..999) assertTrue(indexes.add(LSR.indexForRegion(number.toString().padStart(3, '0'))))
        assertEquals(LSR.REGION_INDEX_LIMIT - 1, indexes.size)
        for (region in listOf("", "us", "Us", "U", "USA", "1A", "-1", "12x", "1234")) assertEquals(0, LSR.indexForRegion(region))
    }
    @Test fun flagsParticipateInEqualityButNotEquivalence() {
        val a = LSR("en", "Latn", "US", LSR.EXPLICIT_LSR)
        val b = LSR("en", "Latn", "US", LSR.IMPLICIT_LSR)
        assertNotEquals(a, b)
        assertTrue(a.isEquivalentTo(b))
        assertEquals(a, LSR("en", "Latn", "US", 7))
        assertEquals("en-Latn-US", a.toString())
        assertEquals("en-US", LSR("en", "", "US", 0).toString())
    }
    @Test fun pinnedLikelyResourceDecodesAndReencodesEveryLsr() {
        val bundle = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "langInfo",
            ICUResourceBundleReader.embeddedLoader, ICUResourceBundle.OpenType.DIRECT)
        val likely = bundle["likely"]
        val nums = likely["lsrnum"].getIntVector()
        val m49 = likely["m49"].getStringArray()
        val decoded = LSR.decodeInts(nums, m49)
        assertTrue(decoded.size > 7000)
        for (index in nums.indices) {
            val lsr = decoded[index]
            assertEquals(LSR.IMPLICIT_LSR, lsr.flags)
            if (nums[index] > 1 || nums[index] < 0) assertEquals(nums[index], lsr.encodeToIntForResource(m49.asList()), "$index/$lsr")
        }
        val sentinels = LSR.decodeInts(intArrayOf(0, 1), emptyArray())
        assertEquals("", sentinels[0].toString())
        assertEquals("skip-script", sentinels[1].toString())
        val firstEn = decoded.first { it.language == "en" }
        val anotherEn = decoded.first { it !== firstEn && it.language == "en" }
        assertSame(firstEn.language, anotherEn.language)
        val metadata = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "metadata",
            ICUResourceBundleReader.embeddedLoader, ICUResourceBundle.OpenType.DIRECT)
        assertEquals("he", metadata["alias"]["language"]["iw"]["replacement"].getString())
    }
    @Test fun nullNumericTableIsOnlyReadForNumericRegions() {
        val encoded = LSR("en", "Latn", "US", 0).encodeToIntForResource(null)
        assertEquals(LSR("en", "Latn", "US", 0), LSR.decodeInts(intArrayOf(encoded), null).single())
        assertEquals(0, LSR.decodeInts(intArrayOf(), null).size)
        assertEquals("skip-script", LSR.decodeInts(intArrayOf(1), null).single().toString())
        assertFailsWith<NullPointerException> { LSR("en", "Latn", "001", 0).encodeToIntForResource(null) }
        val numeric = LSR("en", "Latn", "001", 0).encodeToIntForResource(listOf("001"))
        assertFailsWith<NullPointerException> { LSR.decodeInts(intArrayOf(numeric), null) }
    }
    @Test fun absentNumericRegionHasUpstreamDiagnostic() {
        val failure = assertFailsWith<IllegalStateException> { LSR("en", "Latn", "419", 0).encodeToIntForResource(emptyList()) }
        assertEquals("Please add '419' to M49 in LocaleDistanceMapper.java", failure.message)
    }
}
