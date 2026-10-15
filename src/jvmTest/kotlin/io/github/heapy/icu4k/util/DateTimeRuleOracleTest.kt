// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import com.ibm.icu.util.DateTimeRule as Source
import java.io.*
import kotlin.random.Random
import kotlin.test.*

class DateTimeRuleOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: IndexOutOfBoundsException) { "bounds" }
    private fun values(a: Source) = listOf(a.dateRuleType,a.ruleMonth,a.ruleDayOfMonth,a.ruleDayOfWeek,a.ruleWeekInMonth,a.ruleMillisInDay,a.timeRuleType)
    private fun values(a: DateTimeRule) = listOf(a.getDateRuleType(),a.getRuleMonth(),a.getRuleDayOfMonth(),a.getRuleDayOfWeek(),a.getRuleWeekInMonth(),a.getRuleMillisInDay(),a.getTimeRuleType())
    @Test fun constructorGettersFormattingAndSerialization() {
        val random=Random(78135)
        repeat(3000) { i ->
            val month=random.nextInt(-2,14); val dom=random.nextInt(-100,100); val dow=random.nextInt(-1,10)
            val week=random.nextInt(); val millis=random.nextInt(); val type=random.nextInt(-1,5); val after=random.nextBoolean()
            val a: Source; val b: DateTimeRule
            when(i%3) {
                0 -> { a=Source(month,dom,millis,type); b=DateTimeRule(month,dom,millis,type) }
                1 -> { a=Source(month,week,dow,millis,type); b=DateTimeRule(month,week,dow,millis,type) }
                else -> { a=Source(month,dom,dow,after,millis,type); b=DateTimeRule(month,dom,dow,after,millis,type) }
            }
            assertEquals(values(a),values(b)); assertEquals(outcome { a.toString() },outcome { b.toString() })
            if(i%100==0) {
                val bytes=ByteArrayOutputStream(); ObjectOutputStream(bytes).use { it.writeObject(b) }
                val copy=ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() } as DateTimeRule
                assertEquals(values(b),values(copy)); assertNotSame(b,copy)
            }
        }
        assertEquals(ObjectStreamClass.lookup(Source::class.java).serialVersionUID,ObjectStreamClass.lookup(DateTimeRule::class.java).serialVersionUID)
    }
}
