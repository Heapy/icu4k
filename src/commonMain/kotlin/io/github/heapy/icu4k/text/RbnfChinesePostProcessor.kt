// SPDX-License-Identifier: Unicode-3.0
// Port of ICU4J 78.3 RBNFChinesePostProcessor (Unicode, Inc. and IBM).
package io.github.heapy.icu4k.text

/** Chinese RBNF cleanup kernel; the rule-set adapter is added with the RBNF engine. */
internal class RbnfChinesePostProcessor {
    private var longForm = false
    private var format = 0

    fun process(buf: StringBuilder, ruleSetName: String) {
        for (i in ruleSetNames.indices) {
            if (ruleSetNames[i] == ruleSetName) {
                format = i
                longForm = i == 1 || i == 3
                break
            }
        }
        if (longForm) {
            var i = buf.indexOf("*")
            while (i != -1) {
                buf.deleteAt(i)
                i = buf.indexOf("*", i)
            }
            return
        }
        val markers = arrayOf(
            arrayOf("萬", "億", "兆", "〇"),
            arrayOf("万", "亿", "兆", "〇"),
            arrayOf("萬", "億", "兆", "零"),
        )
        val m = markers[format]
        for (i in 0..<m.lastIndex) {
            val n = buf.indexOf(m[i])
            if (n != -1) buf.insert(n + m[i].length, '|')
        }
        var x = buf.indexOf("點")
        if (x == -1) x = buf.length
        var state = 0
        var previousOptional = -1
        val ling = m[3]
        while (x >= 0) {
            val boundary = buf.lastIndexOf("|", x)
            val zero = buf.lastIndexOf(ling, x)
            val nextState = if (zero > boundary) {
                if (zero > 0 && buf[zero - 1] != '*') 2 else 1
            } else 0
            x = boundary - 1
            when (state * 3 + nextState) {
                0, 2, 3, 6, 8 -> {
                    state = nextState
                    previousOptional = -1
                }
                1 -> {
                    state = nextState
                    previousOptional = zero
                }
                4, 7 -> {
                    buf.deleteRange(zero - 1, zero + ling.length)
                    state = 0
                    previousOptional = -1
                }
                5 -> {
                    buf.deleteRange(previousOptional - 1, previousOptional + ling.length)
                    state = nextState
                    previousOptional = -1
                }
                else -> throw IllegalStateException()
            }
        }
        for (i in buf.lastIndex downTo 0) {
            if (buf[i] == '*' || buf[i] == '|') buf.deleteAt(i)
        }
    }

    private companion object {
        val ruleSetNames = arrayOf("%traditional", "%simplified", "%accounting", "%time")
    }
}
