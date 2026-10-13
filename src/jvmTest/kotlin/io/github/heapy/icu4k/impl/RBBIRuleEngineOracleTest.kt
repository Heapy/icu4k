package io.github.heapy.icu4k.impl

import com.ibm.icu.text.RuleBasedBreakIterator
import io.github.heapy.icu4k.text.StringCharacterIterator
import java.nio.ByteBuffer
import java.text.StringCharacterIterator as JavaCursor
import kotlin.random.Random
import kotlin.test.*

class RBBIRuleEngineOracleTest {
    @Test fun forwardAndSafeReverseMatchOriginalPrivateStateMachines() {
        val type = RuleBasedBreakIterator::class.java
        val next = type.getDeclaredMethod("handleNext").apply { isAccessible = true }
        val previous = type.getDeclaredMethod("handleSafePrevious", Int::class.javaPrimitiveType).apply { isAccessible = true }
        val position = type.getDeclaredField("fPosition").apply { isAccessible = true }
        val status = type.getDeclaredField("fRuleStatusIndex").apply { isAccessible = true }
        val dictionary = type.getDeclaredField("fDictionaryCharCount").apply { isAccessible = true }
        val cursor = type.getDeclaredField("fText").apply { isAccessible = true }
        val random = Random(202)
        for (name in listOf("word", "word_POSIX", "sent", "sent_el", "char", "title", "line", "line_cj", "line_normal", "line_normal_cj", "line_loose", "line_loose_cj", "line_phrase_cj", "line_normal_phrase_cj", "line_loose_phrase_cj")) {
            val raw = ICUData.getBytes("brkitr/$name.brk")!!
            val java = RuleBasedBreakIterator.getInstanceFromCompiledRules(ByteBuffer.wrap(raw))
            val data = RBBIDataWrapper.get(io.github.heapy.icu4k.util.BinaryBuffer(raw))
            repeat(40) {
                val text = if (it == 0) "Hello, world! ไทย 中文 日本語. A\u0301 👩‍👩‍👧‍👦\r\nNext sentence!" else CharArray(60) {
                    when (random.nextInt(5)) {
                        0 -> ' '
                        1 -> random.nextInt(0xd800, 0xe000).toChar()
                        2 -> 0xffff.toChar()
                        else -> random.nextInt(0x10000).toChar()
                    }
                }.concatToString()
                val begin = if (it % 2 == 0) 0 else random.nextInt(text.length + 1)
                val end = if (it % 2 == 0) text.length else random.nextInt(begin, text.length + 1)
                java.setText(JavaCursor(text, begin, end, begin))
                val common = RBBIRuleEngine(data, StringCharacterIterator(text, begin, end, begin))
                position.setInt(java, begin)
                repeat(text.length + 2) {
                    val j = next.invoke(java) as Int
                    val k = common.handleNext()
                    assertEquals(j, k, "$name forward")
                    assertEquals(position.getInt(java), common.position)
                    assertEquals(status.getInt(java), common.ruleStatusIndex)
                    assertEquals(dictionary.getInt(java), common.dictionaryCharCount)
                    assertEquals((cursor.get(java) as java.text.CharacterIterator).index, common.text.getIndex())
                }
                for (index in -2..text.length + 2) {
                    assertEquals(previous.invoke(java, index), common.handleSafePrevious(index), "$name reverse $index")
                    assertEquals((cursor.get(java) as java.text.CharacterIterator).index, common.text.getIndex())
                }
            }
        }
    }
}
