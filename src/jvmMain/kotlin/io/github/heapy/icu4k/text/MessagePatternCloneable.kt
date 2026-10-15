// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

actual interface MessagePatternCloneable : Cloneable {
    actual public override fun clone(): Any
}
