package io.github.heapy.icu4k.util

import kotlin.test.*

class VersionInfoTest {
    @Test fun parsesAndFormats() {
        assertEquals("78.3.0.0", VersionInfo.getInstance("78.3").toString())
        assertEquals(VersionInfo.getInstance(78, 3), VersionInfo.getInstance("78.3.0.0"))
        assertEquals("78.3", VersionInfo.ICU_VERSION.getVersionString(1, 4))
        assertEquals("0.0.0.0", VersionInfo.getInstance("").toString())
        assertEquals("1.0.2.0", VersionInfo.getInstance("1..2.").toString())
    }

    @Test fun ordersUnsignedBytes() {
        val values = listOf("0", "1", "1.0.0.1", "1.0.1", "1.1", "127.255.255.255", "128", "255.255.255.255")
            .map { VersionInfo.getInstance(it) }
        assertEquals(values, values.reversed().sorted())
    }

    @Test fun rejectsInvalidInputAndOverflow() {
        for (value in listOf("-1", "256", "1.256", "1.2.3.4.5", "a", " 1", "4294967296")) {
            assertFailsWith<IllegalArgumentException>(value) { VersionInfo.getInstance(value) }
        }
        assertFailsWith<IllegalArgumentException> { VersionInfo.getInstance(-1) }
        assertFailsWith<IllegalArgumentException> { VersionInfo.getInstance(256) }
        assertFailsWith<IllegalArgumentException> { VersionInfo.getInstance(1).getVersionString(3, 2) }
    }
}
