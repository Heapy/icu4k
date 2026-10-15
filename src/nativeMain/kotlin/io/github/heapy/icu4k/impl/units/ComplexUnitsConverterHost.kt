package io.github.heapy.icu4k.impl.units
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.decimalValue
internal actual fun assertComplexUnitsConverter(condition: () -> Boolean) = Unit
internal actual class MixedUnitInteger private constructor(private val value: UnitDecimal) {
    actual constructor(value: BigDecimal) : this(value.decimalValue().integerValue())
    actual fun negate(): MixedUnitInteger = MixedUnitInteger(value.negate())
    actual fun add(other: MixedUnitInteger): MixedUnitInteger = MixedUnitInteger(value.add(other.value))
    actual fun subtract(other: MixedUnitInteger): MixedUnitInteger = MixedUnitInteger(value.subtract(other.value))
    actual fun multiply(other: MixedUnitInteger): MixedUnitInteger = MixedUnitInteger(value.multiply(other.value))
    actual fun toLong(): Long = value.longValue()
    actual fun toNumber(): Number = MixedUnitIntegerNumber(value)
    actual companion object { actual fun one(): MixedUnitInteger = MixedUnitInteger(UnitDecimal.ONE) }
}

internal actual fun sortMixedUnitList(units: MutableList<MeasureUnitImpl.MeasureUnitImplWithIndex?>, comparator: Comparator<MeasureUnitImpl.MeasureUnitImplWithIndex?>) {
    units.sortWith(comparator)
}
