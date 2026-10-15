@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.MultiplierFormatHandler as JHandler
import com.ibm.icu.impl.number.MicroPropsGenerator as JGenerator
import com.ibm.icu.impl.number.MicroProps as JMicro
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.number.Scale as JScale
import io.github.heapy.icu4k.number.Scale
import kotlin.test.*

class MultiplierFormatHandlerOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) { if (e is NullPointerException) "null" else e.javaClass.simpleName }
    @Test fun parentRunsBeforeScaleEvenWhenReturningNull() {
        for (nullResult in listOf(false, true)) for (nullScale in listOf(false, true)) {
            val jm = JMicro(false); val km = MicroProps(false)
            val jq = JBCD(2); val kq = DecimalQuantity_DualStorageBCD(2)
            val jt = mutableListOf<Long>(); val kt = mutableListOf<Long>()
            val j = JHandler(if (nullScale) null else JScale.powerOfTen(2), JGenerator { jt.add(jq.toLong(false)); if (nullResult) null else jm })
            val k = MultiplierFormatHandler(if (nullScale) null else Scale.powerOfTen(2), MicroPropsGenerator { kt.add(kq.toLong(false)); if (nullResult) null else km })
            assertEquals(outcome { j.processQuantity(jq) === jm }, outcome { k.processQuantity(kq) === km })
            assertEquals(jt, kt); assertEquals(jq.toPlainString(), kq.toPlainString())
        }
        assertEquals(outcome { JHandler(JScale.none(), null).processQuantity(null) }, outcome { MultiplierFormatHandler(Scale.none(), null).processQuantity(null) })
    }
}
