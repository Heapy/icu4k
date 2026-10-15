@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.Grouper as JGrouper
import com.ibm.icu.impl.number.DecimalFormatProperties as JProperties
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import java.lang.reflect.Proxy
import kotlin.test.*

class GrouperOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "null"; else -> "${e.javaClass.simpleName}:${e.message}" }
    }
    @Test fun propertyGetterOrderAndDisabledShortCircuit() {
        for (enabled in listOf(false, true)) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            val j = object : JProperties() {
                override fun getGroupingUsed(): Boolean { jt.add("used"); return enabled }
                override fun getGroupingSize(): Int { jt.add("first"); return 65539 }
                override fun getSecondaryGroupingSize(): Int { jt.add("second"); return 65538 }
                override fun getMinimumGroupingDigits(): Int { jt.add("minimum"); return 65537 }
            }
            val k = object : DecimalFormatProperties() {
                override fun getGroupingUsed(): Boolean { kt.add("used"); return enabled }
                override fun getGroupingSize(): Int { kt.add("first"); return 65539 }
                override fun getSecondaryGroupingSize(): Int { kt.add("second"); return 65538 }
                override fun getMinimumGroupingDigits(): Int { kt.add("minimum"); return 65537 }
            }
            assertEquals(JGrouper.forProperties(j).primary, Grouper.forProperties(k).getPrimary())
            assertEquals(jt, kt)
            assertEquals(if (enabled) listOf("used", "first", "second", "minimum") else listOf("used"), kt)
        }
        assertEquals(outcome { JGrouper.forProperties(null) }, outcome { Grouper.forProperties(null) })
    }
    @Test fun quantityCallbacksAndArithmeticShortCircuits() {
        for (first in listOf<Short>(-1, 0, 2, 3)) for (second in listOf<Short>(0, 2, 3)) for (position in listOf(-1, 0, 2, 3, 4, 6, Int.MAX_VALUE)) {
            val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
            fun proxy(type: Class<*>, trace: MutableList<String>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
                trace.add(method.name)
                if (method.name == "getUpperDisplayMagnitude") 8 else error("Unexpected callback")
            }
            val j = JGrouper.getInstance(first, second, 1); val k = Grouper.getInstance(first, second, 1)
            assertEquals(outcome { j.groupAtPosition(position, proxy(JQuantity::class.java, jt) as JQuantity) },
                outcome { k.groupAtPosition(position, proxy(DecimalQuantity::class.java, kt) as DecimalQuantity) })
            assertEquals(jt, kt)
        }
    }
}
