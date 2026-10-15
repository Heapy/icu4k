// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll
internal actual fun collationAssertionsEnabled(): Boolean = Collation::class.java.desiredAssertionStatus()
