package io.github.heapy.icu4k.text

import java.text.AttributedString
import kotlin.random.Random
import kotlin.test.*

class PortableAttributedCharacterIteratorOracleTest {
    private val fields = listOf(NumberFormatField.INTEGER, NumberFormatField.FRACTION, ListSpanField.LIST_SPAN)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Exception) { failure.javaClass.name }
    @Test fun runsNavigationAndClonesMatchJava() {
        val random = Random(719)
        repeat(160) {
            val text = "a😀 bcdef".take(random.nextInt(10))
            val ranges = if (text.isEmpty()) emptyList() else List(random.nextInt(15)) {
                val start = random.nextInt(text.length)
                FormatAttributeRange(fields.random(random), random.nextInt(3), start, random.nextInt(start + 1, text.length + 1))
            }
            val j = AttributedString(text).also { target -> ranges.forEach { target.addAttribute(it.field, it.value, it.start, it.limit) } }.iterator
            val k = PortableAttributedCharacterIterator(text, ranges)
            assertEquals(j.allAttributeKeys, k.getAllAttributeKeys())
            for (i in 0..text.length) {
                assertEquals(j.setIndex(i), k.setIndex(i))
                assertEquals(j.runStart, k.getRunStart()); assertEquals(j.runLimit, k.getRunLimit())
                assertEquals(j.attributes, k.getAttributes())
                for (field in fields + listOf(null)) {
                    assertEquals(j.getAttribute(field), k.getAttribute(field))
                    assertEquals(j.getRunStart(field), k.getRunStart(field))
                    assertEquals(j.getRunLimit(field), k.getRunLimit(field))
                }
                for (set in listOf(emptySet(), fields.toSet(), setOf(fields[0]), null)) {
                    assertEquals(outcome { j.getRunStart(set) }, outcome { k.getRunStart(set) })
                    assertEquals(outcome { j.getRunLimit(set) }, outcome { k.getRunLimit(set) })
                }
                assertEquals(j.hashCode(), k.hashCode())
                val copy = k.clone() as FormatAttributedCharacterIterator
                assertEquals(k, copy)
                copy.next()
                assertEquals(i, k.getIndex())
            }
            repeat(70) {
                when (random.nextInt(6)) {
                    0 -> assertEquals(j.first(), k.first())
                    1 -> assertEquals(j.last(), k.last())
                    2 -> assertEquals(j.next(), k.next())
                    3 -> assertEquals(j.previous(), k.previous())
                    4 -> {
                        val position = random.nextInt(-2, text.length + 3)
                        assertEquals(outcome { j.setIndex(position) }, outcome { k.setIndex(position) })
                    }
                    else -> assertEquals(j.current(), k.current())
                }
                assertEquals(j.index, k.getIndex())
            }
        }
    }
}
