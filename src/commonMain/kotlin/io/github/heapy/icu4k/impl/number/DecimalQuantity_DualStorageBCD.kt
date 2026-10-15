// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.util.BigDecimal
import kotlin.jvm.JvmStatic

class DecimalQuantity_DualStorageBCD private constructor(core: DecimalQuantityCore) : DecimalQuantity_AbstractBCD(core) {
    constructor() : this(DecimalQuantityCore())
    constructor(value: Int) : this(DecimalQuantityCore(value.toLong()))
    constructor(value: Long) : this(DecimalQuantityCore(value))
    constructor(value: Double) : this(DecimalQuantityCore(value))
    constructor(value: BigDecimal?) : this(DecimalQuantityCore().also { it.setToBigDecimal(value) })
    constructor(other: DecimalQuantity_DualStorageBCD?) : this(other!!.core.createCopy())
    constructor(number: Number?) : this(quantityFromNumber(number))
    override fun createCopy(): DecimalQuantity = DecimalQuantity_DualStorageBCD(this)
    override fun maxRepresentableDigits(): Int = Int.MAX_VALUE
    fun checkHealth(): String? = core.checkHealth()
    fun isUsingBytes(): Boolean = core.isUsingBytes()
    override fun toString(): String = core.toString()
    companion object {
        internal fun fromCore(core: DecimalQuantityCore): DecimalQuantity_DualStorageBCD = DecimalQuantity_DualStorageBCD(core)
        @JvmStatic fun fromExponentString(value: String?): DecimalQuantity = DecimalQuantity_DualStorageBCD(DecimalQuantityCore.fromExponentString(value))
    }
}
internal expect fun quantityFromNumber(number: Number?): DecimalQuantityCore
