// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.Test
import kotlin.test.assertEquals

class RbnfChinesePostProcessorTest {
    @Test fun longFormAndUnknownNamesRetainSourceState() {
        val processor = RbnfChinesePostProcessor()
        fun check(name: String, text: String, expected: String) {
            val buffer = StringBuilder(text)
            processor.process(buffer, name)
            assertEquals(expected, buffer.toString())
        }
        check("%traditional", "一萬*〇億*〇", "一萬億〇")
        check("%simplified", "一|*〇*", "一|〇")
        check("%unknown", "*二|*", "二|")
        check("%accounting", "一萬*零億*零", "一萬億零")
        check("%time", "*一*二|", "一二|")
    }
}
