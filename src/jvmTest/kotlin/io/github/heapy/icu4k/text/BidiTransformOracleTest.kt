// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*
import com.ibm.icu.text.BidiTransform as Oracle

class BidiTransformOracleTest {
    @Test fun allSchemesShapingMirroringAndLevels() {
        val source = Oracle()
        val port = BidiTransform()
        val texts = listOf("", "abc אבג (12)", "مرحبا 123 (abc)", "ﻻ ١٢٣", "a\u2067אב\u2069 z", "123", "😀אב(א)", charArrayOf('a', '\ud800', 'א', '\udc00').concatToString())
        val options = intArrayOf(0, 8, 16, 24, 32, 64, 96, 128, 256, 0x10000, 9, 17, 0x200000, -1)
        for (text in texts) for (input in byteArrayOf(0, 1, 2, 125, 126, 127, -1))
            for (output in byteArrayOf(0, 1, 126, 127, -2))
                for (inOrder in BidiTransform.Order.entries) for (outOrder in BidiTransform.Order.entries)
                    for (mirror in BidiTransform.Mirroring.entries) for (option in options) {
                        fun outcome(action: () -> String): String = try { "OK:" + action() } catch (e: Throwable) { "ERR:" + e::class.java.name + ":" + e.message }
                        val expected = outcome { source.transform(text, input, Oracle.Order.valueOf(inOrder.name), output, Oracle.Order.valueOf(outOrder.name), Oracle.Mirroring.valueOf(mirror.name), option) }
                        val actual = outcome { port.transform(text, input, inOrder, output, outOrder, mirror, option) }
                        assertEquals(expected, actual, "text=$text in=$input/$inOrder out=$output/$outOrder mirror=$mirror options=$option")
                    }
    }
    @Test fun nullArgumentsAndRecovery() {
        val port = BidiTransform()
        assertFailsWith<IllegalArgumentException> { port.transform(null, 0, BidiTransform.Order.LOGICAL, 0, BidiTransform.Order.VISUAL, BidiTransform.Mirroring.OFF, 0) }
        assertFailsWith<IllegalArgumentException> { port.transform("abc", 0, null, 0, BidiTransform.Order.VISUAL, BidiTransform.Mirroring.OFF, 0) }
        assertEquals("abc", port.transform("abc", 0, BidiTransform.Order.LOGICAL, 0, BidiTransform.Order.VISUAL, BidiTransform.Mirroring.OFF, 0))
    }
}
