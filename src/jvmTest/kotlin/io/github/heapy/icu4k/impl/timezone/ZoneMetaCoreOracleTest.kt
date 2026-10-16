package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.util.Output
import io.github.heapy.icu4k.util.UResourceBundle
import com.ibm.icu.impl.ZoneMeta as Original
import com.ibm.icu.util.TimeZone as OriginalTimeZone
import kotlin.random.Random
import kotlin.test.*

class ZoneMetaCoreOracleTest {
    private val ids = UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "zoneinfo64").get("Names").getStringArray().toList()
    private fun outcome(block: () -> Any?): String = try { "ok:${block()}" } catch (e: Exception) { "error:${e::class.simpleName}" }

    @Test fun allIdsMatchCanonicalRegionalAndEquivalentMetadata() {
        for (id in ids + listOf("", "Missing", "america/new_york", "GMT+05:30", "Etc/Unknown", null)) {
            assertEquals(outcome { Original.getCanonicalCLDRID(id as String?) }, outcome { ZoneMetaCore.getCanonicalCLDRID(id) }, "canonical $id")
            assertEquals(outcome { Original.getIanaID(id) }, outcome { ZoneMetaCore.getIanaID(id) }, "IANA $id")
            assertEquals(outcome { Original.getRegion(id) }, outcome { ZoneMetaCore.getRegion(id) }, "region $id")
            assertEquals(outcome { Original.getShortID(id as String?) }, outcome { ZoneMetaCore.getShortID(id) }, "short $id")
            assertEquals(outcome { Original.getCanonicalCountry(id) }, outcome { ZoneMetaCore.getCanonicalCountry(id) }, "country $id")
            val expectedPrimary = com.ibm.icu.util.Output<Boolean>(true)
            val actualPrimary = Output(true)
            assertEquals(outcome { Original.getCanonicalCountry(id, expectedPrimary) }, outcome { ZoneMetaCore.getCanonicalCountry(id, actualPrimary) }, "primary country $id")
            assertEquals(expectedPrimary.value, actualPrimary.value, "primary flag $id")
            assertEquals(outcome { Original.countEquivalentIDs(id) }, outcome { ZoneMetaCore.countEquivalentIDs(id) }, "count $id")
            val count = if (id == null) 0 else Original.countEquivalentIDs(id)
            for (index in listOf(Int.MIN_VALUE, -1) + (0..count + 1).toList() + Int.MAX_VALUE) {
                assertEquals(outcome { Original.getEquivalentID(id, index) }, outcome { ZoneMetaCore.getEquivalentID(id, index) }, "$id/$index")
            }
            val expected = outcome { Original.openOlsonResource(null, id)?.get("typeOffsets")?.intVector?.toList() }
            val actual = outcome { ZoneMetaCore.openOlsonResource(null, id)?.get("typeOffsets")?.getIntVector()?.toList() }
            assertEquals(expected, actual, "Olson resource $id")
        }
        assertEquals(outcome { Original.getCanonicalCountry(null, null) }, outcome { ZoneMetaCore.getCanonicalCountry(null, null) })
    }
    @Test fun everyRegionAndRawOffsetFilterMatchesOriginalAndCallbackOrder() {
        val offsets = ids.associateWith { Original.getSystemTimeZone(it)?.rawOffset }
        val regions = (ids.mapNotNull { Original.getRegion(it) } + listOf("", "by", "us", "ß", "missing", null)).distinct()
        for (type in ZoneMetaCore.SystemTimeZoneType.entries) {
            val originalType = OriginalTimeZone.SystemTimeZoneType.valueOf(type.name)
            for (region in regions) {
                val expected = Original.getAvailableIDs(originalType, region, null)
                val actual = ZoneMetaCore.getAvailableIDs(type, region)
                assertEquals(expected.toList(), actual.toList(), "$type/$region")
                val calls = mutableListOf<String>()
                ZoneMetaCore.getAvailableIDs(type, region, Int.MIN_VALUE) { calls.add(it); offsets[it] }
                assertEquals(expected.toList(), calls, "$type/$region callbacks")
            }
            for (offset in (offsets.values + listOf(Int.MIN_VALUE, Int.MAX_VALUE, 1)).distinct()) {
                for (region in listOf(null, "US", "BY", "001")) {
                    assertEquals(
                        Original.getAvailableIDs(originalType, region, offset).toList(),
                        ZoneMetaCore.getAvailableIDs(type, region, offset) { offsets[it] }.toList(),
                        "$type/$region/$offset",
                    )
                }
            }
        }
        assertEquals(outcome { Original.getAvailableIDs(null, null, null) }, outcome { ZoneMetaCore.getAvailableIDs(null) })
    }
    @Test fun gmtParserAndFormattingMatchPrivateOracle() {
        val parser = Original::class.java.getDeclaredMethod("parseCustomID", String::class.java, IntArray::class.java).also { it.isAccessible = true }
        val formatter = Original::class.java.getDeclaredMethod("formatCustomID", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).also { it.isAccessible = true }
        val random = Random(7813)
        val inputs = mutableListOf<String?>(null, "", "GMT", "GMT+", "GMT-", "GMT+0", "GMT+00:00:59", "GMT+2147483648", "GMT+4294967296", "GMT+١٢:٣٤:٥٦", "GMT+１２３４５６", "GMT+\uD835\uDFD9", "GMT+1:2", "GMT+1:00:")
        for (h in listOf(0, 1, 9, 10, 23, 24, 99)) for (m in listOf(0, 1, 9, 59, 60, 99)) for (s in listOf(0, 1, 59, 60)) {
            for (sign in listOf("+", "-")) for (separator in listOf("", ":")) {
                inputs.add("GMT$sign${h.toString().padStart(2, '0')}$separator${m.toString().padStart(2, '0')}$separator${s.toString().padStart(2, '0')}")
            }
        }
        repeat(4000) {
            val alphabet = "0123456789:+-x٠١٢９"
            inputs.add((if (random.nextBoolean()) "GMT" else "gMt") + buildString { repeat(random.nextInt(18)) { append(alphabet[random.nextInt(alphabet.length)]) } })
        }
        for (id in inputs) {
            for (size in listOf(-1, 0, 1, 2, 3, 4, 6)) {
                val expected = if (size < 0) null else IntArray(size) { 42 }
                val actual = expected?.copyOf()
                assertEquals(parser.invoke(null, id, expected), ZoneMetaCore.parseCustomID(id, actual), "$id/$size")
                assertContentEquals(expected, actual, "$id/$size fields")
            }
            assertEquals(Original.getCustomID(id), ZoneMetaCore.getCustomID(id), id)
        }
        val extremes = listOf(Int.MIN_VALUE, Int.MAX_VALUE, -1000, -59, -1, 0, 1, 9, 10, 59, 60, 1000)
        repeat(2000) {
            val h = if (it < extremes.size) extremes[it] else random.nextInt()
            val m = random.nextInt()
            val s = random.nextInt()
            val negative = random.nextBoolean()
            assertEquals(formatter.invoke(null, h, m, s, negative), ZoneMetaCore.formatCustomID(h, m, s, negative))
        }
        for (offset in extremes + listOf(-59_999, 59_999, 60_000) + List(2000) { random.nextInt() }) {
            assertEquals(Original.getCustomTimeZone(offset).id, ZoneMetaCore.customIDForOffset(offset), "$offset")
        }
    }
    @Test fun utilityNumberParsingMatchesPositionsUnicodeAndOverflow() {
        val random = Random(78)
        val inputs = listOf("", "-1", "١٢٣x", "１２3", "2147483647", "2147483648", "4294967296", "\uD835\uDFD9x", "\uDC00", "\uD800", "ZZzz") +
            List(1000) { buildString { repeat(random.nextInt(1, 35)) { append("0123456789abcdefXYZ٠١٢３".random(random)) } } }
        for (text in inputs) for (radix in listOf(-1, 0, 1, 2, 8, 10, 16, 36, 37)) for (start in listOf(-1, 0, text.length, text.length + 1)) {
            val expected = intArrayOf(start)
            val actual = intArrayOf(start)
            // Implicit indexing exceptions have platform-specific class names, but both are bounds failures.
            fun result(block: () -> Int): Any = try { block() } catch (e: IndexOutOfBoundsException) { "bounds" }
            assertEquals(result { com.ibm.icu.impl.Utility.parseNumber(text, expected, radix) }, result { Utility.parseNumber(text, actual, radix) }, "$text/$radix/$start")
            assertContentEquals(expected, actual)
        }
    }
}
