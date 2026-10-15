package io.github.heapy.icu4k.impl.units
import io.github.heapy.icu4k.util.BigDecimal
internal actual fun assertComplexUnitsConverter(condition: () -> Boolean) {
    if (ComplexUnitsConverter::class.java.desiredAssertionStatus() && !condition()) throw AssertionError()
}
internal actual class MixedUnitInteger private constructor(private val value: java.math.BigInteger) {
    actual constructor(value: BigDecimal) : this(value.toBigInteger())
    actual fun negate(): MixedUnitInteger = MixedUnitInteger(value.negate())
    actual fun add(other: MixedUnitInteger): MixedUnitInteger = MixedUnitInteger(value.add(other.value))
    actual fun subtract(other: MixedUnitInteger): MixedUnitInteger = MixedUnitInteger(value.subtract(other.value))
    actual fun multiply(other: MixedUnitInteger): MixedUnitInteger = MixedUnitInteger(value.multiply(other.value))
    actual fun toLong(): Long = value.toLong()
    actual fun toNumber(): Number = value
    actual companion object { actual fun one(): MixedUnitInteger = MixedUnitInteger(java.math.BigInteger.ONE) }
}

internal actual fun sortMixedUnitList(units: MutableList<MeasureUnitImpl.MeasureUnitImplWithIndex?>, comparator: Comparator<MeasureUnitImpl.MeasureUnitImplWithIndex?>) {
    java.util.Collections.sort(units, comparator)
}
