package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.RuleCharacterIterator as JavaIterator
import java.text.ParsePosition as JavaPosition
import io.github.heapy.icu4k.text.ParsePosition
import kotlin.random.Random
import kotlin.test.*

class RuleCharacterIteratorOracleTest {
    @Test fun iterationAndSnapshotsMatchOriginal() {
        val random = Random(729)
        val parts = listOf("a", " ", "\u200e", "😀", "\\u0041", "\\uD83D\\uDE00", "\\x{21}", "\\n", "\\q", "$", "\\x{}")
        repeat(600) {
            val text = buildString { repeat(random.nextInt(20)) { append(parts.random(random)) } }
            for (options in 0..7) {
                val jp = JavaPosition(0); val kp = ParsePosition(0)
                val j = JavaIterator(text, null, jp); val k = RuleCharacterIterator(text, null, kp)
                repeat(text.length + 2) {
                    val js = j.getPos(null); val ks = k.getPos(null)
                    val expected = runCatching { j.next(options) }; val actual = runCatching { k.next(options) }
                    assertEquals(expected.getOrNull(), actual.getOrNull())
                    assertEquals(expected.isSuccess, actual.isSuccess)
                    assertEquals(jp.index, kp.index)
                    assertEquals(j.isEscaped, k.isEscaped())
                    assertEquals(j.atEnd(), k.atEnd())
                    assertEquals(j.currentBuffer, k.getCurrentBuffer())
                    assertEquals(j.currentBufferPos, k.getCurrentBufferPos())
                    assertEquals(j.toString(), k.toString())
                    j.setPos(js); k.setPos(ks)
                    runCatching { j.next(options) }; runCatching { k.next(options) }
                }
            }
        }
    }
}
