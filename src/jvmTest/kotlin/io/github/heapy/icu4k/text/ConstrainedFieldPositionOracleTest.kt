@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import java.net.URLClassLoader
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.text.NumberFormat.Field as OriginalField
import com.ibm.icu.text.UFieldPosition as OriginalPosition

class ConstrainedFieldPositionOracleTest {
    private class CustomField : FormatField("custom")
    private val custom = CustomField()
    private val jfields = listOf(null, OriginalField.INTEGER, OriginalField.CURRENCY, custom)
    private val kfields = listOf(null, NumberFormatField.INTEGER, NumberFormatField.CURRENCY, custom)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Throwable) {
        val cause = if (failure is InvocationTargetException) failure.cause!! else failure
        listOf(cause.javaClass.simpleName, if (cause is IllegalArgumentException) cause.message else null)
    }
    @Test fun releaseStateTransitionsAndConstraintsMatchSource() {
        val sourceUrl = com.ibm.icu.text.ConstrainedFieldPosition::class.java.protectionDomain.codeSource.location
        URLClassLoader(arrayOf(sourceUrl), null).use { loader ->
            loader.setDefaultAssertionStatus(false)
            val cls = loader.loadClass("com.ibm.icu.text.ConstrainedFieldPosition")
            val source = cls.getConstructor().newInstance()
            val common = ConstrainedFieldPosition()
            fun invoke(name: String, types: Array<Class<*>>, vararg values: Any?): Any? = cls.getMethod(name, *types).invoke(source, *values)
            val stateTypes = arrayOf<Class<*>>(java.text.Format.Field::class.java, Any::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            val fieldTypes = arrayOf<Class<*>>(java.text.Format.Field::class.java, Any::class.java)
            val random = Random(1957)
            val values = listOf(null, "value", 1, 2, Any())
            repeat(1200) {
                val index = random.nextInt(jfields.size)
                val fieldValue = values[random.nextInt(values.size)]
                when (random.nextInt(6)) {
                    0 -> { cls.getMethod("reset").invoke(source); common.reset() }
                    1 -> assertEquals(outcome { invoke("constrainField", arrayOf(java.text.Format.Field::class.java), jfields[index]) }, outcome { common.constrainField(kfields[index]); null })
                    2 -> { invoke("constrainFieldAndValue", fieldTypes, jfields[index], fieldValue); common.constrainFieldAndValue(kfields[index], fieldValue) }
                    3 -> {
                        val kind = random.nextInt(5)
                        val jc = listOf<Class<*>?>(null, Any::class.java, java.text.Format.Field::class.java, OriginalField::class.java, String::class.java)[kind]
                        val kc = listOf<Class<*>?>(null, Any::class.java, java.text.Format.Field::class.java, NumberFormatField::class.java, String::class.java)[kind]
                        assertEquals(outcome { invoke("constrainClass", arrayOf(Class::class.java), jc) }, outcome { common.constrainClass(kc); null })
                    }
                    4 -> {
                        val start = random.nextInt(-100, 100)
                        val end = random.nextInt(-100, 100)
                        invoke("setState", stateTypes, jfields[index], fieldValue, start, end)
                        common.setState(kfields[index], fieldValue, start, end)
                    }
                    else -> {
                        val context = listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0, random.nextLong())[random.nextInt(4)]
                        invoke("setInt64IterationContext", arrayOf(Long::class.javaPrimitiveType!!), context)
                        common.setInt64IterationContext(context)
                    }
                }
                assertEquals(jfields.indexOf(cls.getMethod("getField").invoke(source)), kfields.indexOf(common.getField()))
                assertEquals(cls.getMethod("getFieldValue").invoke(source), common.getFieldValue())
                assertEquals(cls.getMethod("getStart").invoke(source), common.getStart())
                assertEquals(cls.getMethod("getLimit").invoke(source), common.getLimit())
                assertEquals(cls.getMethod("getInt64IterationContext").invoke(source), common.getInt64IterationContext())
                for (field in jfields.indices) for (value in values) assertEquals(outcome { invoke("matchesField", fieldTypes, jfields[field], value) }, outcome { common.matchesField(kfields[field], value) })
                val text = source.toString().replace("com.ibm.icu.text.NumberFormat\$Field", "io.github.heapy.icu4k.text.NumberFormatField")
                assertEquals(text, common.toString())
            }
        }
    }
    @Test fun valueEqualityDirectionAndIdentityShortCircuitMatch() {
        val source = com.ibm.icu.text.ConstrainedFieldPosition()
        val common = ConstrainedFieldPosition()
        val calls = mutableListOf<Any?>()
        val saved = object {
            override fun equals(other: Any?): Boolean { calls.add(other); return other == "allowed" }
            override fun hashCode(): Int = 1
        }
        source.constrainFieldAndValue(OriginalField.INTEGER, saved)
        common.constrainFieldAndValue(NumberFormatField.INTEGER, saved)
        assertTrue(source.matchesField(OriginalField.INTEGER, saved))
        assertTrue(common.matchesField(NumberFormatField.INTEGER, saved))
        assertTrue(calls.isEmpty())
        for (candidate in listOf(null, "allowed", "denied")) {
            calls.clear()
            val expected = source.matchesField(OriginalField.INTEGER, candidate)
            val jc = calls.toList()
            calls.clear()
            val actual = common.matchesField(NumberFormatField.INTEGER, candidate)
            assertEquals(expected, actual)
            assertEquals(jc, calls)
        }
    }
    @Test fun fieldPositionConstructorsMetadataAndEqualityMatch() {
        val jp = listOf(OriginalPosition(), OriginalPosition(0), OriginalPosition(null as java.text.Format.Field?), OriginalPosition(OriginalField.FRACTION), OriginalPosition(OriginalField.CURRENCY, 7))
        val kp = listOf(UFieldPosition(), UFieldPosition(0), UFieldPosition(null as FormatField?), UFieldPosition(NumberFormatField.FRACTION), UFieldPosition(NumberFormatField.CURRENCY, 7))
        for ((j, k) in jp.zip(kp)) {
            assertEquals(j.field, k.getField())
            assertEquals(j.countVisibleFractionDigits, k.getCountVisibleFractionDigits())
            assertEquals(j.fractionDigits, k.getFractionDigits())
            for (count in listOf(-1, 0, 5, Int.MAX_VALUE)) for (digits in listOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE)) {
                j.setFractionDigits(count, digits); k.setFractionDigits(count, digits)
                j.beginIndex = -3; j.endIndex = 99; k.setBeginIndex(-3); k.setEndIndex(99)
                assertEquals(j.countVisibleFractionDigits, k.getCountVisibleFractionDigits())
                assertEquals(j.fractionDigits, k.getFractionDigits())
                assertEquals(j.hashCode(), k.hashCode())
                val text = j.toString().replace("com.ibm.icu.text.UFieldPosition", "io.github.heapy.icu4k.text.UFieldPosition").replace("com.ibm.icu.text.NumberFormat\$Field", "io.github.heapy.icu4k.text.NumberFormatField")
                assertEquals(text, k.toString())
            }
        }
        for (a in jp.indices) for (b in jp.indices) assertEquals(jp[a] == jp[b], kp[a] == kp[b])
    }
}
