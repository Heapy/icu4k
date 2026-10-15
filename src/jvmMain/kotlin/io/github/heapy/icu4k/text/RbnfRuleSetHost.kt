// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text
internal actual fun assertRbnfRuleSetHash() {
    if (RbnfRuleSetCore::class.java.desiredAssertionStatus()) throw AssertionError("hashCode not designed")
}
