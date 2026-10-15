// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.range

import kotlin.jvm.JvmField

open class PrefixInfixSuffixLengthHelper {
    @JvmField var lengthPrefix = 0
    @JvmField var length1 = 0
    @JvmField var lengthInfix = 0
    @JvmField var length2 = 0
    @JvmField var lengthSuffix = 0
    open fun index0(): Int = lengthPrefix
    open fun index1(): Int = lengthPrefix + length1
    open fun index2(): Int = lengthPrefix + length1 + lengthInfix
    open fun index3(): Int = lengthPrefix + length1 + lengthInfix + length2
    open fun index4(): Int = lengthPrefix + length1 + lengthInfix + length2 + lengthSuffix
}
