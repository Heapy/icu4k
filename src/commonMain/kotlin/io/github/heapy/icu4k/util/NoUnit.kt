// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.jvm.JvmField

class NoUnit private constructor() {
    companion object {
        @JvmField val BASE: MeasureUnit? = null
        @JvmField val PERCENT = MeasureUnit.PERCENT
        @JvmField val PERMILLE = MeasureUnit.PERMILLE
    }
}
