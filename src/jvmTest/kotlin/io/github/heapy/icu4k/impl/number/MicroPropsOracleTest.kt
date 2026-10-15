@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.MicroProps as JMicro
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.number.IntegerWidth as JWidth
import com.ibm.icu.number.NumberFormatter.SignDisplay as JSign
import com.ibm.icu.number.NumberFormatter.DecimalSeparatorDisplay as JDecimal
import com.ibm.icu.impl.number.Padder as JPadder
import com.ibm.icu.impl.number.Grouper as JGrouper
import com.ibm.icu.impl.number.ConstantAffixModifier as JAffix
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.util.MeasureUnit as JUnit
import com.ibm.icu.util.Measure as JMeasure
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.number.IntegerWidth
import io.github.heapy.icu4k.number.SignDisplay
import io.github.heapy.icu4k.number.DecimalSeparatorDisplay
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.Measure
import io.github.heapy.icu4k.util.ULocale
import java.io.Serializable
import java.lang.reflect.Modifier
import kotlin.test.*

class MicroPropsOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) { "${e.javaClass.simpleName}:${e.message}" }
    private fun jFilled(immutable: Boolean) = JMicro(immutable).apply {
        sign = JSign.ALWAYS; symbols = JSymbols(JLocale.ENGLISH); nsName = "latn"
        padding = JPadder.NONE; decimal = JDecimal.ALWAYS; integerWidth = JWidth.zeroFillTo(3)
        modOuter = JAffix(); modMiddle = JAffix(); modInner = JAffix(); rounder = JPrecision.fixedFraction(2)
        grouping = JGrouper.getInstance(3, 2, 1); useCurrency = true; gender = "feminine"; currencyAsDecimal = "x"
        outputUnit = JUnit.METER; mixedMeasures = mutableListOf(JMeasure(1, JUnit.METER), null); indexOfQuantity = 42
    }
    private fun kFilled(immutable: Boolean) = MicroProps(immutable).apply {
        sign = SignDisplay.ALWAYS; symbols = DecimalFormatSymbols(ULocale("en")); nsName = "latn"
        padding = Padder.NONE; decimal = DecimalSeparatorDisplay.ALWAYS; integerWidth = IntegerWidth.zeroFillTo(3)
        modOuter = ConstantAffixModifier(); modMiddle = ConstantAffixModifier(); modInner = ConstantAffixModifier(); rounder = Precision.fixedFraction(2)
        grouping = Grouper.getInstance(3, 2, 1); useCurrency = true; gender = "feminine"; currencyAsDecimal = "x"
        outputUnit = MeasureUnit.METER; mixedMeasures = mutableListOf(Measure(1, MeasureUnit.METER), null); indexOfQuantity = 42
    }
    private fun checkClone(original: Any, copy: Any) {
        assertNotSame(original, copy)
        for (field in original.javaClass.fields.filterNot { Modifier.isStatic(it.modifiers) }) {
            if (field.type.isPrimitive) assertEquals(field.get(original), field.get(copy), field.name)
            else assertSame(field.get(original), field.get(copy), field.name)
        }
    }
    @Test fun publicFieldsDefaultsAndShallowClone() {
        val jFields = JMicro::class.java.fields.filterNot { Modifier.isStatic(it.modifiers) }.associateBy { it.name }
        val kFields = MicroProps::class.java.fields.filterNot { Modifier.isStatic(it.modifiers) }.associateBy { it.name }
        assertEquals(jFields.keys, kFields.keys)
        val j = JMicro(true); val k = MicroProps(true)
        for (name in jFields.keys) {
            assertEquals(jFields.getValue(name).get(j), kFields.getValue(name).get(k), name)
            assertEquals(Modifier.isFinal(jFields.getValue(name).modifiers), Modifier.isFinal(kFields.getValue(name).modifiers))
        }
        assertFalse(Serializable::class.java.isAssignableFrom(MicroProps::class.java))
        assertTrue(Cloneable::class.java.isAssignableFrom(MicroProps::class.java))
        val je = JMicro::class.java.getDeclaredField("exhausted"); val ke = MicroProps::class.java.getDeclaredField("exhausted")
        assertEquals(Modifier.isVolatile(je.modifiers), Modifier.isVolatile(ke.modifiers))
        for (immutable in listOf(false, true)) {
            val source = jFilled(immutable); val port = kFilled(immutable)
            checkClone(source, source.clone()); checkClone(port, port.clone())
            if (immutable) {
                val a = source.processQuantity(null); val b = port.processQuantity(null)
                checkClone(source, a); checkClone(port, b)
                assertEquals(a === a.processQuantity(null), b === b.processQuantity(null))
                assertEquals(source === source.processQuantity(null), port === port.processQuantity(null))
            } else {
                val sc = source.clone(); val pc = port.clone()
                assertSame(source, source.processQuantity(null)); assertSame(port, port.processQuantity(null))
                assertSame(sc, sc.processQuantity(null)); assertSame(pc, pc.processQuantity(null))
                assertEquals(outcome { source.processQuantity(null) }, outcome { port.processQuantity(null) })
                assertEquals(outcome { source.clone().processQuantity(null) }, outcome { port.clone().processQuantity(null) })
            }
        }
    }
    private class JDerived(immutable: Boolean) : JMicro(immutable) {
        val extra = Any(); var scalar = 3
    }
    private class KDerived(immutable: Boolean) : MicroProps(immutable) {
        val extra = Any(); var scalar = 3
    }
    @Test fun runtimeSubtypeCloneAndVirtualCloneDispatch() {
        val j = JDerived(true); val k = KDerived(true)
        val jc = j.processQuantity(null); val kc = k.processQuantity(null)
        assertTrue(jc is JDerived); assertTrue(kc is KDerived)
        assertSame(j.extra, jc.extra); assertSame(k.extra, kc.extra)
        assertEquals(jc.scalar, kc.scalar)
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        val overriddenJ = object : JMicro(true) {
            override fun clone(): JMicro { jt.add("clone"); return this }
        }
        val overriddenK = object : MicroProps(true) {
            override fun clone(): MicroProps { kt.add("clone"); return this }
        }
        repeat(2) {
            assertSame(overriddenJ, overriddenJ.processQuantity(null)); assertSame(overriddenK, overriddenK.processQuantity(null))
        }
        assertEquals(jt, kt)
    }
}
