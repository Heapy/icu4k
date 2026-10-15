package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.DecimalSeparatorDisplay
import io.github.heapy.icu4k.number.GroupingStrategy
import io.github.heapy.icu4k.number.IntegerWidth
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.number.SignDisplay
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.Measure
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class MicroPropsTest {
    @Test fun immutableBaseClonesAllFieldsAndSharesReferences() {
        val original = MicroProps(true).apply {
            sign = SignDisplay.ALWAYS; symbols = DecimalFormatSymbols(ULocale("en")); nsName = "latn"
            padding = Padder.NONE; decimal = DecimalSeparatorDisplay.ALWAYS; integerWidth = IntegerWidth.zeroFillTo(3)
            modOuter = ConstantAffixModifier(); modMiddle = ConstantAffixModifier(); modInner = ConstantAffixModifier()
            rounder = Precision.fixedFraction(2); grouping = Grouper.forStrategy(GroupingStrategy.THOUSANDS)
            useCurrency = true; gender = "masculine"; currencyAsDecimal = "¤"
            outputUnit = MeasureUnit.METER; mixedMeasures = mutableListOf(Measure(1, MeasureUnit.METER), null); indexOfQuantity = 8
        }
        val copy = original.processQuantity(null)
        assertNotSame(original, copy)
        assertSame(original.sign, copy.sign); assertSame(original.symbols, copy.symbols); assertSame(original.nsName, copy.nsName)
        assertSame(original.padding, copy.padding); assertSame(original.decimal, copy.decimal); assertSame(original.integerWidth, copy.integerWidth)
        assertSame(original.modOuter, copy.modOuter); assertSame(original.modMiddle, copy.modMiddle); assertSame(original.modInner, copy.modInner)
        assertSame(original.rounder, copy.rounder); assertSame(original.grouping, copy.grouping)
        assertEquals(original.useCurrency, copy.useCurrency); assertSame(original.gender, copy.gender)
        assertSame(original.currencyAsDecimal, copy.currencyAsDecimal); assertSame(original.outputUnit, copy.outputUnit)
        assertSame(original.mixedMeasures, copy.mixedMeasures); assertEquals(original.indexOfQuantity, copy.indexOfQuantity)
        copy.indexOfQuantity = 99; copy.gender = "changed"
        assertEquals(8, original.indexOfQuantity); assertEquals("masculine", original.gender)
        (copy.mixedMeasures as MutableList).clear()
        assertTrue(original.mixedMeasures!!.isEmpty())
        assertNotSame(copy, copy.processQuantity(null))
        assertNotSame(copy, original.processQuantity(null))
    }
    @Test fun mutableBaseAndClonesRetainExhaustionState() {
        val original = MicroProps(false)
        assertEquals(-1, original.indexOfQuantity)
        assertNull(original.rounder); assertFalse(original.useCurrency)
        val freshCopy = original.clone()
        assertSame(original, original.processQuantity(null))
        assertSame(freshCopy, freshCopy.processQuantity(null))
        val usedCopy = original.clone()
        for (value in listOf(original, freshCopy, usedCopy)) {
            val failure = assertFailsWith<AssertionError> { value.processQuantity(null) }
            assertEquals("Cannot re-use a mutable MicroProps in the quantity chain", failure.message)
        }
    }
    @Test fun generatorChainCanRoundAndResolveIndependentResults() {
        val base = MicroProps(true).apply { rounder = Precision.fixedFraction(1) }
        val stage = MicroPropsGenerator { quantity ->
            base.processQuantity(quantity).also { micros ->
                micros.rounder!!.apply(quantity)
                micros.gender = quantity!!.toPlainString()
            }
        }
        val first = stage.processQuantity(DecimalQuantity_DualStorageBCD(1.25))!!
        val second = stage.processQuantity(DecimalQuantity_DualStorageBCD(2.75))!!
        assertNotSame(first, second)
        assertEquals("1.2", first.gender); assertEquals("2.8", second.gender)
        assertNull(base.gender)
    }
}
