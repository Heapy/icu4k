package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.StringRange as J
import kotlin.random.Random
import kotlin.test.*

class StringRangeOracleTest {
    @Test fun expansionCompactionOrderingAndFailuresMatchOriginal() {
        val random = Random(442091)
        val samples = listOf("", "a", "b", "ab", "ac", "bc", "abc", "abd", "😀", "😁", "a😀", "a😁", "\ud800", "\udc00")
        for (start in samples + null) for (end in samples + null) for (same in listOf(false, true)) {
            // Bound rectangular products; malformed surrogate-to-BMP ranges can be very large.
            if (start != null && end != null && start.any { it.code > 127 } != end.any { it.code > 127 }) continue
            val jo = arrayListOf("existing"); val ko = arrayListOf("existing")
            val j = runCatching { J.expand(start, end, same, jo) }
            val k = runCatching { StringRange.expand(start, end, same, ko) }
            if (j.isSuccess) { assertSame(jo, j.getOrThrow()); assertSame(ko, k.getOrThrow()) }
            else { assertNotNull(k.exceptionOrNull()); assertEquals(j.exceptionOrNull()?.message, k.exceptionOrNull()?.message) }
            assertEquals(jo, ko)
        }
        val sets = mutableListOf<Set<String>>(emptySet(), linkedSetOf(""), linkedSetOf("ab", "a"))
        repeat(500) {
            sets += (0 until random.nextInt(50)).map {
                buildString { repeat(random.nextInt(1, 5)) { append(listOf("a", "b", "c", "😀", "😁", "\ud800", "\udc00").random(random)) } }
            }.toSortedSet()
        }
        for (source in sets) for (shorter in listOf(false, true)) for (more in listOf(false, true)) {
            val jo = ArrayList<Pair<String?, String?>>(); val ko = ArrayList<Pair<String?, String?>>()
            val j = runCatching { J.compact(source, { a, b -> jo.add(a to b) }, shorter, more) }
            val k = runCatching { StringRange.compact(source, { a, b -> ko.add(a to b) }, shorter, more) }
            assertEquals(j.isSuccess, k.isSuccess, "$source shorter=$shorter more=$more")
            assertEquals(jo, ko)
        }
        repeat(1000) {
            val a = IntArray(random.nextInt(8)) { random.nextInt() }; val b = IntArray(random.nextInt(8)) { random.nextInt() }
            assertEquals(J.COMPARE_INT_ARRAYS.compare(a, b), StringRange.COMPARE_INT_ARRAYS.compare(a, b))
        }
    }
}
