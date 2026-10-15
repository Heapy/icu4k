// © 2020 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.util.MeasurePrefix
import kotlin.math.abs

open class SingleUnitImpl {
    open fun build(): io.github.heapy.icu4k.util.MeasureUnit = MeasureUnitImpl(this).build()

    private var index = -1
    private var simpleUnitID: String? = ""
    private var dimensionality = 1
    private var prefix: MeasurePrefix? = MeasurePrefix.ONE
    open fun copy(): SingleUnitImpl = SingleUnitImpl().also { it.index = index; it.simpleUnitID = simpleUnitID; it.dimensionality = dimensionality; it.prefix = prefix }
    open fun getNeutralIdentifier(): String {
        val power = abs(getDimensionality())
        assertUnitIdentifierPower(power)
        val start = when { power == 1 -> ""; power == 2 -> "square-"; power == 3 -> "cubic-"; power <= 15 -> "pow$power-"; else -> throw IllegalArgumentException("Unit Identifier Syntax Error") }
        return start + getPrefix()!!.getIdentifier() + getSimpleUnitID()
    }
    internal fun compareTo(other: SingleUnitImpl): Int {
        if (dimensionality < 0 && other.dimensionality > 0) return 1
        if (dimensionality > 0 && other.dimensionality < 0) return -1
        val category = UnitIdentifierData.getCategoryIndexOfSimpleUnit(index)
        val otherCategory = UnitIdentifierData.getCategoryIndexOfSimpleUnit(other.index)
        if (category < otherCategory) return -1
        if (category > otherCategory) return 1
        if (index < other.index) return -1
        if (index > other.index) return 1
        val base = prefix!!.getBase(); val otherBase = other.prefix!!.getBase()
        val power = prefix!!.getPower() * if (base == 1024) 3 else 1
        val otherPower = other.prefix!!.getPower() * if (otherBase == 1024) 3 else 1
        return when { power < otherPower -> 1; power > otherPower -> -1; base < otherBase -> 1; base > otherBase -> -1; else -> 0 }
    }
    internal fun isCompatibleWith(other: SingleUnitImpl): Boolean = compareTo(other) == 0
    open fun getSimpleUnitID(): String? = simpleUnitID
    open fun setSimpleUnit(index: Int, units: Array<out String?>?) { this.index = index; simpleUnitID = unitArrayElement(units!!, index) }
    open fun getDimensionality(): Int = dimensionality
    open fun setDimensionality(value: Int) { dimensionality = value }
    open fun getPrefix(): MeasurePrefix? = prefix
    open fun setPrefix(value: MeasurePrefix?) { prefix = value }
    open fun getIndex(): Int = index
}
internal expect fun <T> unitArrayElement(values: Array<out T>, index: Int): T

/** Source assertion, controlled by JVM assertion settings; no production validation on other targets. */
internal expect fun assertUnitIdentifierPower(power: Int)
