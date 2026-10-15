// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.impl.number.MacroProps

/** Source soft-cache semantics keyed by the original, unnormalized skeleton. */
internal object NumberSkeletonImpl {
    private val cache = object : SoftCache<String, UnlocalizedNumberFormatter, Unit?>() {
        override fun createInstance(key: String, data: Unit?): UnlocalizedNumberFormatter = create(key)
    }
    fun getOrCreate(skeleton: String?): UnlocalizedNumberFormatter = cache.getInstance(skeleton!!, null)!!
    fun create(skeleton: String?): UnlocalizedNumberFormatter = NumberFormatter.with().macros(NumberSkeletonCore.parseSkeleton(skeleton))
    fun generate(macros: MacroProps?): String = NumberSkeletonCore.generate(macros)
}
