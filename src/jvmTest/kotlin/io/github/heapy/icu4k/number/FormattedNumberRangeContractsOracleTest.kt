@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.number.FormattedNumberRange as JResult
import com.ibm.icu.number.NumberRangeFormatter.RangeIdentityResult as JIdentity
import com.ibm.icu.impl.FormattedStringBuilder as JBuffer
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.DecimalQuantity
import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.number.NumberRangeFormatter.RangeIdentityResult
import io.github.heapy.icu4k.text.FormatField
import java.math.BigDecimal
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import kotlin.test.*

class FormattedNumberRangeContractsOracleTest {
    private val ctor = JResult::class.java.getDeclaredConstructor(JBuffer::class.java, JQuantity::class.java, JQuantity::class.java, JIdentity::class.java).also { it.isAccessible = true }
    private class SharedField : FormatField("shared")
    private fun jResult(buffer: JBuffer?, a: JQuantity?, b: JQuantity?, identity: JIdentity?): JResult = ctor.newInstance(buffer, a, b, identity) as JResult
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        val failure = if (e is InvocationTargetException) e.cause!! else e
        when (failure) { is NullPointerException -> "null"; else -> failure.javaClass.simpleName }
    }
    @Test fun retainedStateHashFormulaAndIdentityIndependentEquality() {
        val field = SharedField()
        val jb = JBuffer(); val kb = FormattedStringBuilder(); jb.append("12", field); kb.append("12", field)
        val j1 = JBCD(1); val j2 = JBCD(2)
        val k1 = DecimalQuantity_DualStorageBCD(1); val k2 = DecimalQuantity_DualStorageBCD(2)
        val j = jResult(jb, j1, j2, JIdentity.EQUAL_BEFORE_ROUNDING)
        val k = FormattedNumberRange(kb, k1, k2, RangeIdentityResult.EQUAL_BEFORE_ROUNDING)
        val jd = jResult(JBuffer(jb), JBCD(j1), JBCD(j2), JIdentity.NOT_EQUAL)
        val kd = FormattedNumberRange(FormattedStringBuilder(kb), DecimalQuantity_DualStorageBCD(k1), DecimalQuantity_DualStorageBCD(k2), RangeIdentityResult.NOT_EQUAL)
        assertTrue(j == jd); assertTrue(k == kd)
        assertEquals(j.hashCode(), k.hashCode())
        assertSame(j1, j.firstFixedDecimal); assertSame(k1, k.getFirstFixedDecimal())
        assertSame(j2, j.secondFixedDecimal); assertSame(k2, k.getSecondFixedDecimal())
        jb.append("3", field); kb.append("3", field); j1.setToInt(5); k1.setToInt(5)
        assertEquals(j.toString(), k.toString()); assertEquals(j.firstBigDecimal, k.getFirstBigDecimal())
        assertEquals(j == jd, k == kd)
        assertEquals(j.hashCode(), k.hashCode())
        assertEquals(kb.toCharArray().contentHashCode() xor kb.toFieldArray().contentHashCode() xor k1.toBigDecimal().hashCode() xor k2.toBigDecimal().hashCode(), k.hashCode())
        val jn = jResult(null, null, null, null); val kn = FormattedNumberRange(null, null, null, null)
        assertNull(jn.identityResult); assertNull(kn.getIdentityResult()); assertNull(kn.getFirstFixedDecimal())
        assertEquals(outcome { jn.hashCode() }, outcome { kn.hashCode() })
        assertTrue(kn.equals(kn)); assertFalse(kn.equals(null))
    }
    @Test fun scaleSensitiveDecimalExportsAndEqualityCallbackOrder() {
        fun <T> quantity(type: Class<T>, value: String, events: MutableList<String>, label: String): T {
            @Suppress("UNCHECKED_CAST")
            return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
                if (method.name != "toBigDecimal") error("Unexpected ${method.name}")
                events.add(label)
                BigDecimal(value)
            } as T
        }
        for (right in listOf("1.0", "1.00")) {
            val je = mutableListOf<String>(); val ke = mutableListOf<String>()
            val jb = JBuffer(); val kb = FormattedStringBuilder(); jb.append("same", null); kb.append("same", null)
            val j = jResult(jb, quantity(JQuantity::class.java, "1.0", je, "a1"), quantity(JQuantity::class.java, "2", je, "a2"), JIdentity.NOT_EQUAL)
            val k = FormattedNumberRange(kb, quantity(DecimalQuantity::class.java, "1.0", ke, "a1"), quantity(DecimalQuantity::class.java, "2", ke, "a2"), RangeIdentityResult.NOT_EQUAL)
            val jo = jResult(jb, quantity(JQuantity::class.java, right, je, "b1"), quantity(JQuantity::class.java, "2", je, "b2"), JIdentity.NOT_EQUAL)
            val ko = FormattedNumberRange(kb, quantity(DecimalQuantity::class.java, right, ke, "b1"), quantity(DecimalQuantity::class.java, "2", ke, "b2"), RangeIdentityResult.NOT_EQUAL)
            assertEquals(j == jo, k == ko); assertEquals(je, ke)
            je.clear(); ke.clear()
            assertEquals(j.hashCode(), k.hashCode()); assertEquals(je, ke)
            assertEquals(listOf("a1", "a2"), ke)
        }
    }
}
