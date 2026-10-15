// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RuleBasedNumberFormat
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RbnfChinesePostProcessorOracleTest {
    @Test fun cleanupAndPersistentRuleSetSelectionMatchIcu() {
        val sourceClass = Class.forName("com.ibm.icu.text.RBNFChinesePostProcessor")
        val source = sourceClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val ruleClass = Class.forName("com.ibm.icu.text.NFRuleSet")
        val ctor = ruleClass.getDeclaredConstructor(RuleBasedNumberFormat::class.java, Array<String>::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val process = sourceClass.getDeclaredMethod("process", StringBuilder::class.java, ruleClass).apply { isAccessible = true }
        val target = RbnfChinesePostProcessor()
        val names = listOf("%unknown", "%traditional", "%simplified", "%accounting", "%time")
        val random = Random(7813)
        val chunks = listOf("", "一", "*〇", "二〇", "*零", "二零", "三")
        repeat(1600) { i ->
            val name = names[if (i < names.size) i else random.nextInt(names.size)]
            val text = if (i < 8) listOf("", "*", "***", "*〇萬*〇億*〇", "二〇萬*〇億三〇", "*零萬二零億*零", "一點*〇", "一|*二")[i]
                else buildString {
                    append(chunks.random(random)); append("兆")
                    append(chunks.random(random)); append("億")
                    append(chunks.random(random)); append("萬")
                    append(chunks.random(random))
                    if (random.nextBoolean()) append("點*〇*零")
                }
            val rule = ctor.newInstance(null, arrayOf("$name: 0: zero;"), 0)
            val expected = StringBuilder(text)
            process.invoke(source, expected, rule)
            val actual = StringBuilder(text)
            target.process(actual, name)
            assertEquals(expected.toString(), actual.toString(), "$name: $text")
        }
    }
}
