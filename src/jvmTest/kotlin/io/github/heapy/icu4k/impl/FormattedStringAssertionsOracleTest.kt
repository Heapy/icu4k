@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.FormattedStringBuilder as JBuilder
import com.ibm.icu.text.NumberFormat.Field as JField
import io.github.heapy.icu4k.text.NumberFormatField
import kotlin.test.*

class FormattedStringAssertionsOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) { is AssertionError -> "assert"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName }
    }
    @Test fun logicalBoundsAssertionsMatchTheSource() {
        assertEquals(JBuilder::class.java.desiredAssertionStatus(), FormattedStringBuilder::class.java.desiredAssertionStatus())
        for (text in listOf("", "a", "😀", "12345")) {
            val j = JBuilder(20); val k = FormattedStringBuilder(20)
            j.append(text, JField.INTEGER); k.append(text, NumberFormatField.INTEGER)
            val indices = listOf(Int.MIN_VALUE, -1, 0, text.length, text.length + 1, Int.MAX_VALUE)
            for (index in indices) {
                assertEquals(outcome { j[index] }, outcome { k[index] }, "$text/char/$index")
                assertEquals(outcome { j.fieldAt(index)?.toString()?.substringAfterLast('(') },
                    outcome { k.fieldAt(index)?.toString()?.substringAfterLast('(') }, "$text/field/$index")
            }
            for (start in indices) for (end in indices) {
                assertEquals(outcome { j.subSequence(start, end).toString() }, outcome { k.subSequence(start, end).toString() }, "$text/$start/$end")
            }
        }
    }
}
