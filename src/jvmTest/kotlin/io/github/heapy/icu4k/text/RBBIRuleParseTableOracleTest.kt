package io.github.heapy.icu4k.text

import kotlin.test.*

class RBBIRuleParseTableOracleTest {
    @Test fun completeGeneratedGrammarMatchesOriginalCompilerTable() {
        val type = Class.forName("com.ibm.icu.text.RBBIRuleParseTable")
        val rows = type.getDeclaredField("gRuleParseStateTable").also { it.isAccessible = true }.get(null) as Array<*>
        val common = RBBIRuleParseTable.gRuleParseStateTable
        assertEquals(rows.size, common.size)
        for (i in rows.indices) {
            val j = rows[i]!!; val k = common[i]
            fun field(name: String): Any? = j.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(j)
            assertEquals((field("fAction") as Number).toInt(), k.fAction, "action row $i")
            assertEquals((field("fCharClass") as Number).toInt(), k.fCharClass, "class row $i")
            assertEquals((field("fNextState") as Number).toInt(), k.fNextState, "next row $i")
            assertEquals((field("fPushState") as Number).toInt(), k.fPushState, "push row $i")
            assertEquals(field("fNextChar"), k.fNextChar, "consume row $i")
            assertEquals(field("fStateName"), k.fStateName, "name row $i")
        }
        for (field in type.declaredFields.filter { it.name.startsWith("do") || it.name.startsWith("kRuleSet_") }) {
            field.isAccessible = true
            val k = RBBIRuleParseTable::class.java.getDeclaredField(field.name).also { it.isAccessible = true }
            assertEquals((field.get(null) as Number).toInt(), k.getInt(null), field.name)
        }
    }
}
