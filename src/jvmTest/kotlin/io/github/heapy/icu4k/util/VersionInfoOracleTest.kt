package io.github.heapy.icu4k.util

import com.ibm.icu.util.VersionInfo as JavaVersionInfo
import kotlin.test.*

class VersionInfoOracleTest {
    @Test fun matchesUpstreamValuesAndOrdering() {
        val inputs = listOf("", ".", "...", "....", "1..2.", "78.3", "17", "127.255", "128", "255.255.255.255")
        for (input in inputs) {
            val actual = VersionInfo.getInstance(input)
            val expected = JavaVersionInfo.getInstance(input)
            assertEquals(expected.toString(), actual.toString(), input)
            assertEquals(expected.hashCode(), actual.hashCode(), input)
            for (min in 1..4) for (max in min..4) {
                assertEquals(expected.getVersionString(min, max), actual.getVersionString(min, max), input)
            }
            for (other in inputs) {
                val a = actual.compareTo(VersionInfo.getInstance(other)).compareTo(0)
                val b = expected.compareTo(JavaVersionInfo.getInstance(other)).compareTo(0)
                assertEquals(b, a, "$input vs $other")
            }
        }
    }
}
