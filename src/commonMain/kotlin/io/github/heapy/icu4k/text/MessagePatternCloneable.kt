// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Host Cloneable marker, without introducing Java serialization on MessagePattern. */
expect interface MessagePatternCloneable {
    fun clone(): Any
}
