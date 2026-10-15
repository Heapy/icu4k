// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll
internal actual fun collationIteratorAssertionsEnabled(): Boolean = CollationIterator::class.java.desiredAssertionStatus()
internal actual fun collationIteratorClassAssertionsEnabled(type: kotlin.reflect.KClass<*>): Boolean = type.java.desiredAssertionStatus()
internal actual fun utf16CollationIteratorAssertionsEnabled(): Boolean = UTF16CollationIterator::class.java.desiredAssertionStatus()
